package minic.uiapi;

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
        assertThat(api.currentState().currentStage()).isEqualTo("lexer");
        assertThat(api.currentStageData().stage()).isEqualTo("lexer");
        assertThat(api.globalData().source()).isEqualTo("int main() { return 0; }");
        assertThat(api.next().outcome()).isEqualTo("ADVANCED");
        assertThat(api.play().outcome()).isEqualTo("ADVANCED");
        assertThat(api.playFast().outcome()).isEqualTo("ADVANCED");
        assertThat(api.tick().outcome()).isEqualTo("ADVANCED");
        assertThat(api.pause().outcome()).isEqualTo("ADVANCED");
        assertThat(api.previous().outcome()).isEqualTo("UNSUPPORTED");
        assertThat(api.reversePlay().outcome()).isEqualTo("UNSUPPORTED");
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
