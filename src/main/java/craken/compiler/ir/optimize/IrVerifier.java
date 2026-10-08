package craken.compiler.ir.optimize;

import craken.SourceRange;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.instruction.CallInstruction.*;
import craken.compiler.ir.instruction.ComputeInstruction.*;
import craken.compiler.ir.instruction.ControlInstruction.*;
import craken.compiler.ir.instruction.MemoryInstruction.*;
import craken.compiler.ir.manager.IrTypeLowerer;
import craken.compiler.ir.model.*;
import craken.compiler.ir.value.IrValue;
import craken.compiler.ir.value.IrValue.*;
import craken.compiler.type.CrakenType;
import craken.compiler.type.TypeLayout;

import java.util.*;

/**
 * Checks uninstrumented IR without changing it. This is a compiler-internal
 * contract, not a source-language type checker, alias proof, or complete ABI
 * verifier: external/indirect call signatures and pointer pointee types are not
 * retained in IrResult. Temporary definitions are checked with a must-defined
 * fixed point, allowing repeated definitions and loops rather than assuming SSA.
 * All syntactically reachable paths and all instruction operands participate;
 * this verifier does not prove that a branch condition is constant.
 * External symbol sets are used for reference resolution only; their mutual
 * linker-namespace conflicts and complete external signatures are not checked.
 */
public final class IrVerifier {
    private IrVerifier() {}

    public record Problem(String code, String function, String block, int instructionIndex,
                          SourceRange range, String message) {}

    public static final class VerificationException extends IllegalArgumentException {
        private final List<Problem> problems;
        private VerificationException(List<Problem> problems) {
            super("Invalid IR: " + problems);
            this.problems = List.copyOf(problems);
        }
        public List<Problem> problems() { return problems; }
    }

    public static void verify(IrResult result) {
        List<Problem> problems = inspect(result);
        if (!problems.isEmpty()) throw new VerificationException(problems);
    }

    public static List<Problem> inspect(IrResult result) {
        return new Check(Objects.requireNonNull(result, "result")).run();
    }

    private static final class Check {
        private final IrResult program;
        private final List<Problem> problems = new ArrayList<>();
        private final Map<String, IrFunction> functions = new LinkedHashMap<>();
        private final Set<String> globals = new LinkedHashSet<>();
        private final Set<String> strings = new LinkedHashSet<>();
        private IrFunction function;
        private String block = "";
        private int index = -1;
        private SourceRange range;
        private Map<String, IrParameter> parameters;
        private Map<String, IrType> temporaries;

        Check(IrResult program) { this.program = program; }

        List<Problem> run() {
            Set<String> symbols = new HashSet<>();
            for (var item : program.functions()) {
                if (!symbols.add(item.name())) problem("DUPLICATE_SYMBOL", item.name());
                functions.putIfAbsent(item.name(), item);
            }
            for (var item : program.globalData()) {
                if (!symbols.add(item.label())) problem("DUPLICATE_SYMBOL", item.label());
                globals.add(item.label());
            }
            for (var item : program.stringData()) {
                if (!symbols.add(item.label())) problem("DUPLICATE_SYMBOL", item.label());
                strings.add(item.label());
            }
            for(var global:program.globalData())for(var address:global.addresses()) {
                boolean exists=switch(address.kind()) {
                    case FUNCTION -> functions.containsKey(address.symbol())||program.externalFunctionNames().contains(address.symbol());
                    case OBJECT -> globals.contains(address.symbol())||program.externalObjectNames().contains(address.symbol());
                    case STRING -> strings.contains(address.symbol());
                };
                if(!exists)problem("GLOBAL_ADDRESS","Unknown "+address.kind()+" relocation in "+global.label()+": "+address.symbol());
            }
            for (IrFunction item : program.functions()) checkFunction(item);
            return List.copyOf(problems);
        }

