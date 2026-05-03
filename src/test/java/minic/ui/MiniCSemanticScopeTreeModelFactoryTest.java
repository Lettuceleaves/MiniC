package minic.ui;

import minic.uiapi.MiniCObservationApi;
import minic.uiapi.UiStageVisualDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCSemanticScopeTreeModelFactoryTest {
    @Test
    void createsTopDownScopeTreeRowsWithReverseArrowDirectionAndSymbols() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("scope-view.mc", "int main() { int value = 1; return value; }");
        api.startSession();
        advanceToStage(api, "semantic");
        while (api.currentStageData().completedSteps() < api.currentStageData().totalSteps()) {
            api.next();
        }
        UiStageVisualDto visual = api.currentStageVisualData();

        var rows = new MiniCSemanticScopeTreeModelFactory().create(visual);

        assertThat(rows.getFirst().label()).isEqualTo("global scope");
        assertThat(rows.getFirst().depth()).isZero();
        assertThat(rows)
                .allSatisfy(row -> assertThat(row.arrowDirection()).isEqualTo("child-to-parent"))
                .anySatisfy(row -> assertThat(row.symbols())
                        .anySatisfy(symbol -> assertThat(symbol).contains("FUNCTION main")))
                .anySatisfy(row -> assertThat(row.symbols())
                        .anySatisfy(symbol -> assertThat(symbol).contains("VARIABLE value")));
        assertThat(rows).anyMatch(MiniCSemanticScopeTreeLine::onActivePath);
    }

    private static void advanceToStage(MiniCObservationApi api, String stage) {
        int guard = 0;
        while (!api.currentState().currentStage().equals(stage) && guard++ < 1000) {
            api.next();
        }
        assertThat(api.currentState().currentStage()).isEqualTo(stage);
    }
}
