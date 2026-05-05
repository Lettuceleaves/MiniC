package minic.ui;

import minic.uiapi.MiniCObservationApi;
import minic.uiapi.UiStageVisualDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCAssemblyTextModelFactoryTest {
    @Test
    void createsMonospaceAssemblyRowsAndMarksLatestLineActive() {
        MiniCObservationApi api = new MiniCObservationApi();
        api.loadSource("assembly-view.mc", "int main() { return 0; }");
        api.startSession();
        advanceToStage(api, "codegen");
        api.next();
        api.next();
        UiStageVisualDto visual = api.currentStageVisualData();

        var rows = new MiniCAssemblyTextModelFactory().create(visual);

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().lineNumber()).isEqualTo(1);
        assertThat(rows.getFirst().active()).isFalse();
        assertThat(rows.get(1).lineNumber()).isEqualTo(2);
        assertThat(rows.get(1).active()).isTrue();
        assertThat(rows.get(1).text()).isNotBlank();
        assertThat(rows.get(1).section()).isEqualTo("header");
    }

    private static void advanceToStage(MiniCObservationApi api, String stage) {
        int guard = 0;
        while (!api.currentState().currentStage().equals(stage) && guard++ < 1000) {
            api.next();
        }
        assertThat(api.currentState().currentStage()).isEqualTo(stage);
    }
}
