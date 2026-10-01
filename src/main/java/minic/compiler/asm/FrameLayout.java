package minic.compiler.asm;

import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrCastInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.CallInstruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrStorePointerInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrSelectInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrUnaryInstruction;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrValue.IrTemporary;
import minic.compiler.ir.optimize.TemporarySlotPlan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Collections;

record FrameLayout(
        Map<String, Integer> parameterOffsets,
        Map<String, IrType> parameterTypes,
        Map<String, Integer> localOffsets,
        Map<String, Integer> localInitializedOffsets,
        Map<String, Integer> temporaryOffsets,
        int outgoingArgumentAreaSize,
        int frameSize,
        TemporarySlotPlan temporarySlotPlan,
        Map<String, Integer> calleeSavedOffsets,
        int stackAlignment
) {
    FrameLayout(Map<String, Integer> parameterOffsets, Map<String, IrType> parameterTypes,
                Map<String, Integer> localOffsets, Map<String, Integer> localInitializedOffsets,
                Map<String, Integer> temporaryOffsets, int outgoingArgumentAreaSize, int frameSize,
                TemporarySlotPlan temporarySlotPlan, Map<String, Integer> calleeSavedOffsets) {
        this(parameterOffsets, parameterTypes, localOffsets, localInitializedOffsets, temporaryOffsets,
                outgoingArgumentAreaSize, frameSize, temporarySlotPlan, calleeSavedOffsets, 16);
    }

    boolean realigned() { return stackAlignment > 16; }

    String originalFrameSlot() {
        if (!realigned()) throw new IllegalStateException("ordinary frame has no saved entry base");
        return "QWORD PTR [rbp-8]";
    }

    FrameLayout(Map<String, Integer> parameterOffsets, Map<String, IrType> parameterTypes,
                Map<String, Integer> localOffsets, Map<String, Integer> localInitializedOffsets,
                Map<String, Integer> temporaryOffsets, int outgoingArgumentAreaSize, int frameSize,
                TemporarySlotPlan temporarySlotPlan) {
        this(parameterOffsets, parameterTypes, localOffsets, localInitializedOffsets, temporaryOffsets,
                outgoingArgumentAreaSize, frameSize, temporarySlotPlan, Map.of());
    }

    /** Append private, full-width save slots without changing any existing object or ABI offset. */
    FrameLayout withCalleeSavedRegisters(List<String> registers) {
        if (registers.isEmpty()) return this;
        if (!calleeSavedOffsets.isEmpty()) throw new IllegalArgumentException("callee-saved homes already assigned");
        var offsets = new LinkedHashMap<String, Integer>();
        // Both sizes include alignment. The old outgoing area will move down with the new rsp.
        int nextOffset = frameSize - outgoingArgumentAreaSize;
        for (String register : registers) {
            if (!Set.of("rbx", "r12", "r13", "r14", "r15").contains(register) || offsets.containsKey(register))
                throw new IllegalArgumentException("invalid callee-saved register: " + register);
            nextOffset = Math.addExact(nextOffset, Long.BYTES);
            offsets.put(register, nextOffset);
        }
        return new FrameLayout(parameterOffsets, parameterTypes, localOffsets, localInitializedOffsets,
                temporaryOffsets, outgoingArgumentAreaSize,
                CallingConvention.alignTo16(Math.addExact(nextOffset, outgoingArgumentAreaSize)),
                temporarySlotPlan, Collections.unmodifiableMap(offsets), stackAlignment);
    }

    static FrameLayout create(IrFunction function, boolean reuseTemporarySlots) {
        FrameLayout baseline = create(function);
        if (!reuseTemporarySlots) return baseline;
        TemporarySlotPlan plan = TemporarySlotPlan.allocate(function);
        Set<String> checkedLocals = new HashSet<>();
        Set<String> addressedLocals = new HashSet<>();
        for (var block : function.blocks()) for (IrInstruction instruction : block.instructions()) {
            if (instruction instanceof IrCheckInitializedInstruction check) checkedLocals.add(check.local().name());
            if (instruction instanceof IrAddressOfLocalInstruction address) addressedLocals.add(address.local().name());
        }
        // Match the native check contract: private flags cannot observe writes through aliases.
        checkedLocals.removeAll(addressedLocals);
        LinkedHashMap<String, Integer> localOffsets = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> initializedOffsets = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> temporaries = new LinkedHashMap<>();
        int nextOffset = baseline.parameterOffsets().values().stream().mapToInt(Integer::intValue).max().orElse(baseline.realigned() ? Long.BYTES : 0);
        for (var block : function.blocks()) for (IrInstruction instruction : block.instructions()) {
            IrLocal local = switch (instruction) {
                case IrDeclareLocalInstruction value -> value.local();
                case IrCheckInitializedInstruction value -> value.local();
                case IrAddressOfLocalInstruction value -> value.local();
                case IrLoadLocalInstruction value -> value.local();
                case IrStoreLocalInstruction value -> value.local();
                default -> null;
            };
            if (local != null) nextOffset = ensureLocal(local, localOffsets, initializedOffsets, nextOffset,
                    checkedLocals.contains(local.name()));
        }
        // Fixed homes and source objects never alias temporary slots. Plan offsets require an aligned base.
        int temporaryBase = plan.temporaryCount() == 0 ? nextOffset : alignTo(nextOffset, Long.BYTES);
        plan.slots().forEach((name, slot) -> temporaries.put(name, Math.addExact(temporaryBase, slot.offset())));
        int frameSize = CallingConvention.alignTo16(Math.addExact(baseline.outgoingArgumentAreaSize(), Math.addExact(temporaryBase, plan.storageBytes())));
        // Alignment padding in a tiny function must not increase the native frame.
        if (frameSize > baseline.frameSize()) {
            var retainedFlags = new LinkedHashMap<>(baseline.localInitializedOffsets());
            retainedFlags.keySet().retainAll(checkedLocals);
            return new FrameLayout(baseline.parameterOffsets(), baseline.parameterTypes(), baseline.localOffsets(),
                    retainedFlags, baseline.temporaryOffsets(), baseline.outgoingArgumentAreaSize(), baseline.frameSize(), null, Map.of(), baseline.stackAlignment());
        }
        return new FrameLayout(baseline.parameterOffsets(), baseline.parameterTypes(), localOffsets, initializedOffsets,
                temporaries, baseline.outgoingArgumentAreaSize(), frameSize, plan, Map.of(), baseline.stackAlignment());
    }

    static FrameLayout create(IrFunction function) {
        LinkedHashMap<String, Integer> parameterOffsets = new LinkedHashMap<>();
        LinkedHashMap<String, IrType> parameterTypes = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> localOffsets = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> localInitializedOffsets = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> temporaryOffsets = new LinkedHashMap<>();
        int outgoingArgumentAreaSize = collectOutgoingArgumentAreaSize(function);
        int alignment = requiredStackAlignment(function);
        // In realigned frames the first private slot retains the unaligned entry frame.
        int nextOffset = alignment > 16 ? Long.BYTES : 0;
        for (IrParameter parameter : function.parameters()) {
            nextOffset += slotSize(parameter.type());
            parameterOffsets.put(parameter.name(), nextOffset);
            parameterTypes.put(parameter.name(), parameter.type());
        }
        for (var block : function.blocks()) {
            for (IrInstruction instruction : block.instructions()) {
                nextOffset = collectSlots(
                        instruction,
                        localOffsets,
                        localInitializedOffsets,
                        temporaryOffsets,
                        nextOffset
                );
            }
        }
        int frameSize = CallingConvention.alignTo16(outgoingArgumentAreaSize + nextOffset);
        return new FrameLayout(
                parameterOffsets,
                parameterTypes,
                localOffsets,
                localInitializedOffsets,
                temporaryOffsets,
                outgoingArgumentAreaSize,
                frameSize,
                null, Map.of(), alignment
        );
    }

    private static int requiredStackAlignment(IrFunction function) {
        int alignment = 16;
        for (var block : function.blocks()) for (IrInstruction instruction : block.instructions()) {
            IrLocal local = switch (instruction) {
                case IrDeclareLocalInstruction value -> value.local();
                case IrCheckInitializedInstruction value -> value.local();
                case IrAddressOfLocalInstruction value -> value.local();
                case IrLoadLocalInstruction value -> value.local();
                case IrStoreLocalInstruction value -> value.local();
                default -> null;
            };
            if (local != null && !local.incomingArgumentArea()) alignment = Math.max(alignment, local.alignmentBytes());
        }
        return alignment;
    }

    String parameterSlot(String name) {
        return stackSlot(parameterOffsets.get(name), parameterTypes.get(name));
    }

    String parameterSlot(String name, IrType type) {
        return stackSlot(parameterOffsets.get(name), type);
    }

    String parameterAddress(String name) {
        return stackAddress(parameterOffsets.get(name));
    }

    String localSlot(IrLocal local) {
        if (local.incomingArgumentArea()) {
            throw new IllegalArgumentException("incoming argument pseudo local has no frame slot");
        }
        return stackSlot(localOffsets.get(local.name()), local.type());
    }

    String localInitializedSlot(IrLocal local) {
        if (local.incomingArgumentArea()) {
            throw new IllegalArgumentException("incoming argument pseudo local has no initialized flag");
        }
        return stackSlot(localInitializedOffsets.get(local.name()), IrType.INT);
    }

    boolean hasLocalInitializedFlag(IrLocal local) {
        return localInitializedOffsets.containsKey(local.name());
    }

    String temporarySlot(IrTemporary temporary) {
        return stackSlot(temporaryOffsets.get(temporary.name()), temporary.type());
    }

    String stackAddress(Integer offset) {
        if (offset == null) {
            throw new IllegalArgumentException("missing stack slot");
        }
        return "[rbp-" + offset + "]";
    }

    String localAddress(IrLocal local) {
        if (local.incomingArgumentArea()) {
            return "[rbp+" + CallingConvention.incomingArgumentSlotOffset(local.incomingArgumentIndex()) + "]";
        }
        return stackAddress(localOffsets.get(local.name()));
    }

    String outgoingStackArgumentSlot(int argumentIndex) {
        if (CallingConvention.isRegisterArgument(argumentIndex)) {
            throw new IllegalArgumentException("register argument has no outgoing stack slot");
        }
        return "DWORD PTR [rsp+" + CallingConvention.outgoingStackArgumentOffset(argumentIndex) + "]";
    }

    String outgoingStackArgumentSlot(int argumentIndex, IrType type) {
        if (CallingConvention.isRegisterArgument(argumentIndex)) {
            throw new IllegalArgumentException("register argument has no outgoing stack slot");
        }
        return memoryPrefix(type) + " [rsp+" + CallingConvention.outgoingStackArgumentOffset(argumentIndex) + "]";
    }

    private static int collectOutgoingArgumentAreaSize(IrFunction function) {
        int maxArgumentCount = 0;
        for (var block : function.blocks()) {
            for (IrInstruction instruction : block.instructions()) {
                if (instruction instanceof IrCallInstruction call) {
                    maxArgumentCount = Math.max(maxArgumentCount, call.arguments().size());
                } else if (instruction instanceof IrIndirectCallInstruction call) {
                    maxArgumentCount = Math.max(maxArgumentCount, call.arguments().size());
                }
            }
        }
        return CallingConvention.outgoingArgumentAreaSize(maxArgumentCount);
    }

    private static int collectSlots(
            IrInstruction instruction,
            Map<String, Integer> localOffsets,
            Map<String, Integer> localInitializedOffsets,
            Map<String, Integer> temporaryOffsets,
            int nextOffset
    ) {
        if (instruction instanceof IrDeclareLocalInstruction declareLocal) {
            nextOffset = ensureLocal(declareLocal.local(), localOffsets, localInitializedOffsets, nextOffset);
        } else if (instruction instanceof IrLoadLocalInstruction loadLocal) {
            nextOffset = ensureLocal(loadLocal.local(), localOffsets, localInitializedOffsets, nextOffset);
            nextOffset = ensureTemporary(loadLocal.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrBinaryInstruction binary) {
            nextOffset = ensureTemporary(binary.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrCastInstruction cast) {
            nextOffset = ensureTemporary(cast.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrUnaryInstruction unary) {
            nextOffset = ensureTemporary(unary.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrSelectInstruction select) {
            nextOffset = ensureTemporary(select.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrMoveInstruction move) {
            nextOffset = ensureTemporary(move.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrCallInstruction call) {
            if (call.result() != null) {
                nextOffset = ensureTemporary(call.result(), temporaryOffsets, nextOffset);
            }
        } else if (instruction instanceof IrIndirectCallInstruction call) {
            if (call.result() != null) {
                nextOffset = ensureTemporary(call.result(), temporaryOffsets, nextOffset);
            }
        } else if (instruction instanceof IrAddressOfLocalInstruction addressOfLocal) {
            nextOffset = ensureLocal(addressOfLocal.local(), localOffsets, localInitializedOffsets, nextOffset);
            nextOffset = ensureTemporary(addressOfLocal.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrLoadPointerInstruction loadPointer) {
            nextOffset = ensureTemporary(loadPointer.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrElementAddressInstruction elementAddress) {
            nextOffset = ensureTemporary(elementAddress.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrFieldAddressInstruction fieldAddress) {
            nextOffset = ensureTemporary(fieldAddress.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrStoreLocalInstruction storeLocal) {
            nextOffset = ensureLocal(storeLocal.local(), localOffsets, localInitializedOffsets, nextOffset);
        } else if (instruction instanceof IrStorePointerInstruction) {
            return nextOffset;
        } else if (instruction instanceof IrCheckInitializedInstruction checkInitialized) {
            nextOffset = ensureLocal(checkInitialized.local(), localOffsets, localInitializedOffsets, nextOffset);
        }
        return nextOffset;
    }

    private static int ensureLocal(
            IrLocal local,
            Map<String, Integer> localOffsets,
            Map<String, Integer> localInitializedOffsets,
            int nextOffset
    ) {
        return ensureLocal(local, localOffsets, localInitializedOffsets, nextOffset, true);
    }

    private static int ensureLocal(IrLocal local, Map<String, Integer> localOffsets,
                                   Map<String, Integer> localInitializedOffsets, int nextOffset, boolean needsFlag) {
        if (local.incomingArgumentArea()) {
            return nextOffset;
        }
        if (!localOffsets.containsKey(local.name())) {
            nextOffset = alignTo(nextOffset + local.sizeBytes(), local.alignmentBytes());
            localOffsets.put(local.name(), nextOffset);
            if (needsFlag) {
                nextOffset += 4;
                localInitializedOffsets.put(local.name(), nextOffset);
            }
        }
        return nextOffset;
    }

    private static int alignTo(int value, int alignment) {
        int remainder = value % alignment;
        return remainder == 0 ? value : value + alignment - remainder;
    }

    private static int ensureTemporary(
            IrTemporary temporary,
            Map<String, Integer> temporaryOffsets,
            int nextOffset
    ) {
        if (!temporaryOffsets.containsKey(temporary.name())) {
            nextOffset += slotSize(temporary.type());
            temporaryOffsets.put(temporary.name(), nextOffset);
        }
        return nextOffset;
    }

    private static int slotSize(IrType type) {
        return type.sizeBytes();
    }

    private String stackSlot(Integer offset, IrType type) {
        if (offset == null) {
            throw new IllegalArgumentException("missing stack slot");
        }
        return memoryPrefix(type) + " [rbp-" + offset + "]";
    }

    private static String memoryPrefix(IrType type) {
        return switch (type.sizeBytes()) {
            case 1 -> "BYTE PTR";
            case 2 -> "WORD PTR";
            case 4 -> "DWORD PTR";
            case 8 -> "QWORD PTR";
            default -> throw new IllegalArgumentException("unsupported IR type size: " + type);
        };
    }
}
