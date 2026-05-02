package minic.runtime.step;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StageStepperTest {
    @Test
    void exposesForwardOnlyStageStepperContract() {
        StageStepper stepper = new DummyStepper();

        assertThat(stepper.stage()).isEqualTo(CompileStage.LEXER);
        assertThat(stepper.canNext()).isTrue();
        assertThat(stepper.canPrevious()).isFalse();
        assertThat(stepper.snapshot().currentStage()).isEqualTo(CompileStage.LEXER);
        assertThat(stepper.data().stage()).isEqualTo(CompileStage.LEXER);

        StepResult previous = stepper.previous();

        assertThat(previous.outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
        assertThat(previous.stage()).isEqualTo(CompileStage.LEXER);
        assertThat(previous.title()).contains("上一步");
    }

    @Test
    void nextReturnsStepResultWithoutExposingCompilerWorkData() {
        StageStepper stepper = new DummyStepper();

        StepResult result = stepper.next();

        assertThat(result.outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(StageStepper.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .contains("next", "previous", "snapshot", "data", "canNext", "canPrevious")
                .doesNotContain("work");
    }

    private static final class DummyStepper implements StageStepper {
        @Override
        public CompileStage stage() {
            return CompileStage.LEXER;
        }

        @Override
        public boolean canNext() {
            return true;
        }

        @Override
        public StepResult next() {
            return StepResult.advanced(CompileStage.LEXER, "读取 token", "推进一个 token。");
        }

        @Override
        public CurrentStepState snapshot() {
            return new CurrentStepState(
                    "dummy.mc",
                    CompileStage.LEXER,
                    0,
                    0,
                    PlaybackMode.PAUSED,
                    Duration.ofSeconds(1),
                    null,
                    "读取 token",
                    "等待推进。",
                    List.of(),
                    StepCapabilities.forwardOnly()
            );
        }

        @Override
        public StageStepData data() {
            return new StageStepData(
                    CompileStage.LEXER,
                    new StageProgress(0, 1, false),
                    List.of("source length=0"),
                    "",
                    List.of(),
                    List.of()
            );
        }
    }
}