        private void checkFunction(IrFunction item) {
            function = item; block = ""; index = -1; range = item.range();
            parameters = new LinkedHashMap<>();
            temporaries = new LinkedHashMap<>();
            for (IrParameter parameter : item.parameters()) {
                if (parameters.putIfAbsent(parameter.name(), parameter) != null)
                    problem("DUPLICATE_PARAMETER", parameter.name());
                if (lower(parameter.declaredType()) != parameter.type()) problem("PARAMETER_TYPE", parameter.name());
            }
            if (item.returnType().isStruct() && (item.parameters().isEmpty()
                    || !item.parameters().getFirst().declaredType().unqualified().equals(item.returnType().pointerTo().unqualified())
                    || item.parameters().getFirst().type() != IrType.POINTER)) {
                problem("CALL_ABI", "Aggregate return requires its hidden destination pointer first");
            }
            if (item.blocks().isEmpty()) { problem("NO_ENTRY", "Function has no entry block"); return; }
            Set<String> labels = new LinkedHashSet<>();
            boolean duplicate = false;
            Map<String, IrLocal> locals = new HashMap<>();
            for (IrBlock current : item.blocks()) {
                block = current.label(); index = -1; range = item.range();
                if (!labels.add(block)) { problem("DUPLICATE_BLOCK", block); duplicate = true; }
                for (int i = 0; i < current.instructions().size(); i++) {
                    locate(current, i);
                    IrInstruction instruction = current.instructions().get(i);
                    IrTemporary result = result(instruction);
                    if (result != null) {
                        IrType previous = temporaries.putIfAbsent(result.name(), result.type());
                        if (previous != null && previous != result.type()) problem("TEMPORARY_TYPE", result.name());
                    }
                    IrLocal local = local(instruction);
                    if (local != null) {
                        IrLocal previous = locals.putIfAbsent(local.name(), local);
                        if (previous != null && !sameStorage(previous, local)) problem("LOCAL_CONFLICT", local.name());
                        if (lower(local.declaredType()) != local.type()) problem("TYPE_MISMATCH", "Local ABI type: " + local.name());
                        Integer size = sizeOf(local.declaredType()), alignment = alignmentOf(local.declaredType());
                        if (size == null || local.sizeBytes() < size || alignment == null
                                || local.alignmentBytes() < alignment
                                || (local.alignmentBytes() & (local.alignmentBytes() - 1)) != 0)
                            problem("LAYOUT", "Local storage size/alignment: " + local.name());
                    }
                }
            }
            for (IrBlock current : item.blocks()) {
                for (int i = 0; i < current.instructions().size(); i++) {
                    locate(current, i);
                    IrInstruction instruction = current.instructions().get(i);
                    for (String target : IrControlFlow.targets(instruction))
                        if (!labels.contains(target)) problem("MISSING_TARGET", target);
                    for (IrValue value : uses(instruction)) checkValue(value);
                    checkInstruction(instruction);
                }
            }
            if (duplicate) return;
            IrControlFlow flow = IrControlFlow.analyze(item);
            for (IrBlock current : item.blocks()) {
                if (!flow.reachable().contains(current.label())) continue;
                var effective = flow.effectiveInstructions(current.label());
                if (effective.isEmpty() || !IrControlFlow.isTerminator(effective.getLast())) {
                    block = current.label(); index = effective.size() - 1;
                    range = effective.isEmpty() ? item.range() : effective.getLast().range();
                    problem("MISSING_TERMINATOR", "Reachable block has no terminator");
                }
            }
            checkDefinitions(flow);
        }

