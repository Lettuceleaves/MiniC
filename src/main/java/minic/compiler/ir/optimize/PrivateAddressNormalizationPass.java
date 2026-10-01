package minic.compiler.ir.optimize;

import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.type.MiniType;

import java.util.*;

/**
 * Normalizes exact, private integer and pointer addresses before local scalar promotion.
 * Any observable/unknown alias use rejects the entire object. Must-initialization facts
 * include declarations as lifetime resets, so restoring direct accesses cannot introduce
 * an initialization failure on a defined execution. This pass never changes the source IR.
 */
public final class PrivateAddressNormalizationPass implements IrPass {
    @Override public String name() { return "private-address-normalization"; }
    @Override public IrResult apply(IrResult source) {
        Objects.requireNonNull(source, "source");
        var functions = source.functions().stream().map(this::normalize).toList();
        return functions.equals(source.functions()) ? source : new IrResult(functions, source.stringData(), source.globalData(),
                source.externalFunctionNames(), source.externalObjectNames(), source.structLayouts(), source.currentAstNode(),
                source.currentSubject(), source.displayNames(), source.entryFunction());
    }

    private IrFunction normalize(IrFunction function) {
        if (function.blocks().isEmpty()) return function;
        var analysis = new Analysis(function);
        analysis.findAliases();
        analysis.rejectUnsupportedUses();
        analysis.proveInitializedReads();
        var selected = new LinkedHashSet<>(analysis.addressed);
        selected.removeAll(analysis.rejected);
        if (selected.isEmpty()) return function;

        // Only address-mutated, proven-private parameters need a mutable temporary home.
        // A nonescaping read-only address can instead read the original parameter directly.
        var homes = new LinkedHashMap<String, IrTemporary>();
        var entry = new ArrayList<IrInstruction>();
        int counter = 0;
        for (var parameter : function.parameters()) {
            Target target = analysis.parameters.get(parameter.name());
            if (!selected.contains(target) || !analysis.written.contains(target)) continue;
            String name;
            do { name = "__private_parameter_" + counter++; } while (!analysis.names.add(name));
            var home = new IrTemporary(name, parameter.type());
            homes.put(parameter.name(), home);
            entry.add(new IrMoveInstruction(home, parameter.ref(), parameter.range()));
        }
        var blocks = new ArrayList<IrBlock>();
        for (int index = 0; index < function.blocks().size(); index++) {
            var block = function.blocks().get(index);
            var code = new ArrayList<IrInstruction>();
            if (index == 0) code.addAll(entry);
            for (IrInstruction instruction : block.instructions()) {
                var result = IrValueUses.result(instruction);
                if (result != null && selected.contains(analysis.aliases.get(result.name()))) continue;
                if (instruction instanceof IrLoadPointerInstruction load && selected.contains(analysis.target(load.address()))) {
                    Target target = analysis.target(load.address());
                    code.add(target.local != null ? new IrLoadLocalInstruction(load.result(), target.local, load.range())
                            : new IrMoveInstruction(load.result(), parameterValue(target.parameter.ref(), homes), load.range()));
                } else if (instruction instanceof IrStorePointerInstruction store && selected.contains(analysis.target(store.address()))) {
                    Target target = analysis.target(store.address());
                    IrValue value = parameterValue(store.value(), homes);
                    code.add(target.local != null ? new IrStoreLocalInstruction(target.local, value, store.range())
                            : new IrMoveInstruction(homes.get(target.parameter.name()), value, store.range()));
                } else code.add(rewriteParameters(instruction, homes));
            }
            blocks.add(code.equals(block.instructions()) ? block : new IrBlock(block.label(), code));
        }
        return blocks.equals(function.blocks()) ? function : new IrFunction(function.name(), function.returnType(),
                function.parameters(), function.variadic(), blocks, function.range());
    }

    private record Target(IrLocal local, IrParameter parameter) {
        IrType type() { return local != null ? local.type() : parameter.type(); }
        MiniType declaredType() { return local != null ? local.declaredType() : parameter.declaredType(); }
    }

    private static final class Analysis {
        final IrFunction function;
        final IrControlFlow flow;
        final Map<String, Target> locals = new LinkedHashMap<>(), parameters = new LinkedHashMap<>();
        final Map<String, Target> aliases = new HashMap<>();
        final Map<String, Integer> definitions = new HashMap<>();
        final Set<Target> addressed = new LinkedHashSet<>(), rejected = new HashSet<>(), written = new HashSet<>();
        final Set<String> names = new HashSet<>();

