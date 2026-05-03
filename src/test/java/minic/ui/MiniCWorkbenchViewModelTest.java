package minic.ui;

import minic.uiapi.UiControlResultDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWorkbenchViewModelTest {
    @Test
    void loadsSourceStartsSessionAndRefreshesDtoState() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();

        viewModel.loadSource("view.mc", "int main() { return 0; }");
        assertThat(viewModel.sourceNameProperty().get()).isEqualTo("view.mc");
        assertThat(viewModel.sourceTextProperty().get()).contains("return 0");
        assertThat(viewModel.sessionStartedProperty().get()).isFalse();
        assertThat(viewModel.currentStateProperty().get()).isNull();

        viewModel.startSession();

        assertThat(viewModel.sessionStartedProperty().get()).isTrue();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("lexer");
        assertThat(viewModel.currentStageDataProperty().get().stage()).isEqualTo("lexer");
        assertThat(viewModel.globalDataProperty().get().source()).contains("return 0");
    }

    @Test
    void controlActionsUpdateLastResultAndState() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("controls.mc", "int main() { return 0; }");
        viewModel.startSession();

        UiControlResultDto next = viewModel.next();

        assertThat(next.outcome()).isEqualTo("ADVANCED");
        assertThat(viewModel.lastOutcomeProperty().get()).isEqualTo("ADVANCED");
        assertThat(viewModel.lastControlResultProperty().get()).isSameAs(next);
        assertThat(viewModel.currentStateProperty().get().globalStepIndex()).isEqualTo(1);

        viewModel.play();
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("PLAYING");

        viewModel.tick();
        assertThat(viewModel.currentStateProperty().get().globalStepIndex()).isEqualTo(2);

        viewModel.playFast();
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("FAST_PLAYING");

        viewModel.pause();
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("PAUSED");
    }

    @Test
    void publicApiExposesOnlyUiLayerAndJavaFxTypes() {
        assertThat(MiniCWorkbenchViewModel.class.getMethods())
                .filteredOn(method -> method.getDeclaringClass() == MiniCWorkbenchViewModel.class)
                .allSatisfy(method -> {
                    assertThat(method.getReturnType().getName())
                            .doesNotStartWith("minic.compiler.")
                            .doesNotStartWith("minic.runtime.step.")
                            .doesNotStartWith("minic.session.");
                    for (Class<?> parameterType : method.getParameterTypes()) {
                        assertThat(parameterType.getName())
                                .doesNotStartWith("minic.compiler.")
                                .doesNotStartWith("minic.runtime.step.")
                                .doesNotStartWith("minic.session.");
                    }
                });
    }
}