        private void checkDefinitions(IrControlFlow flow) {
            String entry = function.blocks().getFirst().label();
            Map<String, Set<String>> in = new LinkedHashMap<>(), out = new LinkedHashMap<>();
            for (String label : flow.reachable()) {
                Set<String> initial = new LinkedHashSet<>(temporaries.keySet());
                if (label.equals(entry)) initial.clear();
                in.put(label, initial);
                out.put(label, definedAfter(flow.effectiveInstructions(label), initial));
            }
            boolean changed;
            do {
                changed = false;
                for (String label : flow.reachable()) {
                    Set<String> incoming = new LinkedHashSet<>();
                    if (!label.equals(entry)) {
                        incoming.addAll(temporaries.keySet());
                        for (String previous : flow.predecessors(label))
                            if (flow.reachable().contains(previous)) incoming.retainAll(out.get(previous));
                    }
                    Set<String> outgoing = definedAfter(flow.effectiveInstructions(label), incoming);
                    if (!incoming.equals(in.get(label)) || !outgoing.equals(out.get(label))) changed = true;
                    in.put(label, incoming); out.put(label, outgoing);
                }
            } while (changed);
            for (IrBlock current : function.blocks()) {
                if (!flow.reachable().contains(current.label())) continue;
                Set<String> available = new HashSet<>(in.get(current.label()));
                var effective = flow.effectiveInstructions(current.label());
                for (int i = 0; i < effective.size(); i++) {
                    locate(current, i);
                    IrInstruction instruction = effective.get(i);
                    for (IrValue value : uses(instruction)) {
                        if (value instanceof IrTemporary temporary && temporaries.containsKey(temporary.name())
                                && !available.contains(temporary.name()))
                            problem("UNDEFINED_TEMPORARY", temporary.name() + " is not defined on every incoming path");
                    }
                    IrTemporary defined = result(instruction);
                    if (defined != null) available.add(defined.name());
                }
            }
        }

        private Set<String> definedAfter(List<IrInstruction> instructions, Set<String> before) {
            Set<String> defined = new LinkedHashSet<>(before);
            for (IrInstruction instruction : instructions) {
                IrTemporary result = result(instruction);
                if (result != null) defined.add(result.name());
            }
            return defined;
        }

        private void checkValue(IrValue value) {
            switch (value) {
                case IrTemporary temporary -> {
                    IrType type = temporaries.get(temporary.name());
                    if (type == null) problem("UNKNOWN_TEMPORARY", temporary.name());
                    else if (type != temporary.type()) problem("TEMPORARY_TYPE", temporary.name());
                }
                case IrParameterRef parameter -> {
                    IrParameter declared = parameters.get(parameter.name());
                    if (declared == null) problem("UNKNOWN_PARAMETER", parameter.name());
                    else if (declared.type() != parameter.type()) problem("PARAMETER_TYPE", parameter.name());
                }
                case IrParameterAddress parameter -> {
                    if (!parameters.containsKey(parameter.name())) problem("UNKNOWN_PARAMETER", parameter.name());
                }
                case IrFunctionAddress address -> {
                    if (!functions.containsKey(address.functionName()) && !program.externalFunctionNames().contains(address.functionName()))
                        problem("UNKNOWN_SYMBOL", address.functionName());
                }
                case IrGlobalAddress address -> {
                    if (!globals.contains(address.globalName()) && !program.externalObjectNames().contains(address.globalName()))
                        problem("UNKNOWN_SYMBOL", address.globalName());
                }
                case IrStringLiteral literal -> {
                    if (!strings.contains(literal.label())) problem("UNKNOWN_SYMBOL", literal.label());
                }
                case IrConstant ignored -> { }
                case IrFloatConstant ignored -> { }
            }
        }

