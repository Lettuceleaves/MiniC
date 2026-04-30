package minic.compiler.codegen.windows;

import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrParameterRef;
import minic.compiler.ir.value.IrStringLiteral;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.model.IrType;

final class WindowsX64ValueEmitter {
    private final WindowsX64FrameLayout frame;

    WindowsX64ValueEmitter(WindowsX64FrameLayout frame) {
        this.frame = frame;
    }

    void emitLoadValue(StringBuilder builder, IrValue value, String register) {
        if (value instanceof IrConstant constant) {
            builder.append("    mov ").append(register).append(", ").append(constant.value()).append(System.lineSeparator());
            return;
        }
        if (value instanceof IrTemporary temporary) {
            builder.append("    mov ").append(registerForType(register, temporary.type())).append(", ")
                    .append(frame.temporarySlot(temporary)).append(System.lineSeparator());
            return;
        }
        if (value instanceof IrParameterRef parameterRef) {
            builder.append("    mov ").append(registerForType(register, parameterRef.type())).append(", ")
                    .append(frame.parameterSlot(parameterRef.name())).append(System.lineSeparator());
            return;
        }
        if (value instanceof IrStringLiteral stringLiteral) {
            builder.append("    lea ").append(pointerRegister(register)).append(", ")
                    .append(stringLiteral.label()).append(System.lineSeparator());
            return;
        }
        throw new IllegalArgumentException("unsupported IR value: " + value.getClass().getSimpleName());
    }

    private String pointerRegister(String register) {
        return switch (register) {
            case "eax" -> "rax";
            case "ecx" -> "rcx";
            case "edx" -> "rdx";
            case "r8d" -> "r8";
            case "r9d" -> "r9";
            default -> register;
        };
    }

    private String registerForType(String register, IrType type) {
        if (type == IrType.POINTER) {
            return pointerRegister(register);
        }
        return register;
    }
}
