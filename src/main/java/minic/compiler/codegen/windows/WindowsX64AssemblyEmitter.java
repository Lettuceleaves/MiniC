package minic.compiler.codegen.windows;

import minic.compiler.codegen.AssemblyEmitter;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.target.TargetPlatform;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.IrReturnInstruction;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrValue;

import java.util.Objects;

/**
 * Windows x86_64 MASM 风格汇编 emitter。
 *
 * <p>A053 阶段只支持最小 return 常量函数，用于建立目标平台和汇编文本产物结构。
 * 完整局部变量、调用和运行时检查代码生成会在 A060 补齐。</p>
 */
public final class WindowsX64AssemblyEmitter implements AssemblyEmitter {
    private static final String ENTRY_SYMBOL = "main";

    @Override
    public AssemblySource emit(IrModule module) {
        Objects.requireNonNull(module, "module");
        StringBuilder builder = new StringBuilder();
        builder.append("; target: ").append(TargetPlatform.WINDOWS_X86_64.id()).append(System.lineSeparator());
        builder.append("PUBLIC ").append(ENTRY_SYMBOL).append(System.lineSeparator());
        builder.append(".code").append(System.lineSeparator());
        for (IrFunction function : module.functions()) {
            emitFunction(builder, function);
        }
        builder.append("END").append(System.lineSeparator());
        return new AssemblySource(TargetPlatform.WINDOWS_X86_64, ENTRY_SYMBOL, builder.toString());
    }

    private void emitFunction(StringBuilder builder, IrFunction function) {
        builder.append(function.name()).append(" PROC").append(System.lineSeparator());
        IrReturnInstruction returnInstruction = findSingleReturn(function);
        emitReturn(builder, returnInstruction.value());
        builder.append(function.name()).append(" ENDP").append(System.lineSeparator());
    }

    private IrReturnInstruction findSingleReturn(IrFunction function) {
        if (function.blocks().size() != 1) {
            throw new IllegalArgumentException("A053 emitter only supports one basic block per function");
        }
        return function.blocks().getFirst().instructions().stream()
                .filter(IrReturnInstruction.class::isInstance)
                .map(IrReturnInstruction.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("function must contain return: " + function.name()));
    }

    private void emitReturn(StringBuilder builder, IrValue value) {
        if (!(value instanceof IrConstant constant)) {
            throw new IllegalArgumentException("A053 emitter only supports returning integer constants");
        }
        builder.append("    mov eax, ").append(constant.value()).append(System.lineSeparator());
        builder.append("    ret").append(System.lineSeparator());
    }
}