        private void checkInstruction(IrInstruction instruction) {
            switch (instruction) {
                case IrMoveInstruction move -> assignable(move.result().type(), move.value());
                case IrSelectInstruction select -> {
                    assignable(select.result().type(), select.thenValue());
                    assignable(select.result().type(), select.elseValue());
                }
                case IrBinaryInstruction binary -> checkBinary(binary);
                case IrUnaryInstruction unary -> {
                    if (unary.operator() == IrUnaryOperator.LOGICAL_NOT) integer(unary.result().type());
                    else {
                        same(unary.result().type(), unary.operand().type());
                        if (unary.operator() == IrUnaryOperator.BITWISE_NOT) integer(unary.operand().type());
                        else if (unary.operand().type() == IrType.POINTER) problem("TYPE_MISMATCH", "Cannot negate pointer");
                    }
                }
                case IrLoadLocalInstruction load -> { same(load.result().type(), load.local().type()); scalarLocal(load.local()); }
                case IrStoreLocalInstruction store -> { assignable(store.local().type(), store.value()); scalarLocal(store.local()); }
                case IrAddressOfLocalInstruction address -> pointer(address.result());
                case IrLoadPointerInstruction load -> pointer(load.address());
                case IrStorePointerInstruction store -> pointer(store.address());
                case IrMemCopyInstruction copy -> { pointer(copy.destination()); pointer(copy.source()); }
                case IrElementAddressInstruction element -> {
                    pointer(element.result()); pointer(element.baseAddress()); integer(element.index().type());
                    Integer size = sizeOf(element.elementType());
                    if (size == null || size != element.elementSizeBytes()) problem("LAYOUT", "Element size disagrees with type");
                }
                case IrFieldAddressInstruction field -> {
                    pointer(field.result()); pointer(field.baseAddress());
                    var layout = program.structLayouts().get(field.ownerStructName());
                    var declared = layout == null ? null : layout.field(field.fieldName()).orElse(null);
                    if (declared == null || declared.offset() != field.offset()
                            || !declared.type().unqualified().equals(field.fieldType().unqualified())
                            || !field.fieldType().qualifiers().containsAll(declared.type().qualifiers()))
                        problem("LAYOUT", field.ownerStructName() + "." + field.fieldName());
                }
                case IrCallInstruction call -> checkCall(call);
                case IrIndirectCallInstruction call -> pointer(call.calleeAddress());
                case IrReturnInstruction returned -> {
                    if (function.returnType().isVoid()) {
                        if (returned.value() != null) problem("TYPE_MISMATCH", "Void return carries a value");
                    } else if (returned.value() == null) problem("TYPE_MISMATCH", "Non-void return has no value");
                    else assignable(lower(function.returnType()), returned.value());
                }
                case IrTrapInstruction ignored -> problem("DEBUG_TRAP", "Native optimization requires uninstrumented IR");
                case IrCaptureInstruction ignored -> problem("DEBUG_CAPTURE", "Native optimization requires uninstrumented IR");
                case IrCastInstruction ignored -> { /* Explicit casts can change scalar/pointer representations. */ }
                case IrDeclareLocalInstruction ignored -> { }
                case IrCheckInitializedInstruction ignored -> { }
                case IrCheckNonZeroInstruction ignored -> { }
                case IrBranchInstruction ignored -> { }
                case IrJumpInstruction ignored -> { }
            }
        }

        private void checkBinary(IrBinaryInstruction binary) {
            IrType left = binary.left().type(), right = binary.right().type(), result = binary.result().type();
            switch (binary.operator()) {
                case LOGICAL_AND, LOGICAL_OR -> integer(result);
                case EQUAL, NOT_EQUAL, LESS_THAN, LESS_EQUAL, GREATER_THAN, GREATER_EQUAL -> {
                    // Current lowering keeps a pointer operand as POINTER but
                    // normalizes the integer operand of a null comparison to ULL.
                    if (!((left == IrType.POINTER && right == IrType.UNSIGNED_LONG_LONG)
                            || (right == IrType.POINTER && left == IrType.UNSIGNED_LONG_LONG))) same(left, right);
                    integer(result);
                }
                case BITWISE_AND, BITWISE_OR, BITWISE_XOR, MODULO -> {
                    integer(left); integer(right); same(left, right); same(left, result);
                }
                case SHIFT_LEFT, SHIFT_RIGHT -> { integer(left); integer(right); same(left, result); }
                case ADD, SUBTRACT -> {
                    if (left == IrType.POINTER && right.isIntegerScalar()) same(IrType.POINTER, result);
                    else if (binary.operator() == IrBinaryOperator.SUBTRACT && left == IrType.POINTER && right == IrType.POINTER)
                        same(IrType.LONG_LONG, result);
                    else { same(left, right); same(left, result); if (left == IrType.POINTER) problem("TYPE_MISMATCH", "Invalid pointer arithmetic"); }
                }
                case MULTIPLY, DIVIDE -> {
                    same(left, right); same(left, result);
                    if (left == IrType.POINTER) problem("TYPE_MISMATCH", "Invalid pointer arithmetic");
                }
            }
        }

