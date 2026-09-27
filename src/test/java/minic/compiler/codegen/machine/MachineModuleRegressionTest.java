package minic.compiler.codegen.machine;

import minic.compiler.codegen.target.TargetPlatform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineModuleRegressionTest {
    @Test
    void modelsStructuredInstructionsDataSymbolsAndValidation() {
        MachineInstruction move = new MachineInstruction(
                "MOV",
                new RegisterOperand("EAX"),
                new ImmediateOperand(42)
        );
        MachineSection text = new MachineSection(
                ".text",
                MachineSectionKind.CODE,
                16,
                List.of(new MachineLabel("main", true), move, new MachineInstruction("ret"))
        );
        MachineSection data = new MachineSection(
                ".rdata",
                MachineSectionKind.READ_ONLY_DATA,
                8,
                List.of(new MachineLabel("message", false), new MachineData(new byte[]{'o', 'k', 0}, 1))
        );
        MachineModule module = new MachineModule(
                TargetPlatform.WINDOWS_X86_64,
                "main",
                List.of(text, data),
                List.of("ExitProcess")
        );

        assertThat(module.sections()).containsExactly(text, data);
        assertThat(move.mnemonic()).isEqualTo("mov");
        assertThat(move.operands()).containsExactly(new RegisterOperand("eax"), new ImmediateOperand(42));
        assertThat(module.externalSymbols()).containsExactly("ExitProcess");
        assertThatThrownBy(() -> new MachineModule(
                TargetPlatform.WINDOWS_X86_64,
                "main",
                List.of(text, text),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate section");
    }
}
