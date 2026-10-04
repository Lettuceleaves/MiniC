package craken.compiler.ir.optimize;

import craken.compiler.ir.IrResult;
import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.instruction.CallInstruction.*;
import craken.compiler.ir.instruction.ComputeInstruction.*;
import craken.compiler.ir.instruction.ControlInstruction.*;
import craken.compiler.ir.instruction.MemoryInstruction.*;
import craken.compiler.ir.model.IrBlock;
import craken.compiler.ir.model.IrFunction;
import craken.compiler.ir.model.IrType;
import craken.compiler.ir.value.IrValue;
import craken.compiler.ir.value.IrValue.*;

import java.util.*;

/**
 * Forward must-facts on non-SSA temporaries. Does not infer values of memory or
 * mutable parameter slots, fold floating arithmetic, or remove observable work.
 * The surrounding optimization pipeline verifies input and output.
 */
public final class ConstantPropagationPass implements IrPass {
    @Override public String name() { return "constant-propagation"; }

    @Override public IrResult apply(IrResult input) {
        Objects.requireNonNull(input, "input");
        var functions = input.functions().stream().map(this::propagate).toList();
        if (functions.equals(input.functions())) return input;
        return new IrResult(functions, input.stringData(), input.globalData(), input.externalFunctionNames(),
                input.externalObjectNames(), input.structLayouts(), input.currentAstNode(), input.currentSubject(), input.displayNames(), input.entryFunction());
    }

    private IrFunction propagate(IrFunction function) {
        if (function.blocks().isEmpty()) return function;
        IrControlFlow flow = IrControlFlow.analyze(function);
        Map<String, Integer> definitions = new HashMap<>();
        for (String label : flow.reachable()) for (var instruction : flow.effectiveInstructions(label)) {
            var result = IrValueUses.result(instruction);
            if (result != null) definitions.merge(result.name(), 1, Integer::sum);
        }
        Map<String, Map<String, IrValue>> outgoing = new LinkedHashMap<>();
        String entry = function.blocks().getFirst().label();
        // Absent outgoing state means TOP (not processed), not an empty set of known facts.
        boolean changed;
        do {
            changed = false;
            for (var block : function.blocks()) {
                if (!flow.reachable().contains(block.label())) continue;
                var incoming = meet(block.label(), entry, flow, outgoing);
                if (incoming == null) continue;
                var next = transfer(flow.effectiveInstructions(block.label()), incoming, definitions, null);
                if (!next.equals(outgoing.put(block.label(), next))) changed = true;
            }
        } while (changed);

        var blocks = new ArrayList<IrBlock>();
        for (var block : function.blocks()) {
            if (!flow.reachable().contains(block.label())) { blocks.add(block); continue; }
            var instructions = new ArrayList<IrInstruction>();
            var prefix = flow.effectiveInstructions(block.label());
            transfer(prefix, meet(block.label(), entry, flow, outgoing), definitions, instructions);
            instructions.addAll(block.instructions().subList(prefix.size(), block.instructions().size()));
            blocks.add(new IrBlock(block.label(), instructions));
        }
        return blocks.equals(function.blocks()) ? function : new IrFunction(function.name(), function.returnType(),
                function.parameters(), function.variadic(), blocks, function.range());
    }

    private Map<String, IrValue> meet(String label, String entry, IrControlFlow flow,
                                    Map<String, Map<String, IrValue>> outgoing) {
        if (label.equals(entry)) return Map.of(); // Includes the initial function invocation edge.
        Map<String, IrValue> merged = null;
        for (String predecessor : flow.predecessors(label)) {
            var state = outgoing.get(predecessor);
            if (state == null || !flow.reachable().contains(predecessor)) continue;
            if (merged == null) merged = new HashMap<>(state);
            else merged.entrySet().removeIf(item -> !item.getValue().equals(state.get(item.getKey())));
        }
        return merged;
    }

    private Map<String, IrValue> transfer(List<IrInstruction> instructions, Map<String, IrValue> incoming,
                                          Map<String, Integer> definitions, List<IrInstruction> output) {
        var facts = new HashMap<>(incoming);
        for (var instruction : instructions) {
            // Read old operands before redefining a result, including x = x + 1.
            IrInstruction rewritten = fold(rewrite(instruction, facts));
            var result = IrValueUses.result(instruction);
            if (result != null) {
                kill(result.name(), facts);
                if (rewritten instanceof IrMoveInstruction move && result.type() == move.value().type()) {
                    IrValue value = move.value();
                    if (value instanceof IrConstant constant && canonical(constant) != null)
                        facts.put(result.name(), canonical(constant));
                    else if (value instanceof IrTemporary temporary && !temporary.name().equals(result.name())
                            && definitions.getOrDefault(temporary.name(), 0) == 1)
                        facts.put(result.name(), temporary);
                }
            }
            if (output != null) output.add(rewritten);
        }
        return facts;
    }

    private void kill(String name, Map<String, IrValue> facts) {
        // A single static definition inside a loop still executes repeatedly. Retire all
        // aliases of its old value, not just its own fact, before publishing a new fact.
        Set<String> killed = new HashSet<>();
        killed.add(name);
        boolean changed;
        do {
            changed = false;
            for (var item : facts.entrySet())
                if (item.getValue() instanceof IrTemporary value && killed.contains(value.name()))
                    changed |= killed.add(item.getKey());
        } while (changed);
        killed.forEach(facts::remove);
    }

