package minic.ui;

import minic.uiapi.MiniCObservationApi;
import minic.uiapi.UiStageVisualDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCLexerOverlayModelFactoryTest {
    @Test
    void createsMonoRowsAndActiveTokenSegmentWithoutCollapsingWhitespace() {
        MiniCObservationApi api = new MiniCObservationApi();
        String source = "int\tmain\n  return 0;";
        api.loadSource("overlay-pane.mc", source);
        api.startSession();
        api.next();
        api.next();
        api.next();
        api.next();
        UiStageVisualDto visual = api.currentStageVisualData();

        var rows = new MiniCLexerOverlayModelFactory().create(source, visual);

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().segments())
                .containsExactly(
                        new MiniCLexerOverlaySegment("int", true),
                        new MiniCLexerOverlaySegment("\tmain", false)
                );
        assertThat(rows.get(1).segments())
                .containsExactly(new MiniCLexerOverlaySegment("  return 0;", false));
    }
}
