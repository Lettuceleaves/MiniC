package craken.compiler.asm;

import craken.compiler.ir.model.IrType;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** A typed home for one temporary value; allocation and clobber handling belong to its caller. */
sealed interface ValueLocation {
    IrType type();
    String operand();

    /** Positive ending offset below rbp, using the same convention as FrameLayout. */
    record StackSlot(IrType type, int offset) implements ValueLocation {
        public StackSlot {
            Objects.requireNonNull(type, "type");
            if (offset < type.sizeBytes()) throw new IllegalArgumentException("stack slot is too small: " + offset);
        }
        @Override public String operand() {
            return switch (type.sizeBytes()) {
                case 1 -> "BYTE PTR";
                case 2 -> "WORD PTR";
                case 4 -> "DWORD PTR";
                case 8 -> "QWORD PTR";
                default -> throw new IllegalArgumentException("unsupported value type: " + type);
            } + " [rbp-" + offset + "]";
        }
    }

    /** A canonical full GPR name or xmm0..xmm15; the operand width follows the value, not its slot. */
    record Register(IrType type, String name) implements ValueLocation {
        public Register {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(name, "name");
            boolean valid = type.isFloatingScalar() ? name.matches("xmm(?:[0-9]|1[0-5])")
                    : GENERAL_REGISTERS.containsKey(name);
            if (!valid) throw new IllegalArgumentException("register " + name + " cannot hold " + type);
        }
        @Override public String operand() {
            return type.isFloatingScalar() ? name : generalRegisterName(name, type.sizeBytes());
        }
    }

    // Scratch-register aliases and allocated-register aliases share one width table.
    private static Map<String, java.util.List<String>> registers() {
        String[] full = {"rax", "rcx", "rdx", "rbx", "rsi", "rdi", "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15"};
        String[] ints = {"eax", "ecx", "edx", "ebx", "esi", "edi", "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d"};
        String[] words = {"ax", "cx", "dx", "bx", "si", "di", "r8w", "r9w", "r10w", "r11w", "r12w", "r13w", "r14w", "r15w"};
        String[] bytes = {"al", "cl", "dl", "bl", "sil", "dil", "r8b", "r9b", "r10b", "r11b", "r12b", "r13b", "r14b", "r15b"};
        return IntStream.range(0, full.length).boxed().collect(Collectors.toUnmodifiableMap(
                index -> full[index], index -> java.util.List.of(bytes[index], words[index], ints[index], full[index])));
    }
    Map<String, java.util.List<String>> GENERAL_REGISTERS = registers();
    Map<String, java.util.List<String>> REGISTER_ALIASES = GENERAL_REGISTERS.values().stream()
            .flatMap(names -> names.stream().map(name -> Map.entry(name, names)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    static String generalRegisterName(String register, int sizeBytes) {
        var names = REGISTER_ALIASES.get(register);
        if (names == null) return register;
        return names.get(switch (sizeBytes) {
            case 1 -> 0;
            case 2 -> 1;
            case 4 -> 2;
            case 8 -> 3;
            default -> throw new IllegalArgumentException("unsupported register width: " + sizeBytes);
        });
    }
}
