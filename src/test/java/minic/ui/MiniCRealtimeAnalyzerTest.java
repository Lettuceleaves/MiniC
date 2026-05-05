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

    @Test
    void mapsPreprocessedParserDiagnosticsBackToEditorSourceOffsets() {
        String source = """
                #define BAD (

                int main() {
                    return BAD;
                }
                """;

        UiRealtimeAnalysisDto result = MiniCRealtimeAnalyzer.analyzeNow("live.mc", source, 3);

        int macroUseOffset = source.indexOf("BAD;");
        int macroUseEndOffset = macroUseOffset + "BAD;".length();
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> {
                    assertThat(diagnostic.message()).contains("期望");
                    assertThat(diagnostic.startOffset()).isBetween(macroUseOffset, macroUseEndOffset);
                    assertThat(diagnostic.endOffset()).isBetween(diagnostic.startOffset(), macroUseEndOffset + 1);
                });
    }

    @Test
    void preprocessesMacrosBeforeRealtimeSemanticAnalysis() {
        UiRealtimeAnalysisDto result = MiniCRealtimeAnalyzer.analyzeNow(
                "live.mc",
                """
                        #define VALUE 4

                        int main() {
                            return VALUE;
                        }
                        """,
                2
        );

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.tokens())
                .extracting(token -> token.text())
                .contains("define", "VALUE");
    }
}