        Analysis(IrFunction function) {
            this.function = function;
            flow = IrControlFlow.analyze(function);
            boolean argumentArea = function.variadic();
            for (var block : function.blocks()) {
                names.add(block.label());
                for (var instruction : block.instructions()) {
                    var result = IrValueUses.result(instruction);
                    if (result != null) { definitions.merge(result.name(), 1, Integer::sum); names.add(result.name()); }
                    IrLocal local = localOf(instruction);
                    if (local != null) {
                        names.add(local.name());
                        argumentArea |= local.incomingArgumentArea();
                        if (!local.incomingArgumentArea() && eligible(local.declaredType(), local.type())
                                && local.sizeBytes() == local.type().sizeBytes())
                            locals.putIfAbsent(local.name(), new Target(local, null));
                    }
                }
            }
            for (var parameter : function.parameters()) {
                names.add(parameter.name());
                if (!argumentArea && eligible(parameter.declaredType(), parameter.type()))
                    parameters.put(parameter.name(), new Target(null, parameter));
            }
        }

        Target target(IrValue value) {
            if (value instanceof IrTemporary temporary) return aliases.get(temporary.name());
            if (value instanceof IrParameterAddress address) return parameters.get(address.name());
            return null;
        }

        void findAliases() {
            for (var block : function.blocks()) for (var instruction : block.instructions()) {
                if (instruction instanceof IrAddressOfLocalInstruction address) {
                    Target target = locals.get(address.local().name());
                    if (target != null) {
                        addressed.add(target);
                        if (definitions.getOrDefault(address.result().name(), 0) == 1) aliases.put(address.result().name(), target);
                        else rejected.add(target);
                    }
                }
                for (var input : IrValueUses.inputs(instruction)) if (input instanceof IrParameterAddress address) {
                    Target target = parameters.get(address.name());
                    if (target != null) addressed.add(target);
                }
            }
            boolean changed;
            do {
                changed = false;
                for (var block : function.blocks()) for (var instruction : block.instructions()) {
                    IrTemporary result = IrValueUses.result(instruction);
                    if (result == null || result.type() != IrType.POINTER || definitions.get(result.name()) != 1
                            || aliases.containsKey(result.name())) continue;
                    Target target = derivedAddress(instruction);
                    if (target != null) { aliases.put(result.name(), target); changed = true; }
                }
            } while (changed);
        }

        Target derivedAddress(IrInstruction instruction) {
            if (instruction instanceof IrMoveInstruction move && move.value().type() == IrType.POINTER)
                return target(move.value());
            if (instruction instanceof IrCastInstruction cast && cast.value().type() == IrType.POINTER)
                return target(cast.value());
            if (instruction instanceof IrElementAddressInstruction element && zero(element.index())) {
                Target target = target(element.baseAddress());
                if (target != null && element.elementSizeBytes() == target.type().sizeBytes()
                        && element.elementType().equals(target.declaredType())) return target;
            }
            return null;
        }

        void rejectUnsupportedUses() {
            for (var block : function.blocks()) {
                int prefix = flow.effectiveInstructions(block.label()).size();
                for (int index = 0; index < block.instructions().size(); index++) {
                    IrInstruction instruction = block.instructions().get(index);
                    boolean executable = flow.reachable().contains(block.label()) && index < prefix;
                    IrLocal local = localOf(instruction);
                    Target localTarget = local == null ? null : locals.get(local.name());
                    if (localTarget != null && (!executable
                            || instruction instanceof IrLoadLocalInstruction load && load.volatileAccess()
                            || instruction instanceof IrStoreLocalInstruction store && store.volatileAccess())) rejected.add(localTarget);
                    IrTemporary result = IrValueUses.result(instruction);
                    Target derived = result == null ? null : aliases.get(result.name());
                    if (!executable && derived != null) rejected.add(derived);

                    for (IrValue input : IrValueUses.inputs(instruction)) {
                        if (!executable && input instanceof IrParameterRef reference) {
                            Target parameter = parameters.get(reference.name());
                            if (parameter != null) rejected.add(parameter);
                        }
                        Target target = target(input);
                        if (target == null) continue;
                        boolean allowed = executable && derived != null && derived.equals(target)
                                && target.equals(derivedAddress(instruction));
                        if (instruction instanceof IrLoadPointerInstruction load)
                            allowed = executable && input.equals(load.address()) && !load.volatileAccess()
                                    && load.result().type() == target.type();
                        else if (instruction instanceof IrStorePointerInstruction store)
                            allowed = executable && input.equals(store.address()) && !input.equals(store.value())
                                    && !store.volatileAccess() && store.value().type() == target.type();
                        if (!allowed) rejected.add(target);
                    }
                    if (instruction instanceof IrStorePointerInstruction store) {
                        Target target = target(store.address());
                        if (target != null) written.add(target);
                    }
                }
            }
            // An entry snapshot runs once per invocation only if no reachable edge returns
            // to entry. Keep writable parameter homes in that uncommon valid IR shape.
            if (flow.predecessors(function.blocks().getFirst().label()).stream().anyMatch(flow.reachable()::contains))
                for (Target target : written) if (target.parameter != null) rejected.add(target);
        }

