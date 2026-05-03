package minic.ui;

import minic.uiapi.UiRealtimeAnalysisDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCRealtimeAnalyzerTest {
    @Test
    void analyzesLexerParserAndSemanticDiagnosticsForEditorInput() {
        UiRealtimeAnalysisDto result = MiniCRealtimeAnalyzer.analyzeNow(
                "live.mc",
                """
                        int main() {
                            return missing;
                        }
                        """,
                7
        );

        assertThat(result.version()).isEqualTo(7);
        assertThat(result.tokens()).isNotEmpty();
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic.message()).isEqualTo("未解析变量：missing"));
    }

    @Test
    void stopsAtParserDiagnosticsBeforeSemanticAnalysis() {
        UiRealtimeAnalysisDto result = MiniCRealtimeAnalyzer.analyzeNow("live.mc", "int main( {", 1);

        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic.message()).contains("期望"));
    }
}
