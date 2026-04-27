package minic.diagnostics;

import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosticTest {
    @Test
    void storesDiagnosticFields() {
        SourceFile sourceFile = new SourceFile("diagnostic.mc", "return 1;");
        SourceRange range = new SourceRange(sourceFile, 0, 6);

        Diagnostic diagnostic = new Diagnostic(
                "PAR001",
                DiagnosticSeverity.ERROR,
                "缺少函数声明",
                range
        );

        assertThat(diagnostic.code()).isEqualTo("PAR001");
        assertThat(diagnostic.severity()).isEqualTo(DiagnosticSeverity.ERROR);
        assertThat(diagnostic.message()).isEqualTo("缺少函数声明");
        assertThat(diagnostic.range()).isSameAs(range);
    }

    @Test
    void rejectsBlankCodeAndMessage() {
        SourceFile sourceFile = new SourceFile("invalid-diagnostic.mc", "abc");
        SourceRange range = new SourceRange(sourceFile, 0, 1);

        assertThatThrownBy(() -> new Diagnostic("", DiagnosticSeverity.ERROR, "message", range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Diagnostic("LEX001", DiagnosticSeverity.ERROR, " ", range))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
