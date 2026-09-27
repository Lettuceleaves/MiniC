package minic.compiler.codegen.x64;

import minic.compiler.codegen.machine.ImmediateOperand;
import minic.compiler.codegen.machine.MachineInstruction;
import minic.compiler.codegen.machine.MachineLabel;
import minic.compiler.codegen.machine.MemoryOperand;
import minic.compiler.codegen.machine.MachineSection;
import minic.compiler.codegen.machine.MachineSectionKind;
import minic.compiler.codegen.machine.RegisterOperand;
import minic.compiler.codegen.machine.SymbolOperand;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class X64EncoderRegressionTest {
    @Test
    void encodesRegistersImmediatesLocalBranchesAndExternalRelocations() {
        MachineSection section = new MachineSection(
                ".text",
                MachineSectionKind.CODE,
                16,
                List.of(
                        new MachineLabel("main", true),
                        new MachineInstruction("push", new RegisterOperand("rbp")),
                        new MachineInstruction("mov", new RegisterOperand("rbp"), new RegisterOperand("rsp")),
                        new MachineInstruction("sub", new RegisterOperand("rsp"), new ImmediateOperand(40)),
                        new MachineInstruction("mov", new RegisterOperand("eax"), new ImmediateOperand(42)),
                        new MachineInstruction("call", new SymbolOperand("runtime_print")),
                        new MachineInstruction("jne", new SymbolOperand("done")),
                        new MachineInstruction("xor", new RegisterOperand("r8d"), new RegisterOperand("r8d")),
                        new MachineLabel("done", false),
                        new MachineInstruction("pop", new RegisterOperand("rbp")),
                        new MachineInstruction("ret")
                )
        );

        EncodedMachineSection encoded = new X64Encoder().encode(section);

        assertThat(HexFormat.of().formatHex(encoded.bytes())).isEqualTo(
                "55488bec4883ec28b82a000000e8000000000f85030000004531c05dc3"
        );
        assertThat(encoded.symbols()).containsEntry("main", 0).containsKey("done");
        assertThat(encoded.relocations()).containsExactly(
                new MachineRelocation(14, "runtime_print", MachineRelocationKind.REL32, 0)
        );

        MachineSection memorySection = new MachineSection(
                ".text",
                MachineSectionKind.CODE,
                16,
                List.of(
                        new MachineInstruction("mov", MemoryOperand.base(32, "rbp", -36), new RegisterOperand("ecx")),
                        new MachineInstruction("cmp", MemoryOperand.base(32, "rbp", -40), new ImmediateOperand(0)),
                        new MachineInstruction("lea", new RegisterOperand("rax"), MemoryOperand.ripRelative(0, "message")),
                        new MachineInstruction("movsd", new RegisterOperand("xmm0"), MemoryOperand.base(64, "rbp", -48)),
                        new MachineInstruction("ret")
                )
        );
        EncodedMachineSection memoryEncoded = new X64Encoder().encode(memorySection);
        assertThat(memoryEncoded.bytes()).isNotEmpty();
        assertThat(memoryEncoded.relocations())
                .extracting(MachineRelocation::symbol)
                .containsExactly("message");
    }
}
