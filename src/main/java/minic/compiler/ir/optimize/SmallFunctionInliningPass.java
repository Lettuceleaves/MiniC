package minic.compiler.ir.optimize;

import minic.compiler.ir.IrResult;
import minic.SourceRange;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.TypeLayout;

import java.util.*;

/** Bounded, native-only expansion of small direct calls. */
public final class SmallFunctionInliningPass implements IrPass {
    public record Limits(int maxCalleeInstructions, int maxCalleeBlocks, int maxCallerGrowth,
                         int maxModuleGrowth, int maxAddedFrameBytes, int maxSitesPerCaller) {
        public Limits {
            if (maxCalleeInstructions < 1 || maxCalleeBlocks < 1 || maxCallerGrowth < 0
                    || maxModuleGrowth < 0 || maxAddedFrameBytes < 0 || maxSitesPerCaller < 0)
                throw new IllegalArgumentException("invalid inlining limits");
        }
        public static Limits defaults() { return new Limits(24, 6, 96, 512, 256, 8); }
    }
    private final Limits limits;
    public SmallFunctionInliningPass() { this(Limits.defaults()); }
    public SmallFunctionInliningPass(Limits limits) { this.limits = Objects.requireNonNull(limits); }
    @Override public String name() { return "small-function-inlining"; }
    @Override public IrResult apply(IrResult input) {
        Objects.requireNonNull(input, "input");
        Map<String, Set<String>> calls = new LinkedHashMap<>();
        for (IrFunction function : input.functions()) {
            var flow = IrControlFlow.analyze(function);
            var edges = new LinkedHashSet<String>();
            for (String label : flow.reachable()) for (IrInstruction instruction : flow.effectiveInstructions(label))
                if (instruction instanceof IrCallInstruction call) edges.add(call.calleeName());
            calls.put(function.name(), edges);
        }
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (IrFunction function : input.functions()) {
            if (recursive(function.name(), calls)) continue;
            Candidate candidate = candidate(function);
            if (candidate != null) candidates.put(function.name(), candidate);
        }
        if (candidates.isEmpty()) return input;

        int moduleGrowth = 0;
        boolean changed = false;
        var functions = new ArrayList<IrFunction>();
        for (IrFunction caller : input.functions()) {
            var names = new Names(caller);
            var callerFlow = IrControlFlow.analyze(caller);
            var blocks = new ArrayList<IrBlock>();
            int callerGrowth = 0, frameGrowth = 0, sites = 0;
            for (IrBlock block : caller.blocks()) {
                String label = block.label();
                var body = new ArrayList<IrInstruction>();
                boolean executable = callerFlow.reachable().contains(label);
                for (IrInstruction instruction : block.instructions()) {
                    Candidate candidate = executable && instruction instanceof IrCallInstruction call && !call.variadic()
                            ? candidates.get(call.calleeName()) : null;
                    if (candidate != null && sites < limits.maxSitesPerCaller()) {
                        var call = (IrCallInstruction) instruction;
                        var expansion = new Expansion(candidate, call, names);
                        int growth = Math.max(0, expansion.instructionCount() - 1);
                        int frame = expansion.frameBytes();
                        if (growth <= limits.maxCallerGrowth() - callerGrowth
                                && growth <= limits.maxModuleGrowth() - moduleGrowth
                                && frame <= limits.maxAddedFrameBytes() - frameGrowth) {
                            sites++; callerGrowth += growth; moduleGrowth += growth; frameGrowth += frame;
                            body.addAll(expansion.setup);
                            if (expansion.straightLine) {
                                body.addAll(expansion.blocks.getFirst().instructions());
                            } else {
                                body.add(new IrJumpInstruction(expansion.blocks.getFirst().label(), call.range()));
                                blocks.add(new IrBlock(label, body));
                                blocks.addAll(expansion.blocks);
                                label = expansion.continuation;
                                body = new ArrayList<>();
                            }
                            continue;
                        }
                    }
                    body.add(instruction);
                    if (IrControlFlow.isTerminator(instruction)) executable = false;
                }
                blocks.add(new IrBlock(label, body));
            }
            if (sites == 0) functions.add(caller);
            else {
                changed = true;
                functions.add(new IrFunction(caller.name(), caller.returnType(), caller.parameters(), caller.variadic(), blocks, caller.range()));
            }
        }
        return !changed ? input : new IrResult(functions, input.stringData(), input.globalData(), input.externalFunctionNames(),
                input.externalObjectNames(), input.structLayouts(), input.currentAstNode(), input.currentSubject(), input.displayNames());
    }

