package minic.compiler.obj;

import minic.compiler.obj.machine.ImmediateOperand;
import minic.compiler.obj.machine.MachineInstruction;
import minic.compiler.obj.machine.MachineSection;
import minic.compiler.obj.machine.MachineSectionKind;
import minic.compiler.obj.machine.MemoryOperand;
import minic.compiler.obj.machine.RegisterOperand;
import minic.compiler.obj.x64.X64Encoder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class X64IntegerEncodingTest {
    @Test
    void encodesSixteenBitMovesExtensionsAndArithmetic() {
        MachineSection section = code(
                instruction("mov", register("ax"), new ImmediateOperand(0x1234)),
                instruction("mov", register("r8w"), new ImmediateOperand(0x5678)),
                instruction("mov", MemoryOperand.base(16, "rbp", -2), register("cx")),
                instruction("movzx", register("eax"), MemoryOperand.base(16, "rbp", -2)),
                instruction("movsx", register("rax"), MemoryOperand.base(16, "rbp", -2)),
                instruction("add", register("ax"), register("cx")),
                instruction("imul", register("ax"), register("cx"))
        );

        assertArrayEquals(bytes(
                0x66, 0xB8, 0x34, 0x12,
                0x66, 0x41, 0xB8, 0x78, 0x56,
                0x66, 0x89, 0x4D, 0xFE,
                0x0F, 0xB7, 0x45, 0xFE,
                0x48, 0x0F, 0xBF, 0x45, 0xFE,
                0x66, 0x01, 0xC8,
                0x66, 0x0F, 0xAF, 0xC1
        ), new X64Encoder().encode(section).bytes());
    }

    @Test
    void encodesUnsignedDivisionAndLogicalRightShift() {
        MachineSection section = code(
                instruction("div", register("rcx")),
                instruction("shr", register("rax"), register("cl")),
                instruction("div", register("ecx")),
                instruction("shr", register("eax"), register("cl")),
                instruction("div", register("cx")),
                instruction("shr", register("ax"), register("cl"))
        );

        assertArrayEquals(bytes(
                0x48, 0xF7, 0xF1,
                0x48, 0xD3, 0xE8,
                0xF7, 0xF1,
                0xD3, 0xE8,
                0x66, 0xF7, 0xF1,
                0x66, 0xD3, 0xE8
        ), new X64Encoder().encode(section).bytes());
    }

    private static MachineSection code(MachineInstruction... instructions) {
        return new MachineSection(".text", MachineSectionKind.CODE, 16, List.of(instructions));
    }

    private static MachineInstruction instruction(String mnemonic, minic.compiler.obj.machine.MachineOperand... operands) {
        return new MachineInstruction(mnemonic, operands);
    }

    private static RegisterOperand register(String name) {
        return new RegisterOperand(name);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