        private void checkCall(IrCallInstruction call) {
            IrFunction callee = functions.get(call.calleeName());
            if (callee == null) {
                if (!program.externalFunctionNames().contains(call.calleeName())) problem("UNKNOWN_SYMBOL", call.calleeName());
                return;
            }
            int count = callee.parameters().size();
            if (call.variadic() != callee.variadic() || call.arguments().size() < count
                    || (!callee.variadic() && call.arguments().size() != count)) problem("CALL_ABI", "Signature of " + call.calleeName());
            for (int i = 0; i < Math.min(count, call.arguments().size()); i++) {
                if (!compatibleValue(callee.parameters().get(i).type(), call.arguments().get(i)))
                    problem("CALL_ABI", "Argument " + i + " of " + call.calleeName());
            }
            if (call.result() != null && (callee.returnType().isVoid() || call.result().type() != lower(callee.returnType())))
                problem("CALL_ABI", "Return type of " + call.calleeName());
        }

        private Integer sizeOf(CrakenType type) {
            type = type.unqualified();
            try {
                if (type.isArray()) {
                    Integer element = sizeOf(type.elementType());
                    return element == null ? null : Math.multiplyExact(element, type.arrayLength());
                }
                if (type instanceof CrakenType.StructType structure) {
                    var layout = program.structLayouts().get(structure.name());
                    return layout == null ? null : layout.size();
                }
                return TypeLayout.sizeOf(type);
            } catch (IllegalArgumentException | ArithmeticException ignored) { return null; }
        }

        private Integer alignmentOf(CrakenType type) {
            type = type.unqualified();
            try {
                if (type.isArray()) return alignmentOf(type.elementType());
                if (type instanceof CrakenType.StructType structure) {
                    var layout = program.structLayouts().get(structure.name());
                    return layout == null ? null : layout.alignment();
                }
                return TypeLayout.alignmentOf(type);
            } catch (IllegalArgumentException ignored) { return null; }
        }

        private IrType lower(CrakenType type) {
            try { return IrTypeLowerer.lower(type); }
            catch (IllegalArgumentException error) { problem("TYPE_MISMATCH", error.getMessage()); return null; }
        }
        private boolean sameStorage(IrLocal first, IrLocal second) {
            // A va_start expression creates a fresh view of the same incoming
            // argument area; its source range describes that use, not slot identity.
            return first.sourceName().equals(second.sourceName())
                    && first.declaredType().equals(second.declaredType()) && first.type() == second.type()
                    && first.sizeBytes() == second.sizeBytes() && first.alignmentBytes() == second.alignmentBytes()
                    && first.storageKind() == second.storageKind()
                    && first.incomingArgumentIndex() == second.incomingArgumentIndex();
        }
        private void scalarLocal(IrLocal local) {
            if (local.aggregate() || local.incomingArgumentArea()) problem("TYPE_MISMATCH", "Local requires address access: " + local.name());
        }
        private boolean compatibleValue(IrType expected, IrValue value) {
            // Legacy explicit casts of integer literal zero can remain integer
            // constants in pointer stores, returns and call arguments.
            return expected == value.type() || (expected == IrType.POINTER && value instanceof IrConstant constant
                    && constant.type().isIntegerScalar() && constant.value() == 0);
        }
        private void assignable(IrType expected, IrValue value) {
            if (!compatibleValue(expected, value)) same(expected, value.type());
        }
        private void pointer(IrValue value) { same(IrType.POINTER, value.type()); }
        private void integer(IrType type) { if (!type.isIntegerScalar()) problem("TYPE_MISMATCH", "Expected integer, found " + type); }
        private void same(IrType expected, IrType actual) {
            if (expected != actual) problem("TYPE_MISMATCH", "Expected " + expected + ", found " + actual);
        }
        private void locate(IrBlock current, int position) {
            block = current.label(); index = position; range = current.instructions().get(position).range();
        }
        private void problem(String code, String message) {
            problems.add(new Problem(code, function == null ? "" : function.name(), block, index, range, message));
        }
    }

