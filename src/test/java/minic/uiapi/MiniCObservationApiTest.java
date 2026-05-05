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
        assertThat(api.currentState().currentStage()).isEqualTo("preprocess");
        assertThat(api.currentStageData().stage()).isEqualTo("preprocess");
        assertThat(api.currentStageVisualData().stage()).isEqualTo("preprocess");
        assertThat(api.currentStageVisualData().visualType()).isEqualTo("generic");
        assertThat(api.globalData().source()).isEqualTo("int main() { return 0; }");
        assertThat(api.next().outcome()).isEqualTo("STAGE_COMPLETED");
        assertThat(api.next().outcome()).isEqualTo("ADVANCED");
        assertThat(api.next().outcome()).isEqualTo("ADVANCED");
        assertThat(api.currentStageVisualData().lexerTokens())
                .anySatisfy(token -> {
                    assertThat(token.kind()).isEqualTo("INT");
                    assertThat(token.text()).isEqualTo("int");
                    assertThat(token.active()).isTrue();
                });
        assertThat(api.play().outcome()).isEqualTo("ADVANCED");
        assertThat(api.playFast().outcome()).isEqualTo("ADVANCED");
        assertThat(api.tick().outcome()).isEqualTo("ADVANCED");
        assertThat(api.pause().outcome()).isEqualTo("ADVANCED");
        assertThat(api.previous().outcome()).isEqualTo("UNSUPPORTED");
        assertThat(api.reversePlay().outcome()).isEqualTo("UNSUPPORTED");
    }

    @Test
    void nextStageCompletesCurrentStageAndRefreshesVisualData() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("next-stage-ui.mc", "int main() { return 0; }");
        api.startSession();

        UiControlResultDto result = api.nextStage();

        assertThat(result.outcome()).isEqualTo("ADVANCED");
        assertThat(result.title()).contains("跳转到下一环节");
        assertThat(api.currentState().currentStage()).isEqualTo("lexer");
        assertThat(api.globalData().preprocessSummary()).isNotEmpty();
        assertThat(api.currentStageVisualData().visualType()).isEqualTo("lexer");
    }

    @Test
    void nextStageCanBeInvokedRepeatedlyAcrossPreparedStages() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("next-stage-repeat-ui.mc", "int main() { return 0; }");
        api.startSession();

        api.nextStage();
        assertThat(api.currentState().currentStage()).isEqualTo("lexer");
        api.nextStage();
        assertThat(api.currentState().currentStage()).isEqualTo("parser");
        api.nextStage();
        assertThat(api.currentState().currentStage()).isEqualTo("semantic");
        api.nextStage();
        assertThat(api.currentState().currentStage()).isEqualTo("ir");
        api.nextStage();
        assertThat(api.currentState().currentStage()).isEqualTo("codegen");
        api.nextStage();
        assertThat(api.currentState().currentStage()).isEqualTo("toolchain");
        api.nextStage();
        assertThat(api.currentState().currentStage()).isEqualTo("execution");
        assertThat(api.currentState().canNext()).isFalse();
        assertThat(api.next().title()).isEqualTo("等待运行输入");
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

    @Test
    void lexerVisualDataUsesTokenRangesAndPreservesWhitespaceOffsets() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("overlay.mc", "int\tmain\n  value123 >= 10;");
        api.startSession();

        api.next();
        api.next();
        api.next();
        UiLexerTokenVisualDto intToken = activeLexerToken(api);

        assertThat(intToken.kind()).isEqualTo("INT");
        assertThat(intToken.text()).isEqualTo("int");
        assertThat(intToken.startOffset()).isEqualTo(0);
        assertThat(intToken.endOffset()).isEqualTo(3);
        assertThat(intToken.startLine()).isEqualTo(1);
        assertThat(intToken.startColumn()).isEqualTo(1);
        assertThat(intToken.endLine()).isEqualTo(1);
        assertThat(intToken.endColumn()).isEqualTo(4);

        api.next();
        UiLexerTokenVisualDto mainToken = activeLexerToken(api);

        assertThat(mainToken.kind()).isEqualTo("IDENTIFIER");
        assertThat(mainToken.text()).isEqualTo("main");
        assertThat(mainToken.startOffset()).isEqualTo(4);
        assertThat(mainToken.startColumn()).isEqualTo(5);

        api.next();
        UiLexerTokenVisualDto identifier = activeLexerToken(api);

        assertThat(identifier.text()).isEqualTo("value123");
        assertThat(identifier.startOffset()).isEqualTo(11);
        assertThat(identifier.endOffset()).isEqualTo(19);
        assertThat(identifier.startLine()).isEqualTo(2);
        assertThat(identifier.startColumn()).isEqualTo(3);

        api.next();
        UiLexerTokenVisualDto greaterEqual = activeLexerToken(api);

        assertThat(greaterEqual.kind()).isEqualTo("GREATER_EQUAL");
        assertThat(greaterEqual.text()).isEqualTo(">=");
        assertThat(greaterEqual.startOffset()).isEqualTo(20);
        assertThat(greaterEqual.endOffset()).isEqualTo(22);
    }

    @Test
    void semanticVisualDataExposesScopeTreeAndSymbolsWithoutInternalTypes() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("semantic-visual.mc", "int main() { int value = 1; return value; }");
        api.startSession();
        advanceToStage(api, "semantic");
        while (api.currentStageData().completedSteps() < api.currentStageData().totalSteps()) {
            api.next();
        }

        UiStageVisualDto visual = api.currentStageVisualData();

        assertThat(visual.visualType()).isEqualTo("semantic-ast-scope");
        assertThat(visual.astRoot()).isNotNull();
        assertThat(visual.semanticEdgesPointChildToParent()).isTrue();
        assertThat(visual.semanticRoot().label()).isEqualTo("global scope");
        assertThat(visual.semanticRoot().symbols())
                .anySatisfy(symbol -> assertThat(symbol).contains("FUNCTION main"));
        assertThat(flatScopes(visual.semanticRoot()))
                .anySatisfy(scope -> assertThat(scope.symbols())
                        .anySatisfy(symbol -> assertThat(symbol).contains("VARIABLE value")));
    }

    @Test
    void completedSemanticVisualKeepsAstAndScopeData() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("semantic-complete-visual.mc", "int main() { int value = 1; return value; }");
        api.startSession();
        advanceToStage(api, "semantic");
        while (api.currentState().currentStage().equals("semantic")
                && api.currentStageData().completedSteps() < api.currentStageData().totalSteps()) {
            api.next();
        }
        api.next();

        UiStageVisualDto visual = api.semanticVisualData();

        assertThat(visual.visualType()).isEqualTo("semantic-ast-scope");
        assertThat(visual.astRoot()).isNotNull();
        assertThat(visual.semanticRoot()).isNotNull();
        assertThat(flatScopes(visual.semanticRoot()))
                .anySatisfy(scope -> assertThat(scope.symbols())
                        .anySatisfy(symbol -> assertThat(symbol).contains("VARIABLE value")));
    }

    @Test
    void codegenVisualDataExposesIncrementalAssemblyLinesAndCurrentLineMetadata() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("assembly-visual.mc", "int main() { return 0; }");
        api.startSession();
        advanceToStage(api, "codegen");

        assertThat(api.currentStageVisualData().assemblyLines()).isEmpty();

        api.next();
        UiAssemblyLineVisualDto first = api.currentStageVisualData().assemblyLines().getFirst();

        assertThat(first.lineNumber()).isEqualTo(1);
        assertThat(first.text()).contains("target");
        assertThat(first.kind()).isEqualTo("HEADER");
        assertThat(first.section()).isEqualTo("header");
        assertThat(first.label()).isEqualTo("target");
        assertThat(first.active()).isTrue();

        api.next();
        var lines = api.currentStageVisualData().assemblyLines();

        assertThat(lines).hasSize(2);
        assertThat(lines.getFirst().active()).isFalse();
        assertThat(lines.get(1).lineNumber()).isEqualTo(2);
        assertThat(lines.get(1).active()).isTrue();

        while (api.currentState().currentStage().equals("codegen")
                && api.currentStageVisualData().assemblyLines().stream().noneMatch(line -> line.range() != null)) {
            api.next();
        }
        UiStageVisualDto instructionVisual = api.currentStageVisualData();
        assertThat(instructionVisual.irLines()).isNotEmpty();
        assertThat(instructionVisual.irLines())
                .anySatisfy(line -> assertThat(line.active()).isTrue());
        assertThat(instructionVisual.irLines())
                .extracting(UiIrLineVisualDto::text)
                .allSatisfy(text -> assertThat(text)
                        .doesNotContain("SourceRange")
                        .doesNotContain("startOffset")
                        .doesNotContain("range="));
        assertThat(instructionVisual.irLines())
                .extracting(UiIrLineVisualDto::text)
                .anySatisfy(text -> assertThat(text).contains("return 0"));
    }

    @Test
    void completedCodegenVisualKeepsIrLinesForReview() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("codegen-review.mc", "int main() { return 0; }");
        api.startSession();
        advanceToStage(api, "codegen");
        while (api.currentState().currentStage().equals("codegen")
                && api.currentStageData().completedSteps() < api.currentStageData().totalSteps()) {
            api.next();
        }
        api.next();

        UiStageVisualDto visual = api.codegenVisualData();

        assertThat(visual.assemblyLines()).isNotEmpty();
        assertThat(visual.irLines()).isNotEmpty();
    }

    @Test
    void parserAstVisualExpandsIfElseFunctionBody() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource(
                "if-else-ast.mc",
                """
                        int main() {
                            if (1 < 2) {
                                return 7;
                            } else {
                                return 9;
                            }
                        }
                        """
        );
        api.startSession();
        advanceToStage(api, "parser");

        UiStageVisualDto beforeParseStep = api.currentStageVisualData();
        assertThat(flatAstLabels(beforeParseStep.astRoot()))
                .containsExactly("Program");
        assertThat(api.currentStageData().completedSteps()).isZero();

        api.next();
        UiStageVisualDto afterParseStep = api.currentStageVisualData();

        assertThat(flatAstLabels(afterParseStep.astRoot()))
                .contains("Program");
        assertThat(flatAstLabels(afterParseStep.astRoot())).hasSize(2);

        while (api.currentState().currentStage().equals("parser") && api.currentStageData().completedSteps() < api.currentStageData().totalSteps()) {
            api.next();
        }
        UiStageVisualDto completeParserVisual = api.currentStageVisualData();

        assertThat(flatAstLabels(completeParserVisual.astRoot()))
                .contains("Program")
                .anySatisfy(label -> assertThat(label).contains("FunctionDecl main"))
                .anySatisfy(label -> assertThat(label).contains("BlockStmt"))
                .anySatisfy(label -> assertThat(label).contains("IfStmt"))
                .anySatisfy(label -> assertThat(label).contains("BinaryExpr LESS"))
                .anySatisfy(label -> assertThat(label).contains("IntegerLiteralExpr 1"))
                .anySatisfy(label -> assertThat(label).contains("IntegerLiteralExpr 2"))
                .anySatisfy(label -> assertThat(label).contains("IntegerLiteralExpr 7"))
                .anySatisfy(label -> assertThat(label).contains("IntegerLiteralExpr 9"));
        assertThat(flatAstLabels(completeParserVisual.astRoot()).stream()
                .filter(label -> label.contains("ReturnStmt")))
                .hasSize(2);
    }

    @Test
    void parserStageCanStepThroughMultipleAstVisualNodes() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource(
                "ast-step.mc",
                """
                        int main() {
                            if (1 < 2) {
                                return 7;
                            } else {
                                return 9;
                            }
                        }
                        """
        );
        api.startSession();
        advanceToStage(api, "parser");

        assertThat(flatAstLabels(api.currentStageVisualData().astRoot()))
                .containsExactly("Program");

        java.util.ArrayList<String> currentItems = new java.util.ArrayList<>();
        java.util.ArrayList<String> activeLabels = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> nodeCounts = new java.util.ArrayList<>();
        nodeCounts.add(flatAstLabels(api.currentStageVisualData().astRoot()).size());
        for (int index = 0; index < 120 && api.currentState().currentStage().equals("parser"); index++) {
            api.next();
            currentItems.add(api.currentStageData().currentItem());
            activeAstLabel(api.currentStageVisualData()).ifPresent(activeLabels::add);
            nodeCounts.add(flatAstLabels(api.currentStageVisualData().astRoot()).size());
            if (activeLabels.stream().anyMatch(label -> label.contains("IfStmt"))
                    && activeLabels.stream().anyMatch(label -> label.contains("FunctionDecl main"))
                    && currentItems.stream().anyMatch(item -> item.contains("build BinaryExpr LESS"))) {
                break;
            }
        }

        assertThat(currentItems)
                .anySatisfy(item -> assertThat(item).contains("build FunctionDecl main"))
                .anySatisfy(item -> assertThat(item).contains("build BinaryExpr LESS"))
                .anySatisfy(item -> assertThat(item).contains("build IfStmt"));
        assertThat(activeLabels)
                .anySatisfy(label -> assertThat(label).contains("FunctionDecl main"))
                .anySatisfy(label -> assertThat(label).contains("BlockStmt"))
                .anySatisfy(label -> assertThat(label).contains("IfStmt"));
        assertThat(nodeCounts).startsWith(1, 2);
        assertThat(nodeCounts).isSorted();
        assertThat(api.currentState().currentStage()).isEqualTo("parser");
    }

    private static UiLexerTokenVisualDto activeLexerToken(MiniCObservationApi api) {
        return api.currentStageVisualData().lexerTokens().stream()
                .filter(UiLexerTokenVisualDto::active)
                .findFirst()
                .orElseThrow();
    }

    private static void advanceToStage(MiniCObservationApi api, String stage) {
        int guard = 0;
        while (!api.currentState().currentStage().equals(stage) && guard++ < 1000) {
            api.next();
        }
        assertThat(api.currentState().currentStage()).isEqualTo(stage);
    }

    private static java.util.List<UiSemanticScopeVisualDto> flatScopes(UiSemanticScopeVisualDto node) {
        java.util.ArrayList<UiSemanticScopeVisualDto> scopes = new java.util.ArrayList<>();
        scopes.add(node);
        node.children().forEach(child -> scopes.addAll(flatScopes(child)));
        return scopes;
    }

    private static java.util.List<String> flatAstLabels(UiAstNodeVisualDto node) {
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        labels.add(node.label());
        node.children().forEach(child -> labels.addAll(flatAstLabels(child)));
        return labels;
    }

    private static java.util.Optional<String> activeAstLabel(UiStageVisualDto visual) {
        return flatAstNodes(visual.astRoot()).stream()
                .filter(UiAstNodeVisualDto::active)
                .map(UiAstNodeVisualDto::label)
                .findFirst();
    }

    private static java.util.List<UiAstNodeVisualDto> flatAstNodes(UiAstNodeVisualDto node) {
        java.util.ArrayList<UiAstNodeVisualDto> nodes = new java.util.ArrayList<>();
        nodes.add(node);
        node.children().forEach(child -> nodes.addAll(flatAstNodes(child)));
        return nodes;
    }
}