    private Candidate candidate(IrFunction function) {
        if (function.variadic() || function.blocks().isEmpty()) return null;
        var flow = IrControlFlow.analyze(function);
        if (flow.reachable().size() > limits.maxCalleeBlocks()) return null;
        int count = 0;
        boolean hasReturn = false;
        var storedParameters = new HashSet<String>();
        for (IrParameter parameter : function.parameters())
            if (parameter.declaredType().isVolatileQualified()) storedParameters.add(parameter.name());
        for (String label : flow.reachable()) for (IrInstruction instruction : flow.effectiveInstructions(label)) {
            if (++count > limits.maxCalleeInstructions()) return null;
            if (instruction instanceof IrReturnInstruction) hasReturn = true;
            // These checks return from the current native frame; cloning them would return from the wrong function.
            if (instruction instanceof IrCheckInitializedInstruction || instruction instanceof IrCheckNonZeroInstruction
                    || instruction instanceof IrTrapInstruction || instruction instanceof IrIndirectCallInstruction) return null;
            IrLocal local = localOf(instruction);
            if (local != null && local.incomingArgumentArea()) return null;
            for (IrValue value : IrValueUses.inputs(instruction))
                if (value instanceof IrParameterAddress address) storedParameters.add(address.name());
        }
        for (IrParameter parameter : function.parameters())
            if (storedParameters.contains(parameter.name()) && (parameter.declaredType().isStruct() || parameter.declaredType().isArray())) return null;
        // Removing a never-returning call can orphan its result symbol in unreachable caller suffixes.
        // Keep that boundary instead of manufacturing a bogus definition just to satisfy the verifier.
        return hasReturn ? new Candidate(function, flow, Set.copyOf(storedParameters)) : null;
    }