    private IrValue resolve(IrValue value, Map<String, IrValue> facts) {
        Set<String> visited = new HashSet<>();
        while (value instanceof IrTemporary temporary && visited.add(temporary.name())) {
            IrValue known = facts.get(temporary.name());
            if (known == null || known.type() != value.type()) break;
            value = known;
        }
        return value;
    }

    private IrInstruction rewrite(IrInstruction instruction, Map<String, IrValue> facts) {
        return IrValueRewriter.inputs(instruction,value->resolve(value,facts));
    }

    private IrInstruction fold(IrInstruction instruction) {
        IrConstant constant = switch (instruction) {
            case IrBinaryInstruction binary -> binary(binary);
            case IrUnaryInstruction unary -> unary(unary);
            case IrCastInstruction cast -> cast.value() instanceof IrConstant value
                    && value.type().isIntegerScalar() && cast.result().type().isIntegerScalar()
                    && canonical(value) != null ? constant(canonical(value).value(), cast.result().type()) : null;
            default -> null;
        };
        if (constant != null) return new IrMoveInstruction(IrValueUses.result(instruction), constant, instruction.range());
        if (instruction instanceof IrSelectInstruction select && select.condition() instanceof IrConstant value
                && canonical(value) != null && !select.result().type().isFloatingScalar()) {
            IrValue selected = canonical(value).value() == 0 ? select.elseValue() : select.thenValue();
            if (selected.type() == select.result().type()) return new IrMoveInstruction(select.result(), selected, select.range());
        }
        return instruction;
    }

    private IrConstant unary(IrUnaryInstruction unary) {
        if (!(unary.operand() instanceof IrConstant value) || !value.type().isIntegerScalar()
                || canonical(value) == null) return null;
        long number = canonical(value).value();
        return constant(switch (unary.operator()) {
            case LOGICAL_NOT -> number == 0 ? 1 : 0;
            case BITWISE_NOT -> ~number;
            case NEGATE -> -number;
        }, unary.result().type());
    }

    private IrConstant binary(IrBinaryInstruction binary) {
        if (!(binary.left() instanceof IrConstant left) || !(binary.right() instanceof IrConstant right)
                || !left.type().isIntegerScalar() || left.type() != right.type()
                || canonical(left) == null || canonical(right) == null) return null;
        IrType type = left.type();
        long a = canonical(left).value(), b = canonical(right).value();
        boolean unsigned = type.isUnsignedInteger();
        int width = type.sizeBytes() * Byte.SIZE;
        if ((binary.operator() == IrBinaryOperator.DIVIDE || binary.operator() == IrBinaryOperator.MODULO)
                && (b == 0 || !unsigned && b == -1 && a == (width == 64 ? Long.MIN_VALUE : -(1L << (width - 1))))) return null;
        if ((binary.operator() == IrBinaryOperator.SHIFT_LEFT || binary.operator() == IrBinaryOperator.SHIFT_RIGHT)
                && (b < 0 || b >= width)) return null;
        int comparison = unsigned ? Long.compareUnsigned(a, b) : Long.compare(a, b);
        long number = switch (binary.operator()) {
            case ADD -> a + b;
            case SUBTRACT -> a - b;
            case MULTIPLY -> a * b;
            case DIVIDE -> unsigned ? Long.divideUnsigned(a, b) : a / b;
            case MODULO -> unsigned ? Long.remainderUnsigned(a, b) : a % b;
            case BITWISE_AND -> a & b;
            case BITWISE_OR -> a | b;
            case BITWISE_XOR -> a ^ b;
            case SHIFT_LEFT -> a << b;
            case SHIFT_RIGHT -> unsigned ? a >>> b : a >> b;
            case LOGICAL_AND -> a != 0 && b != 0 ? 1 : 0;
            case LOGICAL_OR -> a != 0 || b != 0 ? 1 : 0;
            case EQUAL -> comparison == 0 ? 1 : 0;
            case NOT_EQUAL -> comparison != 0 ? 1 : 0;
            case LESS_THAN -> comparison < 0 ? 1 : 0;
            case LESS_EQUAL -> comparison <= 0 ? 1 : 0;
            case GREATER_THAN -> comparison > 0 ? 1 : 0;
            case GREATER_EQUAL -> comparison >= 0 ? 1 : 0;
        };
        return constant(number, binary.result().type());
    }

    private IrConstant canonical(IrConstant value) { return constant(value.value(), value.type()); }

    private IrConstant constant(long value, IrType type) {
        // Bool's native representation is canonical at frontend boundaries. Preserve
        // noncanonical casts instead of guessing at legacy backend conversion details.
        if (type == IrType.BOOL && value != 0 && value != 1) return null;
        if (!type.isIntegerScalar() && type != IrType.POINTER) return null;
        int bits = type.sizeBytes() * Byte.SIZE;
        if (bits < Long.SIZE) {
            value &= (1L << bits) - 1;
            if (type.isSignedInteger() && (value & (1L << (bits - 1))) != 0) value |= -1L << bits;
        }
        return new IrConstant(value, type);
    }
}
