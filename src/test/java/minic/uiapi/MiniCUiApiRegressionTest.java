package minic.uiapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCUiApiRegressionTest {
    @Test
    void exposesObservationWorkflowControlsDiagnosticsAndStageVisualData() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("ui.mc", """
                extern int printf(char *fmt, ...);
                int main() { printf("ok\\n"); return 0; }
                """);
        api.startSession();

        assertThat(api.currentState().currentStage()).isEqualTo("source");
        assertThat(api.next().outcome()).isIn("ADVANCED", "STAGE_COMPLETED");
        advanceTo(api, "lexer");
        assertThat(api.currentStageVisualData().visualType()).isEqualTo("lexer");
        api.next();
        assertThat(api.lexerVisualData().lexerTokens()).isNotEmpty();
        assertThat(api.play().outcome()).isEqualTo("ADVANCED");
        assertThat(api.tick().outcome()).isIn("ADVANCED", "STAGE_COMPLETED");
        assertThat(api.pause().outcome()).isEqualTo("ADVANCED");
    }

    @Test
    void exposesDebugControlsViewsAndDtoBoundariesWithoutRuntimeTypes() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("debug.mc", """
                int main() {
                    int x = 1;
                    x = x + 1;
                    return x;
                }
                """);

        UiDebugStateDto state = api.startDebug();
        assertThat(state.sourceName()).isEqualTo("debug.mc");
        assertThat(api.setBreakpoint(3).breakpoints()).hasSize(1);
        assertThat(api.runToBreakpoint().currentSnapshot().sourceRange().startLine()).isEqualTo(3);
        assertThat(api.stepOver().currentSnapshot()).isNotNull();
        assertThat(api.stepBack().currentSnapshot()).isNotNull();
        assertThat(api.runToEnd().executionState()).isEqualTo("COMPLETED");
        assertThat(api.currentState().getClass().getName()).contains("uiapi");
    }

    @Test
    void buildsAstIrAsmMetadataAndDataStructureViews() {
        MiniCDebugApi api = new MiniCDebugApi();
        api.loadSource("views.mc", """
                struct Node { int value; struct Node *next; };
                int main() {
                    struct Node node;
                    node.value = 1;
                    node.next = NULL;
                    return node.value;
                }
                """);
        api.startDebug();

        assertThat(api.metadataView().timeline()).isNotEmpty();
        assertThat(api.astDebugView().root()).isNotNull();
        assertThat(api.irDebugView().lines()).isNotEmpty();
        assertThat(api.asmDebugView().lines()).isNotEmpty();
        assertThat(api.dataStructureDebugView().processSpace()).isNotNull();
        assertThat(api.dataStructureDebugView().visuals()).isNotNull();
    }

    @Test
    void preservesUiDtoDefensiveCopiesAndEndToEndVisualSamples() {
        UiCurrentStateDto dto = new UiCurrentStateDto("a.mc", "source", 0, 0, "PAUSED", 1000,
                null, "title", "description", java.util.List.of(), true, false, true, true, true, false);

        assertThat(dto.diagnostics()).isEmpty();
        assertThat(dto.canReversePlay()).isFalse();

        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("visual.mc", """
                #define VALUE 4
                int main() { return VALUE; }
                """);
        api.startSession();
        advanceTo(api, "lexer");
        assertThat(api.currentStageVisualData().sourceText()).contains("return 4;");
    }

    @Test
    void exposesRealtimeAnalysisAndSourceTokenizationWithoutJavaFx() {
        MiniCRealtimeAnalysisApi api = new MiniCRealtimeAnalysisApi();

        UiRealtimeAnalysisDto result = api.analyze("realtime.mc", """
                #define VALUE 8
                int main() { return VALUE; }
                """, 42);

        assertThat(result.sourceName()).isEqualTo("realtime.mc");
        assertThat(result.version()).isEqualTo(42);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens()).extracting(UiLexerTokenVisualDto::kind).contains("INT", "IDENTIFIER");
        assertThat(api.tokenize("guide.mc", "int main() { return 0; }"))
                .extracting(UiLexerTokenVisualDto::kind)
                .contains("INT", "RETURN");
    }

    private static void advanceTo(MiniCObservationApi api, String stage) {
        for (int guard = 0; !api.currentState().currentStage().equals(stage) && guard < 1000; guard++) {
            api.next();
        }
        assertThat(api.currentState().currentStage()).isEqualTo(stage);
    }
}
