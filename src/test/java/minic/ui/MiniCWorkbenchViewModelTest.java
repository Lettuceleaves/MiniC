package minic.ui;

import minic.uiapi.UiControlResultDto;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("source");
        assertThat(viewModel.currentStageDataProperty().get().stage()).isEqualTo("source");
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("generic");
        assertThat(viewModel.lexerVisualDataProperty().get().visualType()).isEqualTo("generic");
        assertThat(viewModel.astVisualDataProperty().get().visualType()).isEqualTo("generic");
        assertThat(viewModel.semanticVisualDataProperty().get().visualType()).isEqualTo("generic");
        assertThat(viewModel.codegenVisualDataProperty().get().visualType()).isEqualTo("generic");
        assertThat(viewModel.globalDataProperty().get().source()).contains("return 0");
    }

    @Test
    void resolvesQuotedIncludesRelativeToLoadedFilePath() throws Exception {
        Path directory = Files.createTempDirectory("minic-ui-include");
        Path header = directory.resolve("all_syntax.mh");
        Path source = directory.resolve("all_syntax.mc");
        Files.writeString(header, "int helper();\n");

        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource(
                source.toString(),
                """
                        #include "all_syntax.mh"
                        int helper() { return 4; }
                        int main() { return helper(); }
                        """
        );
        viewModel.startSession();
        viewModel.next();
        viewModel.next();

        assertThat(viewModel.currentStageDataProperty().get().diagnostics()).isEmpty();
        assertThat(viewModel.globalDataProperty().get().preprocessSummary())
                .anySatisfy(line -> assertThat(line).contains("include all_syntax.mh expanded"));
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
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("preprocess");
        assertThat(viewModel.currentStateProperty().get().globalStepIndex()).isZero();
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("generic");

        viewModel.next();
        viewModel.next();
        viewModel.next();
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("lexer");
        assertThat(viewModel.currentStageVisualDataProperty().get().lexerTokens())
                .anyMatch(token -> token.active() && token.kind().equals("INT"));

        viewModel.play();
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("PLAYING");
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("lexer");

        viewModel.tick();
        assertThat(viewModel.currentStateProperty().get().globalStepIndex()).isEqualTo(3);
        assertThat(viewModel.currentStageVisualDataProperty().get().lexerTokens())
                .anyMatch(token -> token.active() && token.text().equals("main"));

        viewModel.playFast();
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("FAST_PLAYING");
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("lexer");

        viewModel.pause();
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("PAUSED");
    }

    @Test
    void renamesSourceAndClearsCurrentSession() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("before.mc", "int main() { return 0; }");
        viewModel.startSession();

        viewModel.renameSource("after.mc");

        assertThat(viewModel.sourceNameProperty().get()).isEqualTo("after.mc");
        assertThat(viewModel.sourceTextProperty().get()).contains("return 0");
        assertThat(viewModel.sessionStartedProperty().get()).isFalse();
        assertThat(viewModel.currentStateProperty().get()).isNull();
    }

    @Test
    void nextStageControlMovesToFollowingPipelineStage() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("next-stage-view.mc", "int main() { return 0; }");
        viewModel.startSession();

        UiControlResultDto result = viewModel.nextStage();

        assertThat(result.outcome()).isEqualTo("ADVANCED");
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("preprocess");
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("generic");

        result = viewModel.nextStage();

        assertThat(result.outcome()).isEqualTo("ADVANCED");
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("lexer");
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("lexer");
        assertThat(viewModel.globalDataProperty().get().preprocessSummary()).isNotEmpty();
    }

    @Test
    void repeatedNextStageControlMovesAcrossAllPreparedStages() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("next-stage-repeat-view.mc", "int main() { return 0; }");
        viewModel.startSession();

        viewModel.nextStage();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("preprocess");
        viewModel.nextStage();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("lexer");
        viewModel.nextStage();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("parser");
        viewModel.nextStage();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("semantic");
        viewModel.nextStage();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("ir");
        viewModel.nextStage();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("codegen");
        viewModel.nextStage();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("toolchain");
    }

    @Test
    void nextAutomaticallyConfirmsExecutionInputDraft() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("execution-input-view.mc", "int main() { return 0; }");
        viewModel.startSession();
        viewModel.updateExecutionInputDraft("abc\n");

        advanceToStage(viewModel, "execution");

        assertThat(viewModel.globalDataProperty().get().executionInputSummary()).contains("stdin pending");

        UiControlResultDto result = viewModel.next();

        assertThat(result.outcome()).isEqualTo("STAGE_COMPLETED");
        assertThat(viewModel.globalDataProperty().get().executionInputSummary()).contains("stdin confirmed", "abc\n");
        assertThat(viewModel.currentStageDataProperty().get().completed()).isTrue();
    }

    @Test
    void nextStageAutomaticallyConfirmsExecutionInputDraft() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("execution-input-stage-view.mc", "int main() { return 0; }");
        viewModel.startSession();
        viewModel.updateExecutionInputDraft("stage input");

        advanceToStage(viewModel, "execution");

        UiControlResultDto result = viewModel.nextStage();

        assertThat(result.outcome()).isEqualTo("STAGE_COMPLETED");
        assertThat(viewModel.globalDataProperty().get().executionInputSummary()).contains("stdin confirmed", "stage input");
    }

    @Test
    void executionPendingInputKeepsPlaybackControlsEnabled() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("execution-controls-view.mc", "int main() { return 0; }");
        viewModel.startSession();
        viewModel.updateExecutionInputDraft("controls input");

        advanceToStage(viewModel, "execution");

        assertThat(viewModel.currentStateProperty().get().canNext()).isFalse();
        assertThat(viewModel.currentStateProperty().get().canPlay()).isFalse();
        assertThat(viewModel.currentStateProperty().get().canPlayFast()).isFalse();
        assertThat(viewModel.canNextControl()).isTrue();
        assertThat(viewModel.canNextStageControl()).isTrue();
        assertThat(viewModel.canPlayControl()).isTrue();
        assertThat(viewModel.canPlayFastControl()).isTrue();
    }

    @Test
    void playControlsAutomaticallyConfirmExecutionInputDraft() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("execution-play-view.mc", "int main() { return 0; }");
        viewModel.startSession();
        viewModel.updateExecutionInputDraft("play input");
        advanceToStage(viewModel, "execution");

        UiControlResultDto play = viewModel.play();

        assertThat(play.outcome()).isEqualTo("ADVANCED");
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("PLAYING");
        assertThat(viewModel.globalDataProperty().get().executionInputSummary()).contains("stdin confirmed", "play input");

        viewModel.pause();
        viewModel.loadSource("execution-play-fast-view.mc", "int main() { return 0; }");
        viewModel.startSession();
        viewModel.updateExecutionInputDraft("fast input");
        advanceToStage(viewModel, "execution");

        UiControlResultDto playFast = viewModel.playFast();

        assertThat(playFast.outcome()).isEqualTo("ADVANCED");
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("FAST_PLAYING");
        assertThat(viewModel.globalDataProperty().get().executionInputSummary()).contains("stdin confirmed", "fast input");
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
    void appliesPendingEditorBreakpointsWhenDebugStarts() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("debug-gutter.mc", """
                int main() {
                    int value = 1;
                    value = value + 1;
                    return value;
                }
                """);

        viewModel.setDebugBreakpoints(java.util.List.of(3));
        assertThat(viewModel.debugBreakpointLinesProperty().get()).containsExactly(3);
        viewModel.startDebug();
        viewModel.debugRunToBreakpoint();

        assertThat(viewModel.debugStateProperty().get().breakpoints())
                .extracting(minic.uiapi.UiDebugBreakpointDto::line)
                .containsExactly(3);
        assertThat(viewModel.debugStateProperty().get().currentSnapshot().stopReason()).isEqualTo("BREAKPOINT");
        assertThat(viewModel.debugStateProperty().get().currentSnapshot().sourceRange().startLine()).isEqualTo(3);
    }

    @Test
    void visualDataSwitchesAcrossMainPipelineStages() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("switch.mc", "int main() { return 0; }");
        viewModel.startSession();

        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("generic");

        advanceToStage(viewModel, "parser");
        viewModel.next();
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("ast");
        assertThat(viewModel.lexerVisualDataProperty().get().lexerTokens()).isNotEmpty();
        assertThat(new MiniCAstTreeModelFactory().create(viewModel.currentStageVisualDataProperty().get())).isNotEmpty();

        advanceToStage(viewModel, "semantic");
        viewModel.next();
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("semantic-ast-scope");
        assertThat(viewModel.astVisualDataProperty().get().astRoot()).isNotNull();
        assertThat(viewModel.currentStageVisualDataProperty().get().astRoot()).isNotNull();
        assertThat(new MiniCSemanticScopeTreeModelFactory().create(viewModel.currentStageVisualDataProperty().get())).isNotEmpty();

        advanceToStage(viewModel, "codegen");
        viewModel.next();
        assertThat(viewModel.currentStageVisualDataProperty().get().visualType()).isEqualTo("assembly");
        assertThat(viewModel.semanticVisualDataProperty().get().semanticRoot()).isNotNull();
        assertThat(viewModel.codegenVisualDataProperty().get().assemblyLines()).isNotEmpty();
        assertThat(new MiniCAssemblyTextModelFactory().create(viewModel.currentStageVisualDataProperty().get())).isNotEmpty();
    }

    @Test
    void selectedVisualStageCanReviewCompletedPipelineOutputs() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        viewModel.loadSource("review.mc", "int main() { return 0; }");
        viewModel.startSession();

        viewModel.nextStage();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("preprocess");
        viewModel.nextStage();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("lexer");
        viewModel.nextStage();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("parser");

        viewModel.selectVisualStage("lexer");
        assertThat(viewModel.selectedVisualStageProperty().get()).isEqualTo("lexer");
        assertThat(viewModel.lexerVisualDataProperty().get().lexerTokens()).isNotEmpty();

        viewModel.next();
        assertThat(viewModel.selectedVisualStageProperty().get()).isEmpty();
    }

    private static void advanceToStage(MiniCWorkbenchViewModel viewModel, String stage) {
        int guard = 0;
        while (!viewModel.currentStateProperty().get().currentStage().equals(stage) && guard++ < 1000) {
            viewModel.next();
        }
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo(stage);
    }
}