        void proveInitializedReads() {
            var candidates = new HashSet<>(addressed);
            candidates.removeAll(rejected);
            candidates.removeIf(target -> target.local == null);
            if (candidates.isEmpty()) return;
            var outgoing = new HashMap<String, Set<Target>>();
            flow.reachable().forEach(label -> outgoing.put(label, Set.copyOf(candidates)));
            String entry = function.blocks().getFirst().label();
            boolean changed;
            do {
                changed = false;
                for (var block : function.blocks()) if (flow.reachable().contains(block.label())) {
                    Set<Target> state = incoming(block.label(), entry, outgoing);
                    transfer(flow.effectiveInstructions(block.label()), state, candidates, null);
                    if (!state.equals(outgoing.put(block.label(), state))) changed = true;
                }
            } while (changed);
            for (var block : function.blocks()) if (flow.reachable().contains(block.label()))
                transfer(flow.effectiveInstructions(block.label()), incoming(block.label(), entry, outgoing), candidates, rejected);
        }

        Set<Target> incoming(String label, String entry, Map<String, Set<Target>> outgoing) {
            var state = new HashSet<Target>();
            if (label.equals(entry)) return state;
            boolean first = true;
            for (String predecessor : flow.predecessors(label)) if (flow.reachable().contains(predecessor)) {
                if (first) { state.addAll(outgoing.get(predecessor)); first = false; }
                else state.retainAll(outgoing.get(predecessor));
            }
            return state;
        }

        void transfer(List<IrInstruction> code, Set<Target> state, Set<Target> candidates, Set<Target> unsafe) {
            for (var instruction : code) {
                IrLocal local = localOf(instruction);
                Target target = local == null ? null : locals.get(local.name());
                if (instruction instanceof IrDeclareLocalInstruction) state.remove(target);
                else if (instruction instanceof IrStoreLocalInstruction && candidates.contains(target)) state.add(target);
                else if (instruction instanceof IrStorePointerInstruction store && candidates.contains(target(store.address())))
                    state.add(target(store.address()));
                else if (unsafe != null) {
                    if (instruction instanceof IrLoadPointerInstruction load) target = target(load.address());
                    if ((instruction instanceof IrLoadLocalInstruction || instruction instanceof IrLoadPointerInstruction
                            || instruction instanceof IrCheckInitializedInstruction)
                            && candidates.contains(target) && !state.contains(target)) unsafe.add(target);
                }
            }
        }
    }

    private static boolean eligible(MiniType declaredType, IrType type) {
        return !declaredType.isVolatileQualified() && (declaredType.isIntegerScalar() || declaredType.isPointer())
                && (type.isIntegerScalar() || type == IrType.POINTER);
    }

    private static boolean zero(IrValue value) {
        if (!(value instanceof IrConstant constant) || !constant.type().isIntegerScalar()) return false;
        if (constant.type() == IrType.BOOL) return constant.value() == 0;
        int width = constant.type().sizeBytes() * Byte.SIZE;
        return (width == Long.SIZE ? constant.value() : constant.value() & ((1L << width) - 1)) == 0;
    }

    private static IrLocal localOf(IrInstruction instruction) {
        return switch (instruction) {
            case IrDeclareLocalInstruction v -> v.local();
            case IrCheckInitializedInstruction v -> v.local();
            case IrAddressOfLocalInstruction v -> v.local();
            case IrLoadLocalInstruction v -> v.local();
            case IrStoreLocalInstruction v -> v.local();
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
