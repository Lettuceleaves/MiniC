package minic.compiler.pe;

import minic.compiler.coff.CoffObjectFile;
import minic.compiler.coff.CoffObjectWriter;
import minic.compiler.codegen.machine.ImmediateOperand;
import minic.compiler.codegen.machine.MachineInstruction;
import minic.compiler.codegen.machine.MachineLabel;
import minic.compiler.codegen.machine.MachineModule;
import minic.compiler.codegen.machine.MachineSection;
import minic.compiler.codegen.machine.MachineSectionKind;
import minic.compiler.codegen.machine.RegisterOperand;
import minic.compiler.codegen.machine.SymbolOperand;
import minic.compiler.codegen.target.TargetPlatform;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsPeLinkerRegressionTest {
    @Test
    void linksCoffIntoDeterministicConsolePe32PlusImage() {
        MachineSection text = new MachineSection(
                ".text",
                MachineSectionKind.CODE,
                16,
                List.of(
                        new MachineLabel("entry", true),
                        new MachineInstruction("mov", new RegisterOperand("eax"), new ImmediateOperand(7)),
                        new MachineInstruction("ret")
                )
        );
        MachineModule module = new MachineModule(
                TargetPlatform.WINDOWS_X86_64,
                "entry",
                List.of(text),
                List.of()
        );
        CoffObjectFile object = new CoffObjectWriter().write(module);

        byte[] image = new WindowsPeLinker().link(object, "entry").bytes();
        ByteBuffer buffer = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        int peOffset = buffer.getInt(0x3C);
        int optional = peOffset + 4 + 20;
        int sectionHeader = optional + 240;

        assertThat(image[0]).isEqualTo((byte) 'M');
        assertThat(image[1]).isEqualTo((byte) 'Z');
        assertThat(buffer.getInt(peOffset)).isEqualTo(0x00004550);
        assertThat(Short.toUnsignedInt(buffer.getShort(peOffset + 4))).isEqualTo(0x8664);
        assertThat(Short.toUnsignedInt(buffer.getShort(optional))).isEqualTo(0x020B);
        assertThat(buffer.getInt(optional + 16)).isEqualTo(0x1000);
        assertThat(buffer.getInt(sectionHeader + 12)).isEqualTo(0x1000);
        assertThat(buffer.getInt(sectionHeader + 20)).isEqualTo(0x200);
        assertThat(image[0x200] & 0xFF).isEqualTo(0xB8);
        assertThat(new WindowsPeLinker().link(object, "entry").bytes()).isEqualTo(image);

        MachineModule importedModule = new MachineModule(
                TargetPlatform.WINDOWS_X86_64,
                "entry",
                List.of(new MachineSection(
                        ".text",
                        MachineSectionKind.CODE,
                        16,
                        List.of(
                                new MachineLabel("entry", true),
                                new MachineInstruction("mov", new RegisterOperand("ecx"), new ImmediateOperand(0)),
                                new MachineInstruction("call", new SymbolOperand("ExitProcess")),
                                new MachineInstruction("ret")
                        )
                )),
                List.of("ExitProcess")
        );
        byte[] importedImage = new WindowsPeLinker().link(
                new CoffObjectWriter().write(importedModule),
                "entry",
                Map.of("ExitProcess", "KERNEL32.dll")
        ).bytes();
        ByteBuffer importedBuffer = ByteBuffer.wrap(importedImage).order(ByteOrder.LITTLE_ENDIAN);
        int importedOptional = importedBuffer.getInt(0x3C) + 4 + 20;
        assertThat(importedBuffer.getInt(importedOptional + 112 + 8)).isNotZero();
        assertThat(new String(importedImage, java.nio.charset.StandardCharsets.US_ASCII))
                .contains("ExitProcess", "KERNEL32.dll");
    }
}
