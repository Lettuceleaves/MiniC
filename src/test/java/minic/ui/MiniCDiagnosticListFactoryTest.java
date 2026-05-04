package minic.ui;

import minic.uiapi.UiDiagnosticDto;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiRealtimeAnalysisDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCDiagnosticListFactoryTest {
    @Test
    void createsDiagnosticItemsWithSourceRanges() {
        UiGlobalDataDto globalData = new UiGlobalDataDto(
                "int main() { return @; }",
                List.of(),
                List.of(new UiDiagnosticDto("LEX001", "ERROR", "非法字符", "bad.mc", 20, 21)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        List<MiniCDiagnosticItem> items = new MiniCDiagnosticListFactory().create(
                null,
                globalData
        );

        assertThat(items).isNotEmpty();
        assertThat(items.getFirst().displayText()).contains("ERROR");
        assertThat(items.getFirst().range().sourceName()).isEqualTo("bad.mc");
    }

    @Test
    void selectionExposesSelectedDiagnosticRange() {
        MiniCDiagnosticSelection selection = new MiniCDiagnosticSelection();
        MiniCDiagnosticItem item = new MiniCDiagnosticItem(
                "LEX001",
                "ERROR",
                "bad char",
                new minic.uiapi.UiSourceRangeDto("bad.mc", 1, 2)
        );

        selection.select(item);

        assertThat(selection.selectedRangeProperty().get().startOffset()).isEqualTo(1);
    }

    @Test
    void realtimeDiagnosticsDisplayLineAndColumn() {
        UiRealtimeAnalysisDto realtimeAnalysis = new UiRealtimeAnalysisDto(
                "bad.mc",
                "int main() {\n    return @;\n}",
                List.of(new UiDiagnosticDto("LEX001", "ERROR", "非法字符", "bad.mc", 24, 25)),
                List.of(),
                1
        );

        List<MiniCDiagnosticItem> items = new MiniCDiagnosticListFactory().create(null, null, realtimeAnalysis);

        assertThat(items.getFirst().displayText()).contains("bad.mc:2:12");
    }
}
