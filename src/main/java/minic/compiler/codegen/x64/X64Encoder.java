package minic.compiler.codegen.x64;

import minic.compiler.codegen.machine.ImmediateOperand;
import minic.compiler.codegen.machine.MachineData;
import minic.compiler.codegen.machine.MachineInstruction;
import minic.compiler.codegen.machine.MachineItem;
import minic.compiler.codegen.machine.MachineLabel;
import minic.compiler.codegen.machine.MachineOperand;
import minic.compiler.codegen.machine.MachineSection;
import minic.compiler.codegen.machine.RegisterOperand;
import minic.compiler.codegen.machine.SymbolOperand;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * MiniC 所需 x86-64 指令的内置二进制编码器。
 */
public final class X64Encoder {
    /**
     * 编码一个逻辑 section。section 内标签会直接解析，外部符号保留为 relocation。
     *
     * @param section 结构化机器 section
     * @return 编码结果
     */
    public EncodedMachineSection encode(MachineSection section) {
        Objects.requireNonNull(section, "section");
        LinkedHashMap<String, Integer> symbols = layout(section);
        ByteWriter writer = new ByteWriter();
        ArrayList<MachineRelocation> relocations = new ArrayList<>();
        for (MachineItem item : section.items()) {
            switch (item) {
                case MachineLabel ignored -> {
                    // 标签只影响布局。
                }
                case MachineData data -> {
                    writer.align(data.alignment());
                    writer.bytes(data.bytes());
                }
                case MachineInstruction instruction -> encodeInstruction(writer, instruction, symbols, relocations);
            }
        }
        return new EncodedMachineSection(section.name(), writer.toByteArray(), symbols, relocations);
    }

    private LinkedHashMap<String, Integer> layout(MachineSection section) {
        LinkedHashMap<String, Integer> symbols = new LinkedHashMap<>();
        int offset = 0;
        for (MachineItem item : section.items()) {
            switch (item) {
                case MachineLabel label -> {
                    if (symbols.putIfAbsent(label.name(), offset) != null) {
                        throw new IllegalArgumentException("duplicate label: " + label.name());
                    }
                }
                case MachineData data -> offset = align(offset, data.alignment()) + data.bytes().length;
                case MachineInstruction instruction -> offset += instructionSize(instruction);
            }
        }
        return symbols;
    }

    private int instructionSize(MachineInstruction instruction) {
        ByteWriter writer = new ByteWriter();
        encodeInstruction(writer, instruction, Map.of(), new ArrayList<>());
        return writer.size();
    }

    private void encodeInstruction(
            ByteWriter writer,
            MachineInstruction instruction,
            Map<String, Integer> symbols,
            List<MachineRelocation> relocations
    ) {
        String mnemonic = instruction.mnemonic().toLowerCase(Locale.ROOT);
        List<MachineOperand> operands = instruction.operands();
        switch (mnemonic) {
            case "ret" -> {
                requireOperandCount(instruction, 0);
                writer.u8(0xC3);
            }
            case "nop" -> {
                requireOperandCount(instruction, 0);
                writer.u8(0x90);
            }
            case "push" -> encodePushPop(writer, instruction, 0x50);
            case "pop" -> encodePushPop(writer, instruction, 0x58);
            case "mov" -> encodeMov(writer, instruction);
            case "add" -> encodeBinaryRegister(writer, instruction, 0x01);
            case "sub" -> encodeSub(writer, instruction);
            case "xor" -> encodeBinaryRegister(writer, instruction, 0x31);
            case "call" -> encodeCall(writer, instruction, symbols, relocations);
            case "jmp" -> encodeRelativeBranch(writer, instruction, 0xE9, null, symbols, relocations);
            case "je" -> encodeRelativeBranch(writer, instruction, 0x0F, 0x84, symbols, relocations);
            case "jne" -> encodeRelativeBranch(writer, instruction, 0x0F, 0x85, symbols, relocations);
            default -> throw new UnsupportedOperationException("unsupported x64 instruction: " + instruction);
        }
    }

    private void encodeMov(ByteWriter writer, MachineInstruction instruction) {
        requireOperandCount(instruction, 2);
        MachineOperand destination = instruction.operands().get(0);
        MachineOperand source = instruction.operands().get(1);
        if (destination instanceof RegisterOperand register && source instanceof ImmediateOperand immediate) {
            Register target = Register.parse(register.name());
            if (target.width == 64) {
                emitRex(writer, true, false, false, target.extended());
                writer.u8(0xB8 + target.lowCode());
                writer.i64(immediate.value());
                return;
            }
            if (target.width == 32) {
                emitRex(writer, false, false, false, target.extended());
                writer.u8(0xB8 + target.lowCode());
                writer.i32((int) immediate.value());
                return;
            }
        }
        if (destination instanceof RegisterOperand destinationRegister && source instanceof RegisterOperand sourceRegister) {
            Register target = Register.parse(destinationRegister.name());
            Register value = Register.parse(sourceRegister.name());
            requireSameWidth(target, value, instruction);
            emitRex(writer, target.width == 64, value.extended(), false, target.extended());
            writer.u8(0x89);
            writer.u8(modRm(3, value.lowCode(), target.lowCode()));
            return;
        }
        throw new UnsupportedOperationException("unsupported mov operands: " + instruction.operands());
    }

    private void encodeBinaryRegister(ByteWriter writer, MachineInstruction instruction, int opcode) {
        requireOperandCount(instruction, 2);
        Register target = register(instruction.operands().get(0));
        Register source = register(instruction.operands().get(1));
        requireSameWidth(target, source, instruction);
        emitRex(writer, target.width == 64, source.extended(), false, target.extended());
        writer.u8(opcode);
        writer.u8(modRm(3, source.lowCode(), target.lowCode()));
    }

