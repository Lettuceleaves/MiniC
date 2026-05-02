package minic.runtime.step;

import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepResultTest {
    @Test
    void representsAllRequiredOutcomes() {
        assertThat(StepResult.advanced(CompileStage.LEXER, "token", "读取 token").outcome())
                .isEqualTo(StepOutcome.ADVANCED);
        assertThat(StepResult.stageCompleted(CompileStage.PARSER, "完成", "AST 已完成").outcome())
                .isEqualTo(StepOutcome.STAGE_COMPLETED);
        assertThat(StepResult.cannotAdvance(CompileStage.TOOLCHAIN, "结束", "没有更多步骤").outcome())
                .isEqualTo(StepOutcome.CANNOT_ADVANCE);
        assertThat(StepResult.unsupported(CompileStage.SOURCE, "不支持", "预留能力").outcome())
                .isEqualTo(StepOutcome.UNSUPPORTED);
        assertThat(StepResult.failed(CompileStage.SEMANTIC, "失败", "存在诊断", new ArrayList<>()).outcome())
                .isEqualTo(StepOutcome.FAILED);
    }

    @Test
    void defensivelyCopiesDiagnostics() {
        SourceFile sourceFile = new SourceFile("bad.mc", "@");
        SourceRange range = new SourceRange(sourceFile, 0, 1);
        Diagnostic diagnostic = new Diagnostic("LEX001", DiagnosticSeverity.ERROR, "非法字符", range);
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();

        StepResult result = StepResult.failed(CompileStage.LEXER, "失败", "词法错误", diagnostics);
        diagnostics.add(diagnostic);

        assertThat(result.diagnostics()).isEmpty();
        assertThatThrownBy(() -> result.diagnostics().add(diagnostic))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
