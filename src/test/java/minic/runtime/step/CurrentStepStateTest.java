package minic.runtime.step;

import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentStepStateTest {
    @Test
    void exposesCurrentStateFieldsAndCapabilities() {
        SourceFile sourceFile = new SourceFile("main.mc", "int main() { return 0; }");
        SourceRange range = new SourceRange(sourceFile, 0, 3);
        CurrentStepState state = new CurrentStepState(
                "main.mc",
                CompileStage.LEXER,
                3,
                1,
                PlaybackMode.PLAYING,
                Duration.ofMillis(1000),
                range,
                "读取 token",
                "产出 int 关键字",
                List.of(),
                StepCapabilities.forwardOnly()
        );

        assertThat(state.sourceName()).isEqualTo("main.mc");
        assertThat(state.currentStage()).isEqualTo(CompileStage.LEXER);
        assertThat(state.globalStepIndex()).isEqualTo(3);
        assertThat(state.stageStepIndex()).isEqualTo(1);
        assertThat(state.playbackMode()).isEqualTo(PlaybackMode.PLAYING);
        assertThat(state.frameInterval()).isEqualTo(Duration.ofMillis(1000));
        assertThat(state.sourceRangeOptional()).contains(range);
        assertThat(state.title()).isEqualTo("读取 token");
        assertThat(state.description()).isEqualTo("产出 int 关键字");
        assertThat(state.canNext()).isTrue();
        assertThat(state.canPrevious()).isFalse();
        assertThat(state.canPlay()).isTrue();
        assertThat(state.canPlayFast()).isTrue();
        assertThat(state.canPause()).isTrue();
        assertThat(state.canReversePlay()).isFalse();
    }

    @Test
    void defensivelyCopiesDiagnostics() {
        SourceFile sourceFile = new SourceFile("bad.mc", "@");
        SourceRange range = new SourceRange(sourceFile, 0, 1);
        Diagnostic diagnostic = new Diagnostic("LEX001", DiagnosticSeverity.ERROR, "非法字符", range);
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();

        CurrentStepState state = new CurrentStepState(
                "bad.mc",
                CompileStage.LEXER,
                0,
                0,
                PlaybackMode.PAUSED,
                Duration.ZERO,
                null,
                "词法错误",
                "发现非法字符",
                diagnostics,
                StepCapabilities.none()
        );
        diagnostics.add(diagnostic);

        assertThat(state.sourceRangeOptional()).isEmpty();
        assertThat(state.diagnostics()).isEmpty();
        assertThatThrownBy(() -> state.diagnostics().add(diagnostic))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validatesStableStateInvariants() {
        assertThatThrownBy(() -> state(" ", 0, 0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> state("main.mc", -1, 0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> state("main.mc", 0, -1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> state("main.mc", 0, 0, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CurrentStepState state(String sourceName, long globalStepIndex, long stageStepIndex, Duration frameInterval) {
        return new CurrentStepState(
                sourceName,
                CompileStage.SOURCE,
                globalStepIndex,
                stageStepIndex,
                PlaybackMode.PAUSED,
                frameInterval,
                null,
                "开始",
                "等待推进",
                List.of(),
                StepCapabilities.forwardOnly()
        );
    }
}
