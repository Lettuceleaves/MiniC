package craken.compiler.ir.optimize;

import craken.compiler.ir.IrResult;
import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.instruction.CallInstruction.*;
import craken.compiler.ir.instruction.ComputeInstruction.*;
import craken.compiler.ir.instruction.ControlInstruction.*;
import craken.compiler.ir.instruction.MemoryInstruction.*;
import craken.compiler.ir.model.*;
import craken.compiler.ir.value.IrValue;
import craken.compiler.ir.value.IrValue.*;

import java.util.*;

/**
 * Native-only capture of repeatedly read, unaliased integer/pointer parameter values.
 * The existing register allocator can then assign a normal temporary home. No address
 * or ABI slot is changed, and the caller's source/debug IR is never mutated.
 */
public final class ReadOnlyParameterPromotionPass implements IrPass {
    @Override public String name() { return "read-only-parameter-promotion"; }
    @Override public IrResult apply(IrResult source) {
        Objects.requireNonNull(source, "source");
        var functions = source.functions().stream().map(this::promote).toList();
        return functions.equals(source.functions()) ? source : new IrResult(functions, source.stringData(), source.globalData(),
                source.externalFunctionNames(), source.externalObjectNames(), source.structLayouts(), source.currentAstNode(),
                source.currentSubject(), source.displayNames(), source.entryFunction());
    }

    private IrFunction promote(IrFunction function) {
        if (function.blocks().isEmpty() || function.variadic()) return function;
        var flow = IrControlFlow.analyze(function);
        String entry = function.blocks().getFirst().label();
        if (flow.predecessors(entry).stream().anyMatch(flow.reachable()::contains)) return function;
        var excluded = new HashSet<String>();
        var uses = new HashMap<String, Integer>();
        var loopReads = new HashSet<String>();
        var names = new HashSet<String>();
        function.parameters().forEach(parameter -> names.add(parameter.name()));
        var cycles = new HashMap<String, Boolean>();
        for (var block : function.blocks()) {
            names.add(block.label());
            int prefix = flow.effectiveInstructions(block.label()).size();
            for (int index = 0; index < block.instructions().size(); index++) {
                var instruction = block.instructions().get(index);
                IrLocal local = localOf(instruction);
                // va_start may expose parameter homes through the frame's argument area.
                if (local != null) {
                    if (local.incomingArgumentArea()) return function;
                    names.add(local.name());
                }
                var result = IrValueUses.result(instruction);
                if (result != null) names.add(result.name());
                boolean executable = flow.reachable().contains(block.label()) && index < prefix;
                for (var input : IrValueUses.inputs(instruction)) {
                    if (input instanceof IrTemporary temporary) names.add(temporary.name());
                    if (input instanceof IrParameterAddress address) excluded.add(address.name());
                    if (input instanceof IrParameterRef reference) {
                        if (!executable) excluded.add(reference.name());
                        else {
                            uses.merge(reference.name(), 1, Integer::sum);
                            if (cycles.computeIfAbsent(block.label(), label -> inCycle(flow, label))) loopReads.add(reference.name());
                        }
                    }
                }
            }
        }
        var homes = new LinkedHashMap<String, IrTemporary>();
        var captures = new ArrayList<IrInstruction>();
        int counter = 0;
        for (var parameter : function.parameters()) {
            if (excluded.contains(parameter.name()) || parameter.declaredType().isVolatileQualified()
                    || !(parameter.declaredType().isIntegerScalar() || parameter.declaredType().isPointer())
                    || !(parameter.type().isIntegerScalar() || parameter.type() == IrType.POINTER)
                    || uses.getOrDefault(parameter.name(), 0) < 2 && !loopReads.contains(parameter.name())) continue;
            String name;
            do { name = "__readonly_parameter_" + counter++; } while (!names.add(name));
            var home = new IrTemporary(name, parameter.type());
            homes.put(parameter.name(), home);
            captures.add(new IrMoveInstruction(home, parameter.ref(), parameter.range()));
        }
        if (homes.isEmpty()) return function;
        var blocks = new ArrayList<IrBlock>();
        for (int index = 0; index < function.blocks().size(); index++) {
            var block = function.blocks().get(index);
            var code = new ArrayList<IrInstruction>();
            if (index == 0) code.addAll(captures);
            for (var instruction : block.instructions()) code.add(rewriteParameters(instruction, homes));
            blocks.add(code.equals(block.instructions()) ? block : new IrBlock(block.label(), code));
        }
        return new IrFunction(function.name(), function.returnType(), function.parameters(), function.variadic(), blocks, function.range());
    }

