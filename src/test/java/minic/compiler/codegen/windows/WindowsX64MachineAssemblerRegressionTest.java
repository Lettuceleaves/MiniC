package minic.compiler.codegen.windows;

import minic.compiler.codegen.machine.MachineData;
import minic.compiler.codegen.machine.MachineInstruction;
import minic.compiler.codegen.machine.MachineLabel;
import minic.compiler.codegen.machine.MachineModule;
import minic.compiler.pipeline.MiniCompiler;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsX64MachineAssemblerRegressionTest {
    @Test
    void structuresExistingAssemblyWithoutLosingCodeDataOrExternalSymbols() {
        var assembly = new MiniCompiler().compile(new SourceFile("machine.mc", """
                extern int printf(char *format, ...);
                int add(int a, int b) { return a + b; }
                int main() { printf("sum=%d\\n", add(1, 2)); return 3; }
                """)).assemblySourceOptional().orElseThrow();

        MachineModule module = new WindowsX64MachineAssembler().assemble(assembly);

        assertThat(module.entrySymbol()).isEqualTo("minic$entry");
        assertThat(module.externalSymbols()).containsExactly("ExitProcess", "printf");
        assertThat(module.sections()).extracting(section -> section.name()).containsExactly(".text", ".rdata");
        assertThat(module.sections().getFirst().items())
                .anySatisfy(item -> assertThat(item).isEqualTo(new MachineLabel("main", true)))
                .anySatisfy(item -> assertThat(item).isInstanceOfSatisfying(MachineInstruction.class,
                        instruction -> assertThat(instruction.mnemonic()).isEqualTo("call")));
        assertThat(module.sections().get(1).items())
                .anySatisfy(item -> assertThat(item).isInstanceOf(MachineData.class));
    }
}
