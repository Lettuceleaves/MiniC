package minic.runtime.step;

import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.target.TargetPlatform;
import minic.compiler.toolchain.ExecutableArtifact;
import minic.compiler.toolchain.ToolchainResult;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolchainStageStepperTest {
    @Test
    void runsConfiguredToolchainAndReportsExecutableArtifact() {
        SourceFile sourceFile = new SourceFile("sample.mc", "int main() { return 0; }");
        AssemblySource assemblySource = new AssemblySource(
                TargetPlatform.WINDOWS_X86_64,
                "minic$entry",
                "PUBLIC minic$entry\nEND\n"
        );
        ToolchainStageStepper stepper = new ToolchainStageStepper(
                sourceFile,
                assemblySource,
                (source, assembly, outputDirectory, artifactName) -> new ToolchainResult(
                        outputDirectory.resolve(artifactName + ".asm"),
                        outputDirectory.resolve(artifactName + ".obj"),
                        new ExecutableArtifact(outputDirectory.resolve(artifactName + ".exe")),
                        List.of()
                )
        );

        StepResult result = stepper.next();

        assertThat(result.outcome()).isEqualTo(StepOutcome.STAGE_COMPLETED);
        assertThat(stepper.canNext()).isFalse();
        assertThat(stepper.data().accumulatedOutput())
                .anySatisfy(line -> assertThat(line).contains("executable", Path.of("build", "minic", "sample.exe").toString()));
    }
}
