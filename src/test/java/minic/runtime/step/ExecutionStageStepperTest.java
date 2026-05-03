package minic.runtime.step;

import minic.compiler.toolchain.ExecutableArtifact;
import minic.runtime.execution.ExecutionResult;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionStageStepperTest {
    @Test
    void waitsForConfirmedInputBeforeRunningExecutable() {
        SourceFile sourceFile = new SourceFile("run.mc", "int main() { return 0; }");
        AtomicReference<String> capturedInput = new AtomicReference<>();
        ExecutionStageStepper stepper = new ExecutionStageStepper(
                sourceFile,
                new ExecutableArtifact(Path.of("build", "minic", "run.exe")),
                (source, artifact, input) -> {
                    capturedInput.set(input);
                    return new ExecutionResult("ok\n", "", 42, List.of());
                }
        );

        assertThat(stepper.canNext()).isFalse();
        assertThat(stepper.next().outcome()).isEqualTo(StepOutcome.CANNOT_ADVANCE);

        stepper.confirmInput("abc\n");
        StepResult result = stepper.next();

        assertThat(result.outcome()).isEqualTo(StepOutcome.STAGE_COMPLETED);
        assertThat(capturedInput).hasValue("abc\n");
        assertThat(stepper.canNext()).isFalse();
        assertThat(stepper.data().accumulatedOutput()).contains("exitCode 42", "stdout:", "ok");
        assertThat(stepper.data().inputSummary()).contains("stdin confirmed", "abc\n");
    }
}
