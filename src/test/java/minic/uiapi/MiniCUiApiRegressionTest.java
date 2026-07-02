package minic.uiapi;

import minic.uiapi.web.MiniCUiApiJson;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void exposesCompositeRunToExecutionAndDerivedUiModels() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("run-to-execution.mc", """
                int main() {
                    return 0;
                }
                """);
        api.startSession();

        UiControlResultDto result = api.runToExecution();

        assertThat(result.outcome()).isIn("ADVANCED", "STAGE_COMPLETED", "CANNOT_ADVANCE");
        assertThat(api.currentState().currentStage()).isEqualTo("execution");
        assertThat(api.stageViews())
                .extracting(UiStageViewDto::id)
                .containsExactly("source", "preprocess", "lexer", "parser", "semantic", "ir", "codegen", "toolchain", "execution");
        assertThat(api.stageViews())
                .filteredOn(stage -> stage.id().equals("execution"))
                .singleElement()
                .satisfies(stage -> {
                    assertThat(stage.title()).isEqualTo("执行");
                    assertThat(stage.state()).isIn("running", "done", "error");
                    assertThat(stage.progressPercent()).isBetween(0, 100);
                });
        assertThat(api.inspectorModel().currentState()).contains("阶段: 执行");
        assertThat(api.inspectorModel().accumulatedOutput()).contains("IR:", "汇编:");
    }

    @Test
    void completedIrStageVisualShowsFormattedIrUsedByCodegenInput() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("ir-after.mc", """
                int main() {
                    int value = 1;
                    return value + 1;
                }
                """);
        api.startSession();
        advanceTo(api, "ir");
        while (!api.currentStageData().completed()) {
            api.next();
        }

        List<String> irAfter = api.currentStageVisualData().irLines().stream()
                .map(UiIrLineVisualDto::text)
                .toList();

        assertThat(irAfter).isNotEmpty();
        assertThat(irAfter.getFirst()).isEqualTo("function main");
        assertThat(irAfter).anyMatch(line -> line.contains("return"));

        api.nextStage();
        List<String> codegenBefore = api.codegenVisualData().irLines().stream()
                .map(UiIrLineVisualDto::text)
                .toList();
        assertThat(irAfter).isEqualTo(codegenBefore);
        assertThat(api.irVisualData().irLines().stream()
                .map(UiIrLineVisualDto::text)
                .toList())
                .isEqualTo(codegenBefore);
    }

    @Test
    void runToExecutionReturnsStableCannotAdvanceResultWhenAlreadyAtExecution() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("already-execution.mc", "int main() { return 0; }");
        api.startSession();
        api.runToExecution();

        UiControlResultDto result = api.runToExecution();

        assertThat(result.outcome()).isEqualTo("CANNOT_ADVANCE");
        assertThat(result.stage()).isEqualTo("execution");
        assertThat(result.title()).isEqualTo("已在执行阶段");
        assertThat(api.currentState().currentStage()).isEqualTo("execution");
    }

    @Test
    void globalDataKeepsOldConstructorWhileDerivingInputFlags() {
        UiGlobalDataDto data = new UiGlobalDataDto(
                "",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of("stdin pending", "stdin confirmed"),
                java.util.List.of()
        );

        assertThat(data.executionInputPending()).isTrue();
        assertThat(data.executionInputConfirmed()).isTrue();
    }

    @Test
    void globalDataDerivesInputFlagsWhenReadingOldJsonPayloads() throws Exception {
        MiniCUiApiJson json = new MiniCUiApiJson();
        UiGlobalDataDto data = json.read("""
                {
                  "source": "",
                  "stageSummaries": [],
                  "diagnostics": [],
                  "preprocessSummary": [],
                  "tokenSummary": [],
                  "astSummary": [],
                  "semanticSummary": [],
                  "irSummary": [],
                  "assemblySummary": [],
                  "artifactSummary": [],
                  "executionInputSummary": ["stdin pending", "stdin confirmed"],
                  "executionOutputSummary": []
                }
                """, UiGlobalDataDto.class);

        assertThat(data.executionInputPending()).isTrue();
        assertThat(data.executionInputConfirmed()).isTrue();
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
