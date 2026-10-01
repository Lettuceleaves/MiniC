package minic.compiler.asm;

import minic.compiler.ir.value.IrValue.IrConstant;
import minic.compiler.ir.value.IrValue.IrFloatConstant;
import minic.compiler.ir.value.IrValue.IrFunctionAddress;
import minic.compiler.ir.value.IrValue.IrGlobalAddress;
import minic.compiler.ir.value.IrValue.IrParameterRef;
import minic.compiler.ir.value.IrValue.IrParameterAddress;
import minic.compiler.ir.value.IrValue.IrStringLiteral;
import minic.compiler.ir.value.IrValue.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.model.IrType;

final class ValueEmitter {
    private final FrameLayout frame;
    private final java.util.Set<String> externalFunctionNames;
    private final TemporaryLocations locations;

    ValueEmitter(FrameLayout frame, java.util.Set<String> externalFunctionNames) {
        this(frame, externalFunctionNames, TemporaryLocations.allStack(frame));
    }

    ValueEmitter(FrameLayout frame, java.util.Set<String> externalFunctionNames, TemporaryLocations locations) {
        this.frame = frame;
        this.externalFunctionNames = java.util.Set.copyOf(externalFunctionNames);
        this.locations = java.util.Objects.requireNonNull(locations, "locations");
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
            ValueLocation location = locations.location(temporary);
            if (location instanceof ValueLocation.StackSlot slot) {
                emitLoadStackSlot(builder, register, temporary.type(), slot.operand());
            } else {
                emitLoadRegister(builder, register, (ValueLocation.Register) location);
            }
            return;
        }
        if (value instanceof IrParameterRef parameterRef) {
            emitLoadStackSlot(builder, register, parameterRef.type(), frame.parameterSlot(parameterRef.name()));
            return;
        }
        if (value instanceof IrParameterAddress parameterAddress) {
            builder.append("    lea ").append(pointerRegister(register)).append(", ")
                    .append(frame.parameterAddress(parameterAddress.name())).append(System.lineSeparator());
            return;
        }
        if (value instanceof IrStringLiteral stringLiteral) {
            builder.append("    lea ").append(pointerRegister(register)).append(", ")
                    .append(stringLiteral.label()).append(System.lineSeparator());
            return;
        }
        if (value instanceof IrFunctionAddress functionAddress) {
            builder.append("    lea ").append(pointerRegister(register)).append(", ")
                    .append(CallingConvention.callSymbol(
                            functionAddress.functionName(),
                            externalFunctionNames.contains(functionAddress.functionName())
                    ))
                    .append(System.lineSeparator());
            return;
        }
        if (value instanceof IrGlobalAddress globalAddress) {
            builder.append("    lea ").append(pointerRegister(register)).append(", ")
                    .append(globalAddress.globalName()).append(System.lineSeparator());
            return;
        }
        throw new IllegalArgumentException("unsupported IR value: " + value.getClass().getSimpleName());
    }

    void emitStoreTemporary(StringBuilder builder, IrTemporary temporary, String register) {
        ValueLocation location = locations.location(temporary);
        IrType type = temporary.type();
        String source = registerForType(register, type);
        if (location instanceof ValueLocation.Register && location.operand().equals(source)) return;
        builder.append(type == IrType.FLOAT ? "    movss " : type == IrType.DOUBLE ? "    movsd " : "    mov ")
                .append(location.operand()).append(", ").append(source).append(System.lineSeparator());
    }

    private void emitLoadRegister(StringBuilder builder, String register, ValueLocation.Register location) {
        IrType type = location.type();
        if (type.isIntegerScalar() && type.sizeBytes() < Integer.BYTES && !isNarrowRegister(register, type.sizeBytes())) {
            builder.append(type.isSignedInteger() ? "    movsx " : "    movzx ")
                    .append(intRegister(register)).append(", ").append(location.operand()).append(System.lineSeparator());
            return;
        }
        String destination = registerForType(register, type);
        // A 32-bit load also clears the high half of the GPR, as a stack load did.
        if (destination.equals(location.operand()) && !(type.isIntegerScalar() && type.sizeBytes() == Integer.BYTES)) return;
        builder.append(type == IrType.FLOAT ? "    movss " : type == IrType.DOUBLE ? "    movsd " : "    mov ")
                .append(destination).append(", ").append(location.operand()).append(System.lineSeparator());
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
        if (type.isIntegerScalar() && type.sizeBytes() < Integer.BYTES
                && !isNarrowRegister(register, type.sizeBytes())) {
            builder.append(type.isSignedInteger() ? "    movsx " : "    movzx ")
                    .append(intRegister(register)).append(", ").append(slot)
                    .append(System.lineSeparator());
            return;
        }
        builder.append("    mov ").append(registerForType(register, type)).append(", ")
                .append(slot).append(System.lineSeparator());
    }

    private String pointerRegister(String register) {
        return ValueLocation.generalRegisterName(register, Long.BYTES);
    }

    private String constantRegister(String register, IrType type) {
        if ((type == IrType.BOOL || type == IrType.CHAR) && !isByteRegister(register)) {
            return intRegister(register);
        }
        return registerForType(register, type);
    }

    private String registerForType(String register, IrType type) {
        if (type.isFloatingScalar()) return floatRegister(register);
        if (type == IrType.POINTER || type.sizeBytes() == 8) return pointerRegister(register);
        if (type.sizeBytes() == 1) return byteRegister(register);
        if (type.sizeBytes() == 2) return wordRegister(register);
        return intRegister(register);
    }

    private String intRegister(String register) {
        return ValueLocation.generalRegisterName(register, Integer.BYTES);
    }

    private String byteRegister(String register) {
        return ValueLocation.generalRegisterName(register, Byte.BYTES);
    }

    private String wordRegister(String register) {
        return ValueLocation.generalRegisterName(register, Short.BYTES);
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
        return switch (type.sizeBytes()) {
            case 1 -> "BYTE PTR";
            case 2 -> "WORD PTR";
            case 4 -> "DWORD PTR";
            case 8 -> "QWORD PTR";
            default -> throw new IllegalArgumentException("unsupported IR type size: " + type);
        };
    }

    String storeRegister(String preferredRegister, IrType type) {
        return registerForType(preferredRegister, type);
    }

    String arithmeticRegister(String preferredRegister, IrType type) {
        if (type.isFloatingScalar()) {
            return floatRegister(preferredRegister);
        }
        if (type == IrType.POINTER || type.sizeBytes() == 8) {
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
        return ValueLocation.REGISTER_ALIASES.containsKey(register) && byteRegister(register).equals(register);
    }

    private boolean isWordRegister(String register) {
        return ValueLocation.REGISTER_ALIASES.containsKey(register) && wordRegister(register).equals(register);
    }

    private boolean isNarrowRegister(String register, int sizeBytes) {
        return sizeBytes == 1 ? isByteRegister(register) : sizeBytes == 2 && isWordRegister(register);
    }
}
