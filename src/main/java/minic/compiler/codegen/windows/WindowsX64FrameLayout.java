package minic.compiler.codegen.windows;

import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrAddressOfLocalInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCastInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrElementAddressInstruction;
import minic.compiler.ir.instruction.IrFieldAddressInstruction;
import minic.compiler.ir.instruction.IrIndirectCallInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrLoadPointerInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.instruction.IrStorePointerInstruction;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.model.IrType;
import minic.compiler.ir.value.IrTemporary;

import java.util.LinkedHashMap;
import java.util.Map;

record WindowsX64FrameLayout(
        Map<String, Integer> parameterOffsets,
        Map<String, IrType> parameterTypes,
        Map<String, Integer> localOffsets,
        Map<String, Integer> localInitializedOffsets,
        Map<String, Integer> temporaryOffsets,
        int outgoingArgumentAreaSize,
        int frameSize
) {
    static WindowsX64FrameLayout create(IrFunction function) {
        LinkedHashMap<String, Integer> parameterOffsets = new LinkedHashMap<>();
        LinkedHashMap<String, IrType> parameterTypes = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> localOffsets = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> localInitializedOffsets = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> temporaryOffsets = new LinkedHashMap<>();
        int outgoingArgumentAreaSize = collectOutgoingArgumentAreaSize(function);
        int nextOffset = 0;
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
        int frameSize = WindowsX64CallingConvention.alignTo16(outgoingArgumentAreaSize + nextOffset);
        return new WindowsX64FrameLayout(
                parameterOffsets,
                parameterTypes,
                localOffsets,
                localInitializedOffsets,
                temporaryOffsets,
                outgoingArgumentAreaSize,
                frameSize
        );
    }

    String parameterSlot(String name) {
        return stackSlot(parameterOffsets.get(name), parameterTypes.get(name));
    }

    String parameterSlot(String name, IrType type) {
        return stackSlot(parameterOffsets.get(name), type);
    }

    String localSlot(IrLocal local) {
        return stackSlot(localOffsets.get(local.name()), local.type());
    }

    String localInitializedSlot(IrLocal local) {
        return stackSlot(localInitializedOffsets.get(local.name()), IrType.INT);
    }

    String temporarySlot(IrTemporary temporary) {
        return stackSlot(temporaryOffsets.get(temporary.name()), temporary.type());
    }

    String stackAddress(Integer offset) {
        if (offset == null) {
            throw new IllegalArgumentException("missing stack slot");
        }
        return "[rbp-" + (outgoingArgumentAreaSize + offset) + "]";
    }

    String localAddress(IrLocal local) {
        return stackAddress(localOffsets.get(local.name()));
    }

    String outgoingStackArgumentSlot(int argumentIndex) {
        if (WindowsX64CallingConvention.isRegisterArgument(argumentIndex)) {
            throw new IllegalArgumentException("register argument has no outgoing stack slot");
        }
        return "DWORD PTR [rsp+" + WindowsX64CallingConvention.outgoingStackArgumentOffset(argumentIndex) + "]";
    }

    String outgoingStackArgumentSlot(int argumentIndex, IrType type) {
        if (WindowsX64CallingConvention.isRegisterArgument(argumentIndex)) {
            throw new IllegalArgumentException("register argument has no outgoing stack slot");
        }
        return memoryPrefix(type) + " [rsp+" + WindowsX64CallingConvention.outgoingStackArgumentOffset(argumentIndex) + "]";
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
        return WindowsX64CallingConvention.outgoingArgumentAreaSize(maxArgumentCount);
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
        } else if (instruction instanceof IrCallInstruction call) {
            nextOffset = ensureTemporary(call.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrIndirectCallInstruction call) {
            nextOffset = ensureTemporary(call.result(), temporaryOffsets, nextOffset);
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
        if (!localOffsets.containsKey(local.name())) {
            nextOffset += local.sizeBytes();
            localOffsets.put(local.name(), nextOffset);
            nextOffset += 4;
            localInitializedOffsets.put(local.name(), nextOffset);
        }
        return nextOffset;
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
        return slotSize(type, 1);
    }

    private static int slotSize(IrType type, int elementCount) {
        if (type == IrType.INT_ARRAY) {
            return 4 * elementCount;
        }
        return type.sizeBytes();
    }

    private String stackSlot(Integer offset, IrType type) {
        if (offset == null) {
            throw new IllegalArgumentException("missing stack slot");
        }
        return memoryPrefix(type) + " [rbp-" + (outgoingArgumentAreaSize + offset) + "]";
    }

    private static String memoryPrefix(IrType type) {
        return switch (type) {
            case BOOL, CHAR -> "BYTE PTR";
            case LONG, POINTER, DOUBLE -> "QWORD PTR";
            case INT, INT_ARRAY, STRUCT, FLOAT -> "DWORD PTR";
        };
    }
}
