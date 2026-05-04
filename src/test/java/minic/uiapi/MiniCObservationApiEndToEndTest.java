package minic.uiapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCObservationApiEndToEndTest {
    @Test
    void runsUiFacadeWorkflowFromLoadedSourceThroughForwardPlaybackControls() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource(
                "ui-e2e.mc",
                """
                        extern int puts(char *text);
                        int main() {
                            return puts("ok");
                        }
                        """
        );
        api.startSession();

        assertThat(api.currentState().currentStage()).isEqualTo("lexer");
        assertThat(api.currentStageData().accumulatedOutput()).isEmpty();

        UiControlResultDto first = api.next();
        UiStageDataDto afterFirst = api.currentStageData();

        assertThat(first.outcome()).isEqualTo("ADVANCED");
        assertThat(afterFirst.stage()).isEqualTo("lexer");
        assertThat(afterFirst.accumulatedOutput()).contains("EXTERN extern");
        assertThat(api.currentState().globalStepIndex()).isEqualTo(1);

        api.play();
        UiControlResultDto playTick = api.tick();

        assertThat(playTick.outcome()).isEqualTo("ADVANCED");
        assertThat(api.currentState().playbackMode()).isEqualTo("PLAYING");
        assertThat(api.currentState().frameIntervalMillis()).isEqualTo(1000);
        assertThat(api.currentState().globalStepIndex()).isEqualTo(2);

        api.playFast();
        UiControlResultDto fastTick = api.tick();

        assertThat(fastTick.outcome()).isEqualTo("ADVANCED");
        assertThat(api.currentState().playbackMode()).isEqualTo("FAST_PLAYING");
        assertThat(api.currentState().frameIntervalMillis()).isEqualTo(500);
        assertThat(api.currentState().globalStepIndex()).isEqualTo(3);

        assertThat(api.pause().outcome()).isEqualTo("ADVANCED");
        assertThat(api.currentState().playbackMode()).isEqualTo("PAUSED");

        assertThat(api.previous().outcome()).isEqualTo("UNSUPPORTED");
        assertThat(api.reversePlay().outcome()).isEqualTo("UNSUPPORTED");

        int guard = 0;
        while (api.currentState().canNext() && guard++ < 1000) {
            api.next();
        }

        assertThat(api.currentState().currentStage()).isEqualTo("execution");
        assertThat(api.currentState().canNext()).isFalse();
        assertThat(api.globalData().executionInputSummary()).contains("stdin pending");
        assertThat(api.globalData().tokenSummary()).isNotEmpty();
        assertThat(api.globalData().astSummary()).isNotEmpty();
        assertThat(api.globalData().semanticSummary()).isNotEmpty();
        assertThat(api.globalData().irSummary()).isNotEmpty();
        assertThat(api.globalData().assemblySummary()).contains("END");
    }

    @Test
    void exposesStageSpecificVisualDataAcrossPreparedStages() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("visual-e2e.mc", "int main() { return 0; }");
        api.startSession();

        assertThat(api.currentStageVisualData().visualType()).isEqualTo("lexer");

        advanceToStage(api, "parser");
        while (api.currentState().currentStage().equals("parser") && api.currentStageData().completedSteps() < api.currentStageData().totalSteps()) {
            api.next();
        }
        UiStageVisualDto parserVisual = api.currentStageVisualData();
        assertThat(parserVisual.visualType()).isEqualTo("ast");
        assertThat(parserVisual.astRoot()).isNotNull();
        assertThat(flatAstLabels(parserVisual.astRoot()))
                .contains("Program")
                .anySatisfy(label -> assertThat(label).contains("FunctionDecl main"))
                .anySatisfy(label -> assertThat(label).contains("BlockStmt"))
                .anySatisfy(label -> assertThat(label).contains("ReturnStmt"))
                .anySatisfy(label -> assertThat(label).contains("IntegerLiteralExpr"));
        assertThat(parserVisual.astRoot().children().getFirst().range().startOffset()).isZero();
        assertThat(flatAstNodes(parserVisual.astRoot())).anySatisfy(node ->
                assertThat(node.label()).contains("FunctionDecl main"));

        advanceToStage(api, "semantic");
        api.next();
        api.next();
        api.next();
        api.next();
        UiStageVisualDto semanticVisual = api.currentStageVisualData();
        assertThat(semanticVisual.visualType()).isEqualTo("semantic-ast-scope");
        assertThat(semanticVisual.astRoot()).isNotNull();
        assertThat(semanticVisual.semanticRoot().label()).isEqualTo("global scope");
        assertThat(semanticVisual.semanticEdgesPointChildToParent()).isTrue();
        assertThat(semanticVisual.semanticRoot().symbols())
                .anySatisfy(symbol -> assertThat(symbol).contains("FUNCTION main"));

        advanceToStage(api, "ir");
        api.next();
        UiStageVisualDto irVisual = api.currentStageVisualData();
        assertThat(irVisual.visualType()).isEqualTo("ir-ast-scope");
        assertThat(irVisual.astRoot()).isNotNull();
        assertThat(irVisual.semanticRoot()).isNotNull();

        advanceToStage(api, "codegen");
        api.next();
        UiStageVisualDto codegenVisual = api.currentStageVisualData();
        assertThat(codegenVisual.visualType()).isEqualTo("assembly");
        assertThat(codegenVisual.assemblyLines())
                .hasSize(1)
                .anySatisfy(line -> {
                    assertThat(line.lineNumber()).isEqualTo(1);
                    assertThat(line.kind()).isEqualTo("HEADER");
                    assertThat(line.section()).isEqualTo("header");
                    assertThat(line.label()).isEqualTo("target");
                    assertThat(line.active()).isTrue();
                });
    }

    private static void advanceToStage(MiniCObservationApi api, String stage) {
        int guard = 0;
        while (!api.currentState().currentStage().equals(stage) && guard++ < 1000) {
            api.next();
        }
        assertThat(api.currentState().currentStage()).isEqualTo(stage);
    }

    private static java.util.List<String> flatAstLabels(UiAstNodeVisualDto node) {
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        labels.add(node.label());
        node.children().forEach(child -> labels.addAll(flatAstLabels(child)));
        return labels;
    }

    private static java.util.List<UiAstNodeVisualDto> flatAstNodes(UiAstNodeVisualDto node) {
        java.util.ArrayList<UiAstNodeVisualDto> nodes = new java.util.ArrayList<>();
        nodes.add(node);
        node.children().forEach(child -> nodes.addAll(flatAstNodes(child)));
        return nodes;
    }
}
