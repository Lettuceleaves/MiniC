package minic.compiler.ir.optimize;

import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.value.IrValue.IrTemporary;

import java.util.*;

/** Native-only promotion of proven initialized, private integer and pointer locals. */
public final class LocalScalarPromotionPass implements IrPass {
    @Override public String name() { return "local-scalar-promotion"; }
    @Override public IrResult apply(IrResult input) {
        Objects.requireNonNull(input, "input");
        var functions = input.functions().stream().map(this::promote).toList();
        return functions.equals(input.functions()) ? input : new IrResult(functions, input.stringData(), input.globalData(),
                input.externalFunctionNames(), input.externalObjectNames(), input.structLayouts(), input.currentAstNode(),
                input.currentSubject(), input.displayNames(), input.entryFunction());
    }

    private IrFunction promote(IrFunction function) {
        if (function.blocks().isEmpty()) return function;
        var flow = IrControlFlow.analyze(function);
        var candidates = new LinkedHashMap<String, IrLocal>();
        var excluded = new HashSet<String>();
        var usedNames = new HashSet<String>();
        function.parameters().forEach(parameter -> usedNames.add(parameter.name()));
        for (var block : function.blocks()) {
            usedNames.add(block.label());
            int prefix = flow.effectiveInstructions(block.label()).size();
            for (int index = 0; index < block.instructions().size(); index++) {
                var instruction = block.instructions().get(index);
                var result = IrValueUses.result(instruction);
                if (result != null) usedNames.add(result.name());
                for (var value : IrValueUses.inputs(instruction))
                    if (value instanceof IrTemporary temporary) usedNames.add(temporary.name());
                IrLocal local = localOf(instruction);
                if (local == null) continue;
                usedNames.add(local.name()); candidates.putIfAbsent(local.name(), local);
                if (!eligible(local) || !flow.reachable().contains(block.label()) || index >= prefix
                        || instruction instanceof IrAddressOfLocalInstruction || instruction instanceof IrCheckInitializedInstruction
                        || instruction instanceof IrLoadLocalInstruction load && load.volatileAccess()
                        || instruction instanceof IrStoreLocalInstruction store && store.volatileAccess()) excluded.add(local.name());
            }
        }
        candidates.keySet().removeAll(excluded);
        if (candidates.isEmpty()) return function;

        // Prove every read independently of the presence/absence of frontend runtime checks.
        // This is a must analysis: TOP is the set of all candidate locals; the invocation edge
        // starts empty, and each declaration kills the previous lifetime's assignment fact.
        Set<String> universe = Set.copyOf(candidates.keySet());
        var outgoing = new HashMap<String, Set<String>>();
        flow.reachable().forEach(label -> outgoing.put(label, universe));
        String entry = function.blocks().getFirst().label();
        boolean changed;
        do {
            changed = false;
            for (var block : function.blocks()) if (flow.reachable().contains(block.label())) {
                var state = incoming(block.label(), entry, flow, outgoing);
                transfer(flow.effectiveInstructions(block.label()), state, universe, null);
                if (!state.equals(outgoing.put(block.label(), state))) changed = true;
            }
        } while (changed);
        var unsafe = new HashSet<String>();
        for (var block : function.blocks()) if (flow.reachable().contains(block.label()))
            transfer(flow.effectiveInstructions(block.label()), incoming(block.label(), entry, flow, outgoing), universe, unsafe);
        candidates.keySet().removeAll(unsafe);
        if (candidates.isEmpty()) return function;

        var homes = new HashMap<String, IrTemporary>();
        int next = 0;
        for (var local : candidates.values()) {
            String name; do { name = "__promoted_" + next++; } while (!usedNames.add(name));
            homes.put(local.name(), new IrTemporary(name, local.type()));
        }
        var blocks = new ArrayList<IrBlock>();
        for (var block : function.blocks()) {
            var code = new ArrayList<IrInstruction>();
            for (var instruction : block.instructions()) {
                IrLocal local = localOf(instruction);
                var home = local == null ? null : homes.get(local.name());
                if (home == null) code.add(instruction);
                else if (instruction instanceof IrStoreLocalInstruction store)
                    code.add(new IrMoveInstruction(home, store.value(), store.range()));
                else if (instruction instanceof IrLoadLocalInstruction load)
                    // The load result is a snapshot. A later store/call must not change its value.
                    code.add(new IrMoveInstruction(load.result(), home, load.range()));
                else if (!(instruction instanceof IrDeclareLocalInstruction))
                    throw new IllegalStateException("non-promotable local access: " + instruction);
            }
            blocks.add(new IrBlock(block.label(), code));
        }
        return new IrFunction(function.name(), function.returnType(), function.parameters(), function.variadic(), blocks, function.range());
    }

    private static boolean eligible(IrLocal local) {
        return !local.incomingArgumentArea() && !local.declaredType().isVolatileQualified()
                && (local.declaredType().isIntegerScalar() || local.declaredType().isPointer());
    }

    private static Set<String> incoming(String label, String entry, IrControlFlow flow, Map<String, Set<String>> outgoing) {
        var state = new HashSet<String>();
        if (label.equals(entry)) return state;
        boolean first = true;
        for (String predecessor : flow.predecessors(label)) if (flow.reachable().contains(predecessor)) {
            if (first) { state.addAll(outgoing.get(predecessor)); first = false; }
            else state.retainAll(outgoing.get(predecessor));
        }
        return state;
    }

    private static void transfer(List<IrInstruction> code, Set<String> state, Set<String> candidates, Set<String> unsafe) {
        for (var instruction : code) {
            if (instruction instanceof IrDeclareLocalInstruction declare) state.remove(declare.local().name());
            else if (instruction instanceof IrStoreLocalInstruction store && candidates.contains(store.local().name())) state.add(store.local().name());
            else if (unsafe != null && instruction instanceof IrLoadLocalInstruction load
                    && candidates.contains(load.local().name()) && !state.contains(load.local().name())) unsafe.add(load.local().name());
        }
    }

    private static IrLocal localOf(IrInstruction instruction) {
        return switch (instruction) {
            case IrDeclareLocalInstruction local -> local.local();
            case IrCheckInitializedInstruction local -> local.local();
            case IrAddressOfLocalInstruction local -> local.local();
            case IrLoadLocalInstruction local -> local.local();
            case IrStoreLocalInstruction local -> local.local();
            default -> null;
        };
    }
}
