package minic.ui;

import minic.uiapi.MiniCObservationApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCAstGraphModelFactoryTest {
    @Test
    void createsGraphNodesAndEdgesForIfElsePreview() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource(
                "if-else-graph.mc",
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

        MiniCAstGraphModel initialGraph = new MiniCAstGraphModelFactory().create(api.currentStageVisualData());

        assertThat(initialGraph.nodes()).hasSize(1);
        assertThat(initialGraph.edges()).isEmpty();

        api.next();
        MiniCAstGraphModel firstStepGraph = new MiniCAstGraphModelFactory().create(api.currentStageVisualData());

        assertThat(firstStepGraph.nodes()).hasSize(2);
        assertThat(firstStepGraph.edges()).hasSize(1);

        while (api.currentState().currentStage().equals("parser") && api.currentStageData().completedSteps() < api.currentStageData().totalSteps()) {
            api.next();
        }

        MiniCAstGraphModel graph = new MiniCAstGraphModelFactory().create(api.currentStageVisualData());

        assertThat(graph.nodes()).hasSizeGreaterThan(8);
        assertThat(graph.edges()).hasSize(graph.nodes().size() - 1);
        assertThat(graph.nodes())
                .anySatisfy(node -> {
                    assertThat(node.label()).contains("Program");
                    assertThat(node.root()).isTrue();
                })
                .anySatisfy(node -> assertThat(node.label()).contains("IfStmt"))
                .anySatisfy(node -> assertThat(node.label()).contains("IntegerLiteralExpr 7"))
                .anySatisfy(node -> assertThat(node.label()).contains("IntegerLiteralExpr 9"));
        assertThat(graph.width()).isGreaterThan(520);
        assertThat(graph.height()).isGreaterThan(300);
    }

    private static void advanceToStage(MiniCObservationApi api, String stage) {
        int guard = 0;
        while (!api.currentState().currentStage().equals(stage) && guard++ < 1000) {
            api.next();
        }
        assertThat(api.currentState().currentStage()).isEqualTo(stage);
    }
}
