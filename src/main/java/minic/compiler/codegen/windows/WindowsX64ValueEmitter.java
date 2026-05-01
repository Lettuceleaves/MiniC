package minic.compiler.codegen.windows;

import minic.compiler.ir.value.IrConstant;
import minic.compiler.ir.value.IrFloatConstant;
import minic.compiler.ir.value.IrFunctionAddress;
import minic.compiler.ir.value.IrParameterRef;
import minic.compiler.ir.value.IrStringLiteral;
import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.model.IrType;

final class WindowsX64ValueEmitter {
    private final WindowsX64FrameLayout frame;
    private final java.util.Set<String> externalFunctionNames;

    WindowsX64ValueEmitter(WindowsX64FrameLayout frame, java.util.Set<String> externalFunctionNames) {
        this.frame = frame;
        this.externalFunctionNames = java.util.Set.copyOf(externalFunctionNames);
    }

    void emitLoadValue(StringBuilder builder, IrValue value, String register) {
        if (value instanceof IrConstant constant) {
            builder.append("    mov ").append(constantRegister(register, constant.type()))
                    .append(", ").append(constant.value()).append(System.lineSeparator());
            return;
        }
        if (value instanceof IrFloatConstant constant) {
            emitLoadFloatConstant(builder, constant, register);
            return;
        }
        if (value instanceof IrTemporary temporary) {
            emitLoadStackSlot(builder, register, temporary.type(), frame.temporarySlot(temporary));
            return;
        }
        if (value instanceof IrParameterRef parameterRef) {
            emitLoadStackSlot(builder, register, parameterRef.type(), frame.parameterSlot(parameterRef.name()));
            return;
        }
        if (value instanceof IrStringLiteral stringLiteral) {
            builder.append("    lea ").append(pointerRegister(register)).append(", ")
                    .append(stringLiteral.label()).append(System.lineSeparator());
            return;
        }
        if (value instanceof IrFunctionAddress functionAddress) {
            builder.append("    lea ").append(pointerRegister(register)).append(", ")
                    .append(WindowsX64CallingConvention.callSymbol(
                            functionAddress.functionName(),
                            externalFunctionNames.contains(functionAddress.functionName())
                    ))
                    .append(System.lineSeparator());
            return;
        }
        throw new IllegalArgumentException("unsupported IR value: " + value.getClass().getSimpleName());
    }

    private void emitLoadFloatConstant(StringBuilder builder, IrFloatConstant constant, String register) {
        if (constant.type() == IrType.FLOAT) {
            int bits = Float.floatToRawIntBits((float) constant.value());
            builder.append("    mov eax, ").append(Integer.toUnsignedString(bits)).append(System.lineSeparator());
            builder.append("    movd ").append(floatRegister(register)).append(", eax").append(System.lineSeparator());
            return;
        }
        long bits = Double.doubleToRawLongBits(constant.value());
        builder.append("    mov rax, ").append(Long.toUnsignedString(bits)).append(System.lineSeparator());
        builder.append("    movq ").append(floatRegister(register)).append(", rax").append(System.lineSeparator());
    }

    private void emitLoadStackSlot(StringBuilder builder, String register, IrType type, String slot) {
        if (type == IrType.FLOAT) {
            builder.append("    movss ").append(floatRegister(register)).append(", ").append(slot)
                    .append(System.lineSeparator());
            return;
        }
        if (type == IrType.DOUBLE) {
            builder.append("    movsd ").append(floatRegister(register)).append(", ").append(slot)
                    .append(System.lineSeparator());
            return;
        }
        if (type == IrType.BOOL && !isByteRegister(register)) {
            builder.append("    movzx ").append(intRegister(register)).append(", ").append(slot)
                    .append(System.lineSeparator());
            return;
        }
        if (type == IrType.CHAR && !isByteRegister(register)) {
            builder.append("    movsx ").append(intRegister(register)).append(", ").append(slot)
                    .append(System.lineSeparator());
            return;
        }
        builder.append("    mov ").append(registerForType(register, type)).append(", ")
                .append(slot).append(System.lineSeparator());
    }

    private String pointerRegister(String register) {
        return switch (register) {
            case "eax" -> "rax";
            case "ecx" -> "rcx";
            case "edx" -> "rdx";
            case "r8d" -> "r8";
            case "r9d" -> "r9";
            case "al" -> "rax";
            case "cl" -> "rcx";
            case "dl" -> "rdx";
            case "r8b" -> "r8";
            case "r9b" -> "r9";
            default -> register;
        };
    }

    private String constantRegister(String register, IrType type) {
        if ((type == IrType.BOOL || type == IrType.CHAR) && !isByteRegister(register)) {
            return intRegister(register);
        }
        return registerForType(register, type);
    }

    private String registerForType(String register, IrType type) {
        return switch (type) {
            case BOOL, CHAR -> byteRegister(register);
            case LONG, POINTER -> pointerRegister(register);
            case FLOAT, DOUBLE -> floatRegister(register);
            case INT, INT_ARRAY, STRUCT -> intRegister(register);
        };
    }

    private String intRegister(String register) {
        return switch (register) {
            case "rax" -> "eax";
            case "rcx" -> "ecx";
            case "rdx" -> "edx";
            case "r8" -> "r8d";
            case "r9" -> "r9d";
            default -> register;
        };
    }

    private String byteRegister(String register) {
        return switch (pointerRegister(register)) {
            case "rax" -> "al";
            case "rcx" -> "cl";
            case "rdx" -> "dl";
            case "r8" -> "r8b";
            case "r9" -> "r9b";
            default -> register;
        };
    }

    String loadRegister(String preferredRegister, IrType type) {
        return registerForType(preferredRegister, type);
    }

    String pointerRegisterName(String register) {
        return pointerRegister(register);
    }

    String intRegisterName(String register) {
        return intRegister(register);
    }

    String byteRegisterName(String register) {
        return byteRegister(register);
    }

    String floatRegisterName(String register) {
        return floatRegister(register);
    }

    String memoryPrefix(IrType type) {
        return switch (type) {
            case BOOL, CHAR -> "BYTE PTR";
            case LONG, POINTER, DOUBLE -> "QWORD PTR";
            case INT, INT_ARRAY, STRUCT, FLOAT -> "DWORD PTR";
        };
    }

    String storeRegister(String preferredRegister, IrType type) {
        return registerForType(preferredRegister, type);
    }

    String arithmeticRegister(String preferredRegister, IrType type) {
        if (type.isFloatingScalar()) {
            return floatRegister(preferredRegister);
        }
        if (type == IrType.LONG) {
            return pointerRegister(preferredRegister);
        }
        return intRegister(preferredRegister);
    }

    private String floatRegister(String register) {
        return switch (register) {
            case "rax", "eax", "al", "xmm0" -> "xmm0";
            case "rcx", "ecx", "cl", "xmm1" -> "xmm1";
            case "rdx", "edx", "dl", "xmm2" -> "xmm2";
            case "r8", "r8d", "r8b", "xmm3" -> "xmm3";
            case "r9", "r9d", "r9b", "xmm4" -> "xmm4";
            default -> register;
        };
    }

    private boolean isByteRegister(String register) {
        return switch (register) {
            case "al", "cl", "dl", "r8b", "r9b" -> true;
            default -> false;
        };
    }
}
