package minic.compiler.codegen.windows;

import minic.compiler.ir.instruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.instruction.IrCheckInitializedInstruction;
import minic.compiler.ir.instruction.IrDeclareLocalInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrLoadLocalInstruction;
import minic.compiler.ir.instruction.IrStoreLocalInstruction;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.value.IrTemporary;

import java.util.LinkedHashMap;
import java.util.Map;

record WindowsX64FrameLayout(
        Map<String, Integer> parameterOffsets,
        Map<String, Integer> localOffsets,
        Map<String, Integer> localInitializedOffsets,
        Map<String, Integer> temporaryOffsets,
        int outgoingArgumentAreaSize,
        int frameSize
) {
    static WindowsX64FrameLayout create(IrFunction function) {
        LinkedHashMap<String, Integer> parameterOffsets = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> localOffsets = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> localInitializedOffsets = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> temporaryOffsets = new LinkedHashMap<>();
        int outgoingArgumentAreaSize = collectOutgoingArgumentAreaSize(function);
        int nextOffset = 0;
        for (IrParameter parameter : function.parameters()) {
            nextOffset += 4;
            parameterOffsets.put(parameter.name(), nextOffset);
        }
        for (IrInstruction instruction : function.blocks().getFirst().instructions()) {
            nextOffset = collectSlots(
                    instruction,
                    localOffsets,
                    localInitializedOffsets,
                    temporaryOffsets,
                    nextOffset
            );
        }
        int frameSize = WindowsX64CallingConvention.alignTo16(outgoingArgumentAreaSize + nextOffset);
        return new WindowsX64FrameLayout(
                parameterOffsets,
                localOffsets,
                localInitializedOffsets,
                temporaryOffsets,
                outgoingArgumentAreaSize,
                frameSize
        );
    }

    String parameterSlot(String name) {
        return stackSlot(parameterOffsets.get(name));
    }

    String localSlot(IrLocal local) {
        return stackSlot(localOffsets.get(local.name()));
    }

    String localInitializedSlot(IrLocal local) {
        return stackSlot(localInitializedOffsets.get(local.name()));
    }

    String temporarySlot(IrTemporary temporary) {
        return stackSlot(temporaryOffsets.get(temporary.name()));
    }

    String outgoingStackArgumentSlot(int argumentIndex) {
        if (WindowsX64CallingConvention.isRegisterArgument(argumentIndex)) {
            throw new IllegalArgumentException("register argument has no outgoing stack slot");
        }
        return "DWORD PTR [rsp+" + WindowsX64CallingConvention.outgoingStackArgumentOffset(argumentIndex) + "]";
    }

    private static int collectOutgoingArgumentAreaSize(IrFunction function) {
        int maxArgumentCount = 0;
        for (IrInstruction instruction : function.blocks().getFirst().instructions()) {
            if (instruction instanceof IrCallInstruction call) {
                maxArgumentCount = Math.max(maxArgumentCount, call.arguments().size());
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
        } else if (instruction instanceof IrCallInstruction call) {
            nextOffset = ensureTemporary(call.result(), temporaryOffsets, nextOffset);
        } else if (instruction instanceof IrStoreLocalInstruction storeLocal) {
            nextOffset = ensureLocal(storeLocal.local(), localOffsets, localInitializedOffsets, nextOffset);
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
            nextOffset += 4;
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
            nextOffset += 4;
            temporaryOffsets.put(temporary.name(), nextOffset);
        }
        return nextOffset;
    }

    private String stackSlot(Integer offset) {
        if (offset == null) {
            throw new IllegalArgumentException("missing stack slot");
        }
        return "DWORD PTR [rbp-" + (outgoingArgumentAreaSize + offset) + "]";
    }
}
