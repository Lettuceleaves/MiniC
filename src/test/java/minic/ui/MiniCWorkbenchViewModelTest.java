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
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("lexer");
        assertThat(viewModel.lexerVisualDataProperty().get().visualType()).isEqualTo("lexer");
        assertThat(viewModel.astVisualDataProperty().get().visualType()).isEqualTo("lexer");
        assertThat(viewModel.semanticVisualDataProperty().get().visualType()).isEqualTo("lexer");
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
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("lexer");
        assertThat(viewModel.currentStageVisualDataProperty().get().lexerTokens())
                .anyMatch(token -> token.active() && token.kind().equals("INT"));

        viewModel.play();
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("PLAYING");
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("lexer");

        viewModel.tick();
        assertThat(viewModel.currentStateProperty().get().globalStepIndex()).isEqualTo(2);
        assertThat(viewModel.currentStageVisualDataProperty().get().lexerTokens())
                .anyMatch(token -> token.active() && token.text().equals("main"));

        viewModel.playFast();
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("FAST_PLAYING");
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("lexer");

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

    @Test
    void visualDataSwitchesAcrossMainPipelineStages() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("switch.mc", "int main() { return 0; }");
        viewModel.startSession();

        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("lexer");

        advanceToStage(viewModel, "parser");
        viewModel.next();
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("ast");
        assertThat(viewModel.lexerVisualDataProperty().get().lexerTokens()).isNotEmpty();
        assertThat(new MiniCAstTreeModelFactory().create(viewModel.currentStageVisualDataProperty().get())).isNotEmpty();

        advanceToStage(viewModel, "semantic");
        viewModel.next();
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("semantic-scope");
        assertThat(viewModel.astVisualDataProperty().get().astRoot()).isNotNull();
        assertThat(new MiniCSemanticScopeTreeModelFactory().create(viewModel.currentStageVisualDataProperty().get())).isNotEmpty();

        advanceToStage(viewModel, "codegen");
        viewModel.next();
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("assembly");
        assertThat(viewModel.semanticVisualDataProperty().get().semanticRoot()).isNotNull();
        assertThat(new MiniCAssemblyTextModelFactory().create(viewModel.currentStageVisualDataProperty().get())).isNotEmpty();
    }

    private static void advanceToStage(MiniCWorkbenchViewModel viewModel, String stage) {
        int guard = 0;
        while (!viewModel.currentStateProperty().get().currentStage().equals(stage) && guard++ < 1000) {
            viewModel.next();
        }
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo(stage);
    }
}