    private void encodeSub(ByteWriter writer, MachineInstruction instruction) {
        requireOperandCount(instruction, 2);
        Register target = register(instruction.operands().get(0));
        MachineOperand source = instruction.operands().get(1);
        if (source instanceof RegisterOperand) {
            encodeBinaryRegister(writer, instruction, 0x29);
            return;
        }
        if (source instanceof ImmediateOperand immediate) {
            emitRex(writer, target.width == 64, false, false, target.extended());
            if (immediate.value() >= -128 && immediate.value() <= 127) {
                writer.u8(0x83);
                writer.u8(modRm(3, 5, target.lowCode()));
                writer.u8((int) immediate.value());
            } else {
                writer.u8(0x81);
                writer.u8(modRm(3, 5, target.lowCode()));
                writer.i32(Math.toIntExact(immediate.value()));
            }
            return;
        }
        throw new UnsupportedOperationException("unsupported sub operands: " + instruction.operands());
    }

    private void encodePushPop(ByteWriter writer, MachineInstruction instruction, int opcode) {
        requireOperandCount(instruction, 1);
        Register register = register(instruction.operands().getFirst());
        if (register.width != 64) {
            throw new IllegalArgumentException(instruction.mnemonic() + " requires a 64-bit register");
        }
        emitRex(writer, false, false, false, register.extended());
        writer.u8(opcode + register.lowCode());
    }

    private void encodeCall(
            ByteWriter writer,
            MachineInstruction instruction,
            Map<String, Integer> symbols,
            List<MachineRelocation> relocations
    ) {
        requireOperandCount(instruction, 1);
        MachineOperand operand = instruction.operands().getFirst();
        if (operand instanceof RegisterOperand) {
            Register target = register(operand);
            emitRex(writer, true, false, false, target.extended());
            writer.u8(0xFF);
            writer.u8(modRm(3, 2, target.lowCode()));
            return;
        }
        encodeRelativeBranch(writer, instruction, 0xE8, null, symbols, relocations);
    }

    private void encodeRelativeBranch(
            ByteWriter writer,
            MachineInstruction instruction,
            int firstOpcode,
            Integer secondOpcode,
            Map<String, Integer> symbols,
            List<MachineRelocation> relocations
    ) {
        requireOperandCount(instruction, 1);
        if (!(instruction.operands().getFirst() instanceof SymbolOperand symbol)) {
            throw new UnsupportedOperationException("branch target must be a symbol: " + instruction.operands());
        }
        writer.u8(firstOpcode);
        if (secondOpcode != null) {
            writer.u8(secondOpcode);
        }
        int displacementOffset = writer.size();
        Integer targetOffset = symbols.get(symbol.symbol());
        if (targetOffset == null) {
            writer.i32(0);
            relocations.add(new MachineRelocation(displacementOffset, symbol.symbol(), MachineRelocationKind.REL32, 0));
            return;
        }
        int nextInstruction = displacementOffset + Integer.BYTES;
        writer.i32(targetOffset - nextInstruction);
    }

    private static Register register(MachineOperand operand) {
        if (!(operand instanceof RegisterOperand register)) {
            throw new UnsupportedOperationException("register operand required: " + operand);
        }
        return Register.parse(register.name());
    }

    private static void requireSameWidth(Register left, Register right, MachineInstruction instruction) {
        if (left.width != right.width) {
            throw new IllegalArgumentException("register width mismatch in " + instruction);
        }
    }

    private static void requireOperandCount(MachineInstruction instruction, int count) {
        if (instruction.operands().size() != count) {
            throw new IllegalArgumentException(instruction.mnemonic() + " requires " + count + " operands");
        }
    }

    private static void emitRex(ByteWriter writer, boolean w, boolean r, boolean x, boolean b) {
        int rex = 0x40 | (w ? 8 : 0) | (r ? 4 : 0) | (x ? 2 : 0) | (b ? 1 : 0);
        if (rex != 0x40) {
            writer.u8(rex);
        }
    }

    private static int modRm(int mod, int reg, int rm) {
        return (mod << 6) | ((reg & 7) << 3) | (rm & 7);
    }

    private static int align(int value, int alignment) {
        return (value + alignment - 1) & -alignment;
    }

    private record Register(int code, int width) {
        private static final Map<String, Register> REGISTERS = createRegisters();

        private static Register parse(String name) {
            Register register = REGISTERS.get(name.toLowerCase(Locale.ROOT));
            if (register == null) {
                throw new IllegalArgumentException("unsupported x64 register: " + name);
            }
            return register;
        }

        private int lowCode() {
            return code & 7;
        }

        private boolean extended() {
            return code >= 8;
        }

        private static Map<String, Register> createRegisters() {
            String[] names64 = {"rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi",
                    "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15"};
            String[] names32 = {"eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi",
                    "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d"};
            LinkedHashMap<String, Register> registers = new LinkedHashMap<>();
            for (int index = 0; index < names64.length; index++) {
                registers.put(names64[index], new Register(index, 64));
                registers.put(names32[index], new Register(index, 32));
            }
            return Map.copyOf(registers);
        }
    }

    private static final class ByteWriter {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private int size() {
            return output.size();
        }

        private void align(int alignment) {
            while ((size() & (alignment - 1)) != 0) {
                u8(0);
            }
        }

        private void u8(int value) {
            output.write(value & 0xFF);
        }

        private void i32(int value) {
            u8(value);
            u8(value >>> 8);
            u8(value >>> 16);
            u8(value >>> 24);
        }

        private void i64(long value) {
            i32((int) value);
            i32((int) (value >>> 32));
        }

        private void bytes(byte[] bytes) {
            output.writeBytes(bytes);
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}