    private static IrTemporary result(IrInstruction instruction) {
        return switch (instruction) {
            case IrBinaryInstruction value -> value.result();
            case IrUnaryInstruction value -> value.result();
            case IrCastInstruction value -> value.result();
            case IrMoveInstruction value -> value.result();
            case IrSelectInstruction value -> value.result();
            case IrAddressOfLocalInstruction value -> value.result();
            case IrLoadLocalInstruction value -> value.result();
            case IrLoadPointerInstruction value -> value.result();
            case IrElementAddressInstruction value -> value.result();
            case IrFieldAddressInstruction value -> value.result();
            case IrCallInstruction value -> value.result();
            case IrIndirectCallInstruction value -> value.result();
            case IrDeclareLocalInstruction ignored -> null;
            case IrCheckInitializedInstruction ignored -> null;
            case IrStoreLocalInstruction ignored -> null;
            case IrStorePointerInstruction ignored -> null;
            case IrMemCopyInstruction ignored -> null;
            case IrBranchInstruction ignored -> null;
            case IrJumpInstruction ignored -> null;
            case IrReturnInstruction ignored -> null;
            case IrCheckNonZeroInstruction ignored -> null;
            case IrTrapInstruction ignored -> null;
            case IrCaptureInstruction ignored -> null;
        };
    }

    private static IrLocal local(IrInstruction instruction) {
        return switch (instruction) {
            case IrDeclareLocalInstruction value -> value.local();
            case IrCheckInitializedInstruction value -> value.local();
            case IrLoadLocalInstruction value -> value.local();
            case IrStoreLocalInstruction value -> value.local();
            case IrAddressOfLocalInstruction value -> value.local();
            default -> null;
        };
    }

    private static List<IrValue> uses(IrInstruction instruction) {
        return switch (instruction) {
            case IrBinaryInstruction value -> List.of(value.left(), value.right());
            case IrUnaryInstruction value -> List.of(value.operand());
            case IrCastInstruction value -> List.of(value.value());
            case IrMoveInstruction value -> List.of(value.value());
            case IrSelectInstruction value -> List.of(value.condition(), value.thenValue(), value.elseValue());
            case IrLoadPointerInstruction value -> List.of(value.address());
            case IrElementAddressInstruction value -> List.of(value.baseAddress(), value.index());
            case IrFieldAddressInstruction value -> List.of(value.baseAddress());
            case IrCallInstruction value -> value.arguments();
            case IrIndirectCallInstruction value -> {
                List<IrValue> arguments = new ArrayList<>(); arguments.add(value.calleeAddress()); arguments.addAll(value.arguments()); yield arguments;
            }
            case IrStoreLocalInstruction value -> List.of(value.value());
            case IrStorePointerInstruction value -> List.of(value.address(), value.value());
            case IrMemCopyInstruction value -> List.of(value.destination(), value.source());
            case IrBranchInstruction value -> List.of(value.condition());
            case IrReturnInstruction value -> value.value() == null ? List.of() : List.of(value.value());
            case IrCheckNonZeroInstruction value -> List.of(value.value());
            case IrDeclareLocalInstruction ignored -> List.of();
            case IrCheckInitializedInstruction ignored -> List.of();
            case IrLoadLocalInstruction ignored -> List.of();
            case IrAddressOfLocalInstruction ignored -> List.of();
            case IrJumpInstruction ignored -> List.of();
            case IrTrapInstruction ignored -> List.of();
            case IrCaptureInstruction ignored -> List.of();
        };
    }
}
