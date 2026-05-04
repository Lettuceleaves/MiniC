package minic.ui;

import minic.uiapi.MiniCObservationApi;
import minic.uiapi.UiStageVisualDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCAstTreeModelFactoryTest {
    @Test
    void createsTreeRowsWithDepthActiveStateAndSourceRange() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("ast-view.mc", "int main() { return 0; }");
        api.startSession();
        advanceToStage(api, "parser");
        while (api.currentState().currentStage().equals("parser") && api.currentStageData().completedSteps() < api.currentStageData().totalSteps()) {
            api.next();
        }
        UiStageVisualDto visual = api.currentStageVisualData();

        var rows = new MiniCAstTreeModelFactory().create(visual);

        assertThat(rows.getFirst().label()).isEqualTo("Program");
        assertThat(rows.getFirst().depth()).isZero();
        assertThat(rows.getFirst().active()).isFalse();
        assertThat(rows.getFirst().range()).isEqualTo(visual.astRoot().range());
        assertThat(rows)
                .anySatisfy(row -> {
                    assertThat(row.label()).contains("FunctionDecl main");
                    assertThat(row.depth()).isEqualTo(1);
                    assertThat(row.range()).isNotNull();
                })
                .anySatisfy(row -> {
                    assertThat(row.label()).contains("ReturnStmt");
                    assertThat(row.depth()).isGreaterThan(1);
                });
    }

    @Test
    void createsExpandedRowsIncrementallyForIfElseParserSteps() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource(
                "if-else-preview.mc",
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

        var initialRows = new MiniCAstTreeModelFactory().create(api.currentStageVisualData());

        assertThat(initialRows)
                .extracting(MiniCAstTreeLine::label)
                .containsExactly("Program");

        while (api.currentState().currentStage().equals("parser") && api.currentStageData().completedSteps() < api.currentStageData().totalSteps()) {
            api.next();
        }

        var rows = new MiniCAstTreeModelFactory().create(api.currentStageVisualData());

        assertThat(rows).hasSizeGreaterThan(8);
        assertThat(rows)
                .anySatisfy(row -> assertThat(row.label()).contains("IfStmt"))
                .anySatisfy(row -> assertThat(row.label()).contains("BinaryExpr LESS"))
                .anySatisfy(row -> assertThat(row.label()).contains("IntegerLiteralExpr 7"))
                .anySatisfy(row -> assertThat(row.label()).contains("IntegerLiteralExpr 9"));
        assertThat(rows.stream().filter(row -> row.label().contains("ReturnStmt"))).hasSize(2);
    }

    private static void advanceToStage(MiniCObservationApi api, String stage) {
        int guard = 0;
        while (!api.currentState().currentStage().equals(stage) && guard++ < 1000) {
            api.next();
        }
        assertThat(api.currentState().currentStage()).isEqualTo(stage);
    }
}
