package minic.uiapi;

import minic.runtime.step.CompileStage;
import minic.runtime.step.StepOutcome;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniCObservationApiTest {
    @Test
    void loadsSourceStartsSessionAndExposesCompileControlsAndData() {
        MiniCObservationApi api = new MiniCObservationApi();

        api.loadSource("ui.mc", "int main() { return 0; }");
        api.startSession();

        assertThat(api.currentState().sourceName()).isEqualTo("ui.mc");
        assertThat(api.currentState().currentStage()).isEqualTo(CompileStage.LEXER);
        assertThat(api.currentStageData().stage()).isEqualTo(CompileStage.LEXER);
        assertThat(api.globalData().source()).isEqualTo("int main() { return 0; }");
        assertThat(api.next().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(api.play().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(api.playFast().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(api.tick().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(api.pause().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(api.previous().outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
        assertThat(api.reversePlay().outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
    }

    @Test
    void loadsSourceFileAndRequiresSessionBeforeControls() {
        MiniCObservationApi api = new MiniCObservationApi();

        assertThatThrownBy(api::startSession)
                .isInstanceOf(IllegalStateException.class);

        api.loadSource(new SourceFile("file.mc", "int main() { return 0; }"));
        assertThatThrownBy(api::currentState)
                .isInstanceOf(IllegalStateException.class);

        api.startSession();

        assertThat(api.currentState().sourceName()).isEqualTo("file.mc");
    }
}
