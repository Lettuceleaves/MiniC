package minic.compiler.codegen.windows;

import minic.compiler.codegen.AssemblyEmitter;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.target.TargetPlatform;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.model.IrStringData;

import java.util.Objects;

/**
 * Windows x86_64 MASM 风格汇编 emitter。
 */
public final class WindowsX64AssemblyEmitter implements AssemblyEmitter {
    @Override
    public AssemblySource emit(IrModule module) {
        Objects.requireNonNull(module, "module");
        StringBuilder builder = new StringBuilder();
        builder.append("; target: ").append(TargetPlatform.WINDOWS_X86_64.id()).append(System.lineSeparator());
        builder.append("PUBLIC ").append(WindowsX64CallingConvention.ENTRY_SYMBOL).append(System.lineSeparator());
        if (!module.stringData().isEmpty()) {
            builder.append(".const").append(System.lineSeparator());
            for (IrStringData stringData : module.stringData()) {
                emitStringData(builder, stringData);
            }
        }
        builder.append(".code").append(System.lineSeparator());
        for (IrFunction function : module.functions()) {
            emitFunction(builder, function);
        }
        builder.append("END").append(System.lineSeparator());
        return new AssemblySource(TargetPlatform.WINDOWS_X86_64, WindowsX64CallingConvention.ENTRY_SYMBOL, builder.toString());
    }

    private void emitFunction(StringBuilder builder, IrFunction function) {
        WindowsX64FrameLayout frame = WindowsX64FrameLayout.create(function);
        String functionSymbol = WindowsX64CallingConvention.symbolName(function.name());
        String epilogueLabel = functionSymbol + "$epilogue";
        WindowsX64InstructionEmitter instructionEmitter = new WindowsX64InstructionEmitter(frame);

        builder.append(functionSymbol).append(" PROC").append(System.lineSeparator());
        builder.append("    push rbp").append(System.lineSeparator());
        builder.append("    mov rbp, rsp").append(System.lineSeparator());
        if (frame.frameSize() > 0) {
            builder.append("    sub rsp, ").append(frame.frameSize()).append(System.lineSeparator());
        }
        instructionEmitter.emitParameterStores(builder, function);
        for (IrBlock block : function.blocks()) {
            if (!"entry".equals(block.label())) {
                builder.append(instructionEmitter.blockSymbol(functionSymbol, block.label()))
                        .append(":").append(System.lineSeparator());
            }
            for (IrInstruction instruction : block.instructions()) {
                instructionEmitter.emitInstruction(builder, functionSymbol, epilogueLabel, instruction);
            }
        }
        instructionEmitter.emitFunctionTrap(builder, functionSymbol, "uninitialized", 101, epilogueLabel);
        instructionEmitter.emitFunctionTrap(builder, functionSymbol, "divide_by_zero", 102, epilogueLabel);
        builder.append(epilogueLabel).append(":").append(System.lineSeparator());
        builder.append("    mov rsp, rbp").append(System.lineSeparator());
        builder.append("    pop rbp").append(System.lineSeparator());
        builder.append("    ret").append(System.lineSeparator());
        builder.append(functionSymbol).append(" ENDP").append(System.lineSeparator());
    }

    private void emitStringData(StringBuilder builder, IrStringData stringData) {
        builder.append(stringData.label()).append(" BYTE ");
        for (int index = 0; index < stringData.value().length(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append((int) stringData.value().charAt(index));
        }
        if (!stringData.value().isEmpty()) {
            builder.append(", ");
        }
        builder.append("0").append(System.lineSeparator());
    }
}
