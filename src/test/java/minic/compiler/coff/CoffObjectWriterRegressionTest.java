package minic.compiler.coff;

import minic.compiler.codegen.machine.MachineInstruction;
import minic.compiler.codegen.machine.MachineLabel;
import minic.compiler.codegen.machine.MachineModule;
import minic.compiler.codegen.machine.MachineSection;
import minic.compiler.codegen.machine.MachineSectionKind;
import minic.compiler.codegen.machine.SymbolOperand;
import minic.compiler.codegen.target.TargetPlatform;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoffObjectWriterRegressionTest {
    @Test
    void writesAmd64SectionsSymbolsStringTableAndRelocations() {
        MachineSection text = new MachineSection(
                ".text",
                MachineSectionKind.CODE,
                16,
                List.of(
                        new MachineLabel("main", true),
                        new MachineInstruction("call", new SymbolOperand("runtime_print")),
                        new MachineInstruction("ret")
                )
        );
        MachineModule module = new MachineModule(
                TargetPlatform.WINDOWS_X86_64,
                "main",
                List.of(text),
                List.of("runtime_print")
        );

        byte[] object = new CoffObjectWriter().write(module).bytes();
        ByteBuffer buffer = ByteBuffer.wrap(object).order(ByteOrder.LITTLE_ENDIAN);
        int symbolTablePointer = buffer.getInt(8);
        int symbolCount = buffer.getInt(12);
        int rawPointer = buffer.getInt(20 + 20);
        int relocationPointer = buffer.getInt(20 + 24);

        assertThat(Short.toUnsignedInt(buffer.getShort(0))).isEqualTo(0x8664);
        assertThat(Short.toUnsignedInt(buffer.getShort(2))).isEqualTo(1);
        assertThat(symbolCount).isEqualTo(2);
        assertThat(buffer.get(rawPointer) & 0xFF).isEqualTo(0xE8);
        assertThat(buffer.getInt(relocationPointer)).isEqualTo(1);
        assertThat(buffer.getInt(relocationPointer + 4)).isEqualTo(1);
        assertThat(Short.toUnsignedInt(buffer.getShort(relocationPointer + 8))).isEqualTo(0x0004);
        assertThat(buffer.getInt(symbolTablePointer + symbolCount * 18)).isGreaterThan(4);
    }
}