    private static boolean recursive(String start, Map<String, Set<String>> calls) {
        var visited = new HashSet<String>();
        var pending = new ArrayDeque<>(calls.getOrDefault(start, Set.of()));
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            if (next.equals(start)) return true;
            if (visited.add(next)) pending.addAll(calls.getOrDefault(next, Set.of()));
        }
        return false;
    }

    private record Candidate(IrFunction function, IrControlFlow flow, Set<String> storedParameters) { }

    private static IrLocal localOf(IrInstruction instruction) {
        return switch (instruction) {
            case IrDeclareLocalInstruction i -> i.local();
            case IrCheckInitializedInstruction i -> i.local();
            case IrAddressOfLocalInstruction i -> i.local();
            case IrLoadLocalInstruction i -> i.local();
            case IrStoreLocalInstruction i -> i.local();
            default -> null;
        };
    }

    /** One shared namespace avoids collisions even with user-constructed IR names. */
    private static final class Names {
        private final Set<String> used = new HashSet<>();
        private int next;
        Names(IrFunction function) {
            function.parameters().forEach(p -> used.add(p.name()));
            for (IrBlock block : function.blocks()) {
                used.add(block.label());
                for (IrInstruction instruction : block.instructions()) {
                    IrTemporary result = IrValueUses.result(instruction);
                    if (result != null) used.add(result.name());
                    IrLocal local = localOf(instruction);
                    if (local != null) used.add(local.name());
                    for (IrValue input : IrValueUses.inputs(instruction))
                        if (input instanceof IrTemporary temporary) used.add(temporary.name());
                }
            }
        }
        String fresh() { String name; do { name = "__inline_" + next++; } while (!used.add(name)); return name; }
    }

    private static final class Expansion {
        final List<IrInstruction> setup = new ArrayList<>();
        final List<IrBlock> blocks = new ArrayList<>();
        final String continuation;
        final boolean straightLine;
        final Names names;
        final Map<String, IrTemporary> temporaries = new HashMap<>();
        final Map<String, IrLocal> locals = new HashMap<>();
        final Map<String, IrValue> parameters = new HashMap<>();
        final Map<String, IrLocal> parameterSlots = new HashMap<>();
        final Map<String, IrTemporary> parameterAddresses = new HashMap<>();
        final Map<String, String> labels = new HashMap<>();
        int temporaryCount;

        Expansion(Candidate candidate, IrCallInstruction call, Names names) {
            this.names = names;
            continuation = names.fresh();
            var flow = candidate.flow();
            var function = candidate.function();
            var entry = flow.effectiveInstructions(function.blocks().getFirst().label());
            straightLine = flow.reachable().size() == 1 && !entry.isEmpty() && entry.getLast() instanceof IrReturnInstruction;
            for (IrBlock block : function.blocks())
                if (flow.reachable().contains(block.label())) labels.put(block.label(), names.fresh());
            // Capture all actuals before any callee instruction: parameter refs denote live mutable caller slots.
            for (int index = 0; index < function.parameters().size(); index++) {
                IrParameter parameter = function.parameters().get(index);
                IrTemporary snapshot = freshTemporary(parameter.type());
                setup.add(new IrMoveInstruction(snapshot, call.arguments().get(index), call.range()));
                parameters.put(parameter.name(), snapshot);
            }
            for (IrParameter parameter : function.parameters()) {
                if (!candidate.storedParameters().contains(parameter.name())) continue;
                var type = parameter.declaredType();
                var slot = new IrLocal(names.fresh(), parameter.name(), type, parameter.type(),
                        TypeLayout.sizeOf(type), TypeLayout.alignmentOf(type), parameter.range());
                parameterSlots.put(parameter.name(), slot);
                setup.add(new IrDeclareLocalInstruction(slot, call.range()));
                setup.add(new IrStoreLocalInstruction(slot, parameters.get(parameter.name()), type.isVolatileQualified(), call.range()));
                IrTemporary address = freshTemporary(IrType.POINTER);
                parameterAddresses.put(parameter.name(), address);
                setup.add(new IrAddressOfLocalInstruction(address, slot, call.range()));
            }
            for (IrBlock block : function.blocks()) {
                if (!flow.reachable().contains(block.label())) continue;
                var body = new ArrayList<IrInstruction>();
                for (IrInstruction instruction : flow.effectiveInstructions(block.label())) {
                    if (instruction instanceof IrReturnInstruction ret) {
                        IrValue returned = ret.value() == null ? null : value(ret.value(), body, ret.range());
                        if (call.result() != null) body.add(new IrMoveInstruction(call.result(), returned, ret.range()));
                        if (!straightLine) body.add(new IrJumpInstruction(continuation, ret.range()));
                    } else body.add(clone(instruction, body));
                }
                blocks.add(new IrBlock(labels.get(block.label()), body));
            }
        }

        int instructionCount() { return setup.size() + blocks.stream().mapToInt(b -> b.instructions().size()).sum() + (straightLine ? 0 : 1); }
        int frameBytes() {
            long size = 8L * temporaryCount;
            for (IrLocal local : locals.values()) size += (long) local.sizeBytes() + local.alignmentBytes() - 1 + 8;
            for (IrLocal local : parameterSlots.values()) size += (long) local.sizeBytes() + local.alignmentBytes() - 1 + 8;
            // Function alignment may consume a further 15 bytes after any inserted storage.
            return (int) Math.min(Integer.MAX_VALUE, size == 0 ? 0 : size + 15);
        }
        IrTemporary freshTemporary(IrType type) { temporaryCount++; return new IrTemporary(names.fresh(), type); }
        IrTemporary temporary(IrTemporary old) {
            return old == null ? null : temporaries.computeIfAbsent(old.name(), ignored -> freshTemporary(old.type()));
        }
        IrLocal local(IrLocal old) {
            return locals.computeIfAbsent(old.name(), ignored -> new IrLocal(names.fresh(), old.sourceName(), old.declaredType(),
                    old.type(), old.sizeBytes(), old.alignmentBytes(), old.storageKind(), old.incomingArgumentIndex(), old.range()));
        }
        IrValue value(IrValue old, List<IrInstruction> before, SourceRange range) {
            return switch (old) {
                case IrTemporary temp -> temporary(temp);
                case IrParameterAddress address -> parameterAddresses.get(address.name());
                case IrParameterRef reference -> {
                    IrLocal slot = parameterSlots.get(reference.name());
                    if (slot == null) yield parameters.get(reference.name());
                    IrTemporary loaded = freshTemporary(reference.type());
                    before.add(new IrLoadLocalInstruction(loaded, slot, slot.declaredType().isVolatileQualified(), range));
                    yield loaded;
                }
                default -> old;
            };
        }
        List<IrValue> values(List<IrValue> old, List<IrInstruction> before, SourceRange range) {
            var mapped = new ArrayList<IrValue>();
            for (IrValue value : old) mapped.add(value(value, before, range));
            return List.copyOf(mapped);
        }
        IrInstruction clone(IrInstruction instruction, List<IrInstruction> before) {
            SourceRange r = instruction.range();
            return switch (instruction) {
                case IrBinaryInstruction i -> new IrBinaryInstruction(temporary(i.result()), i.operator(), value(i.left(), before, r), value(i.right(), before, r), r);
                case IrUnaryInstruction i -> new IrUnaryInstruction(temporary(i.result()), i.operator(), value(i.operand(), before, r), r);
                case IrCastInstruction i -> new IrCastInstruction(temporary(i.result()), value(i.value(), before, r), r);
                case IrMoveInstruction i -> new IrMoveInstruction(temporary(i.result()), value(i.value(), before, r), r);
                case IrSelectInstruction i -> new IrSelectInstruction(temporary(i.result()), value(i.condition(), before, r), value(i.thenValue(), before, r), value(i.elseValue(), before, r), r);
                case IrDeclareLocalInstruction i -> new IrDeclareLocalInstruction(local(i.local()), r);
                case IrAddressOfLocalInstruction i -> new IrAddressOfLocalInstruction(temporary(i.result()), local(i.local()), r);
                case IrLoadLocalInstruction i -> new IrLoadLocalInstruction(temporary(i.result()), local(i.local()), i.volatileAccess(), r);
                case IrStoreLocalInstruction i -> new IrStoreLocalInstruction(local(i.local()), value(i.value(), before, r), i.volatileAccess(), r);
                case IrLoadPointerInstruction i -> new IrLoadPointerInstruction(temporary(i.result()), value(i.address(), before, r), i.volatileAccess(), r);
                case IrStorePointerInstruction i -> new IrStorePointerInstruction(value(i.address(), before, r), value(i.value(), before, r), i.volatileAccess(), r);
                case IrElementAddressInstruction i -> new IrElementAddressInstruction(temporary(i.result()), value(i.baseAddress(), before, r), value(i.index(), before, r), i.elementType(), i.elementSizeBytes(), r);
                case IrFieldAddressInstruction i -> new IrFieldAddressInstruction(temporary(i.result()), value(i.baseAddress(), before, r), i.ownerStructName(), i.fieldName(), i.offset(), i.fieldType(), r);
                case IrMemCopyInstruction i -> new IrMemCopyInstruction(value(i.destination(), before, r), value(i.source(), before, r), i.sizeBytes(), i.volatileAccess(), r);
                case IrCallInstruction i -> new IrCallInstruction(temporary(i.result()), i.calleeName(), values(i.arguments(), before, r), i.variadic(), r);
                case IrBranchInstruction i -> new IrBranchInstruction(value(i.condition(), before, r), labels.get(i.thenLabel()), labels.get(i.elseLabel()), r);
                case IrJumpInstruction i -> new IrJumpInstruction(labels.get(i.targetLabel()), r);
                case IrCheckInitializedInstruction ignored -> throw new IllegalStateException("checked candidate");
                case IrCheckNonZeroInstruction ignored -> throw new IllegalStateException("checked candidate");
                case IrTrapInstruction ignored -> throw new IllegalStateException("debug candidate");
                case IrIndirectCallInstruction ignored -> throw new IllegalStateException("indirect candidate");
                case IrReturnInstruction ignored -> throw new IllegalStateException("return must join at call site");
            };
        }
    }
}
