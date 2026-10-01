package minic.compiler.ir;

import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrCastInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrSelectInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrUnaryInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrBranchInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrCheckNonZeroInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrReturnInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrMemCopyInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrGlobalData;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.IrFunctionAddress;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Final IR reachability pass used before handing a translation unit to ASM. */
final class IrReachability {
    private static final String ENTRY_FUNCTION = "main";

    private IrReachability() {
    }

    static Result prune(List<IrFunction> functions, Set<String> declaredExternals) {
        return prune(functions, declaredExternals, ENTRY_FUNCTION);
    }

    static Result prune(List<IrFunction> functions, Set<String> declaredExternals, String entryFunction) {
        return prune(functions,declaredExternals,entryFunction,List.of());
    }

    static Result prune(List<IrFunction> functions, Set<String> declaredExternals, String entryFunction,
                        List<IrGlobalData> globalData) {
        LinkedHashMap<String, IrFunction> locals = new LinkedHashMap<>();
        functions.forEach(function -> locals.put(function.name(), function));

        // IrLowerer is also used for isolated snippets. Without an executable entry,
        // preserve the complete module rather than guessing an external root.
        if (!locals.containsKey(entryFunction)) {
            return new Result(functions, declaredExternals);
        }

        LinkedHashSet<String> roots = new LinkedHashSet<>();
        roots.add(entryFunction);
        LinkedHashSet<String> staticFunctionTargets = new LinkedHashSet<>();
        for (IrGlobalData global : globalData) for (var address : global.addresses()) {
            if (address.kind() == IrGlobalData.AddressKind.FUNCTION) {
                staticFunctionTargets.add(address.symbol());
                if (locals.containsKey(address.symbol())) roots.add(address.symbol());
            }
        }
        // Any materialized local function address can escape through memory or an
        // indirect call. Treat every such target as a root, even when the address
        // occurs in code that later proves unreachable.
        for (IrFunction function : functions) {
            forEachFunctionAddress(function, name -> {
                if (locals.containsKey(name)) {
                    roots.add(name);
                }
            });
        }

        LinkedHashSet<String> reachable = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            if (!reachable.add(name)) {
                continue;
            }
            IrFunction function = locals.get(name);
            if (function == null) {
                continue;
            }
            forEachInstruction(function, instruction -> {
                if (instruction instanceof IrCallInstruction call
                        && locals.containsKey(call.calleeName())
                        && !reachable.contains(call.calleeName())) {
                    pending.addLast(call.calleeName());
                }
            });
        }

        ArrayList<IrFunction> retainedFunctions = new ArrayList<>();
        for (IrFunction function : functions) {
            if (reachable.contains(function.name())) {
                retainedFunctions.add(function);
            }
        }

        LinkedHashSet<String> referencedExternals = new LinkedHashSet<>();
        for (String name : staticFunctionTargets) {
            if (declaredExternals.contains(name)) referencedExternals.add(name);
        }
        for (IrFunction function : retainedFunctions) {
            forEachInstruction(function, instruction -> {
                if (instruction instanceof IrCallInstruction call
                        && declaredExternals.contains(call.calleeName())) {
                    referencedExternals.add(call.calleeName());
                }
                forEachValue(instruction, value -> {
                    if (value instanceof IrFunctionAddress address
                            && declaredExternals.contains(address.functionName())) {
                        referencedExternals.add(address.functionName());
                    }
                });
            });
        }
        return new Result(retainedFunctions, referencedExternals);
    }

    private static void forEachFunctionAddress(IrFunction function, Consumer<String> consumer) {
        forEachInstruction(function, instruction -> forEachValue(instruction, value -> {
            if (value instanceof IrFunctionAddress address) {
                consumer.accept(address.functionName());
            }
        }));
    }

    private static void forEachInstruction(IrFunction function, Consumer<IrInstruction> consumer) {
        function.blocks().forEach(block -> block.instructions().forEach(consumer));
    }

    private static void forEachValue(IrInstruction instruction, Consumer<IrValue> consumer) {
        if (instruction instanceof IrCallInstruction call) {
            call.arguments().forEach(consumer);
        } else if (instruction instanceof IrIndirectCallInstruction call) {
            consumer.accept(call.calleeAddress());
            call.arguments().forEach(consumer);
        } else if (instruction instanceof IrBinaryInstruction binary) {
            consumer.accept(binary.left());
            consumer.accept(binary.right());
        } else if (instruction instanceof IrUnaryInstruction unary) {
            consumer.accept(unary.operand());
        } else if (instruction instanceof IrCastInstruction cast) {
            consumer.accept(cast.value());
        } else if (instruction instanceof IrMoveInstruction move) {
            consumer.accept(move.value());
        } else if (instruction instanceof IrSelectInstruction select) {
            consumer.accept(select.condition());
            consumer.accept(select.thenValue());
            consumer.accept(select.elseValue());
        } else if (instruction instanceof IrBranchInstruction branch) {
            consumer.accept(branch.condition());
        } else if (instruction instanceof IrReturnInstruction returned) {
            returned.valueOptional().ifPresent(consumer);
        } else if (instruction instanceof IrCheckNonZeroInstruction check) {
            consumer.accept(check.value());
        } else if (instruction instanceof IrStoreLocalInstruction store) {
            consumer.accept(store.value());
        } else if (instruction instanceof IrLoadPointerInstruction load) {
            consumer.accept(load.address());
        } else if (instruction instanceof IrStorePointerInstruction store) {
            consumer.accept(store.address());
            consumer.accept(store.value());
        } else if (instruction instanceof IrElementAddressInstruction element) {
            consumer.accept(element.baseAddress());
            consumer.accept(element.index());
        } else if (instruction instanceof IrFieldAddressInstruction field) {
            consumer.accept(field.baseAddress());
        } else if (instruction instanceof IrMemCopyInstruction copy) {
            consumer.accept(copy.destination());
            consumer.accept(copy.source());
        }
    }

    record Result(List<IrFunction> functions, Set<String> externalFunctionNames) {
        Result {
            functions = List.copyOf(functions);
            externalFunctionNames = Set.copyOf(externalFunctionNames);
        }
    }
}
