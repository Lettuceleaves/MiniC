package minic.runtime.step;

import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageStepDataTest {
    @Test
    void exposesStageDataAndDefensivelyCopiesCollections() {
        SourceFile sourceFile = new SourceFile("bad.mc", "@");
        SourceRange range = new SourceRange(sourceFile, 0, 1);
        Diagnostic diagnostic = new Diagnostic("LEX001", DiagnosticSeverity.ERROR, "非法字符", range);
        ArrayList<String> inputSummary = new ArrayList<>();
        ArrayList<String> output = new ArrayList<>();
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();

        StageStepData data = new StageStepData(
                CompileStage.LEXER,
                new StageProgress(1, 3, false),
                inputSummary,
                "INT int",
                output,
                diagnostics
        );
        inputSummary.add("source length=1");
        output.add("INT int");
        diagnostics.add(diagnostic);

        assertThat(data.stage()).isEqualTo(CompileStage.LEXER);
        assertThat(data.progress().completedSteps()).isEqualTo(1);
        assertThat(data.currentItem()).isEqualTo("INT int");
        assertThat(data.inputSummary()).isEmpty();
        assertThat(data.accumulatedOutput()).isEmpty();
        assertThat(data.diagnostics()).isEmpty();
        assertThatThrownBy(() -> data.inputSummary().add("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> data.accumulatedOutput().add("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> data.diagnostics().add(diagnostic))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