    private static boolean inCycle(IrControlFlow flow, String block) {
        var pending = new ArrayDeque<>(flow.successors(block));
        var seen = new HashSet<String>();
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            if (next.equals(block)) return true;
            if (seen.add(next)) pending.addAll(flow.successors(next));
        }
        return false;
    }

    private static IrLocal localOf(IrInstruction instruction) {
        return switch (instruction) {
            case IrDeclareLocalInstruction value -> value.local();
            case IrCheckInitializedInstruction value -> value.local();
            case IrAddressOfLocalInstruction value -> value.local();
            case IrLoadLocalInstruction value -> value.local();
            case IrStoreLocalInstruction value -> value.local();
            default -> null;
        };
    }
    private static IrValue parameterValue(IrValue value, Map<String, IrTemporary> homes) {
        return value instanceof IrParameterRef reference && homes.containsKey(reference.name()) ? homes.get(reference.name()) : value;
    }

    private static IrInstruction rewriteParameters(IrInstruction instruction, Map<String, IrTemporary> homes) {
        if (homes.isEmpty()) return instruction;
        var rewritten = switch (instruction) {
            case IrBinaryInstruction v -> new IrBinaryInstruction(v.result(), v.operator(), parameterValue(v.left(), homes), parameterValue(v.right(), homes), v.range());
            case IrUnaryInstruction v -> new IrUnaryInstruction(v.result(), v.operator(), parameterValue(v.operand(), homes), v.range());
            case IrCastInstruction v -> new IrCastInstruction(v.result(), parameterValue(v.value(), homes), v.range());
            case IrMoveInstruction v -> new IrMoveInstruction(v.result(), parameterValue(v.value(), homes), v.range());
            case IrSelectInstruction v -> new IrSelectInstruction(v.result(), parameterValue(v.condition(), homes), parameterValue(v.thenValue(), homes), parameterValue(v.elseValue(), homes), v.range());
            case IrLoadPointerInstruction v -> new IrLoadPointerInstruction(v.result(), parameterValue(v.address(), homes), v.volatileAccess(), v.range());
            case IrStorePointerInstruction v -> new IrStorePointerInstruction(parameterValue(v.address(), homes), parameterValue(v.value(), homes), v.volatileAccess(), v.range());
            case IrStoreLocalInstruction v -> new IrStoreLocalInstruction(v.local(), parameterValue(v.value(), homes), v.volatileAccess(), v.range());
            case IrElementAddressInstruction v -> new IrElementAddressInstruction(v.result(), parameterValue(v.baseAddress(), homes), parameterValue(v.index(), homes), v.elementType(), v.elementSizeBytes(), v.range());
            case IrFieldAddressInstruction v -> new IrFieldAddressInstruction(v.result(), parameterValue(v.baseAddress(), homes), v.ownerStructName(), v.fieldName(), v.offset(), v.fieldType(), v.range());
            case IrMemCopyInstruction v -> new IrMemCopyInstruction(parameterValue(v.destination(), homes), parameterValue(v.source(), homes), v.sizeBytes(), v.volatileAccess(), v.range());
            case IrCallInstruction v -> new IrCallInstruction(v.result(), v.calleeName(), v.arguments().stream().map(value -> parameterValue(value, homes)).toList(), v.variadic(), v.range());
            case IrIndirectCallInstruction v -> new IrIndirectCallInstruction(v.result(), parameterValue(v.calleeAddress(), homes), v.arguments().stream().map(value -> parameterValue(value, homes)).toList(), v.variadic(), v.range());
            case IrBranchInstruction v -> new IrBranchInstruction(parameterValue(v.condition(), homes), v.thenLabel(), v.elseLabel(), v.range());
            case IrReturnInstruction v -> new IrReturnInstruction(v.value() == null ? null : parameterValue(v.value(), homes), v.range());
            case IrCheckNonZeroInstruction v -> new IrCheckNonZeroInstruction(parameterValue(v.value(), homes), v.range());
            default -> instruction;
        };
        return rewritten.equals(instruction) ? instruction : rewritten;
    }
}
