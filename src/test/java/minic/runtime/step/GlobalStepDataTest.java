package minic.runtime.step;

import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalStepDataTest {
    @Test
    void exposesGlobalDataAndDefensivelyCopiesCollections() {
        SourceFile sourceFile = new SourceFile("bad.mc", "@");
        SourceRange range = new SourceRange(sourceFile, 0, 1);
        Diagnostic diagnostic = new Diagnostic("LEX001", DiagnosticSeverity.ERROR, "非法字符", range);
        ArrayList<String> stageSummaries = new ArrayList<>();
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        ArrayList<String> tokenSummary = new ArrayList<>();
        ArrayList<String> astSummary = new ArrayList<>();
        ArrayList<String> semanticSummary = new ArrayList<>();
        ArrayList<String> irSummary = new ArrayList<>();
        ArrayList<String> assemblySummary = new ArrayList<>();
        ArrayList<String> artifactSummary = new ArrayList<>();

        GlobalStepData data = new GlobalStepData(
                sourceFile.content(),
                stageSummaries,
                diagnostics,
                tokenSummary,
                astSummary,
                semanticSummary,
                irSummary,
                assemblySummary,
                artifactSummary
        );
        stageSummaries.add("lexer 1/3");
        diagnostics.add(diagnostic);
        tokenSummary.add("INVALID @");
        astSummary.add("Program");
        semanticSummary.add("main ok");
        irSummary.add("return 0");
        assemblySummary.add("main PROC");
        artifactSummary.add("main.exe");

        assertThat(data.source()).isEqualTo("@");
        assertThat(data.stageSummaries()).isEmpty();
        assertThat(data.diagnostics()).isEmpty();
        assertThat(data.tokenSummary()).isEmpty();
        assertThat(data.astSummary()).isEmpty();
        assertThat(data.semanticSummary()).isEmpty();
        assertThat(data.irSummary()).isEmpty();
        assertThat(data.assemblySummary()).isEmpty();
        assertThat(data.artifactSummary()).isEmpty();
        assertThatThrownBy(() -> data.stageSummaries().add("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> data.diagnostics().add(diagnostic))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> data.tokenSummary().add("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
