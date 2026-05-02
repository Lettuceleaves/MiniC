package minic.compiler.stage;

import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.runtime.step.CompileStage;
import minic.runtime.step.StageProgress;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompilerStageModelTest {
    @Test
    void snapshotRepresentsStageStatusAndDefensivelyCopiesDiagnostics() {
        SourceFile sourceFile = new SourceFile("bad.mc", "@");
        SourceRange range = new SourceRange(sourceFile, 0, 1);
        Diagnostic diagnostic = new Diagnostic("LEX001", DiagnosticSeverity.ERROR, "非法字符", range);
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();

        CompilerStageSnapshot snapshot = new CompilerStageSnapshot(
                CompileStage.LEXER,
                CompilerStageStatus.RUNNING,
                new StageProgress(1, 2, false),
                "INVALID @",
                diagnostics
        );
        diagnostics.add(diagnostic);

        assertThat(snapshot.stage()).isEqualTo(CompileStage.LEXER);
        assertThat(snapshot.status()).isEqualTo(CompilerStageStatus.RUNNING);
        assertThat(snapshot.progress().completedSteps()).isEqualTo(1);
        assertThat(snapshot.currentItem()).isEqualTo("INVALID @");
        assertThat(snapshot.diagnostics()).isEmpty();
        assertThatThrownBy(() -> snapshot.diagnostics().add(diagnostic))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resultRepresentsSuccessAndFailureWithoutExposingMutableDiagnostics() {
        SourceFile sourceFile = new SourceFile("bad.mc", "@");
        SourceRange range = new SourceRange(sourceFile, 0, 1);
        Diagnostic diagnostic = new Diagnostic("LEX001", DiagnosticSeverity.ERROR, "非法字符", range);
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        DummyOutput output = new DummyOutput("tokens");

        CompilerStageResult<DummyOutput> success = CompilerStageResult.success(CompileStage.LEXER, output);
        CompilerStageResult<DummyOutput> failure = CompilerStageResult.failure(CompileStage.LEXER, diagnostics);
        diagnostics.add(diagnostic);

        assertThat(success.successful()).isTrue();
        assertThat(success.output()).isSameAs(output);
        assertThat(failure.successful()).isFalse();
        assertThat(failure.output()).isNull();
        assertThat(failure.diagnostics()).isEmpty();
        assertThatThrownBy(() -> failure.diagnostics().add(diagnostic))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void statePatternSeparatesInputWorkAndOutput() {
        DummyState state = new DummyState(new DummyInput("source"), new DummyWork(0));

        assertThat(state.stage()).isEqualTo(CompileStage.LEXER);
        assertThat(state.input().source()).isEqualTo("source");
        assertThat(state.work().offset()).isEqualTo(0);
        assertThat(state.snapshot().status()).isEqualTo(CompilerStageStatus.NOT_STARTED);

        CompilerStageSnapshot snapshot = state.advance();

        assertThat(snapshot.status()).isEqualTo(CompilerStageStatus.COMPLETED);
        assertThat(state.canNext()).isFalse();
        assertThat(state.result().output().value()).isEqualTo("source");
    }

    @Test
    void existingOutputWrapperCanCarryCurrentPipelineResults() {
        ExistingCompilerOutputs outputs = new ExistingCompilerOutputs(null, null, null, null, null);

        assertThat(outputs.lexResult()).isNull();
        assertThat(outputs.parseResult()).isNull();
        assertThat(outputs.semanticResult()).isNull();
        assertThat(outputs.irModule()).isNull();
        assertThat(outputs.assemblySource()).isNull();
    }

    private record DummyInput(String source) implements CompilerStageInput {
    }

    private record DummyWork(int offset) implements CompilerStageWork {
    }

    private record DummyOutput(String value) implements CompilerStageOutput {
    }

    private static final class DummyState implements CompilerStageState<DummyInput, DummyWork, DummyOutput> {
        private final DummyInput input;
        private DummyWork work;
        private CompilerStageSnapshot snapshot = CompilerStageSnapshot.notStarted(CompileStage.LEXER);

        private DummyState(DummyInput input, DummyWork work) {
            this.input = input;
            this.work = work;
        }

        @Override
        public CompileStage stage() {
            return CompileStage.LEXER;
        }

        @Override
        public DummyInput input() {
            return input;
        }

        @Override
        public DummyWork work() {
            return work;
        }

        @Override
        public CompilerStageSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public boolean canNext() {
            return snapshot.status() != CompilerStageStatus.COMPLETED;
        }

        @Override
        public CompilerStageSnapshot advance() {
            work = new DummyWork(input.source().length());
            snapshot = new CompilerStageSnapshot(
                    CompileStage.LEXER,
                    CompilerStageStatus.COMPLETED,
                    StageProgress.completed(1),
                    input.source(),
                    List.of()
            );
            return snapshot;
        }

        @Override
        public CompilerStageResult<DummyOutput> result() {
            return CompilerStageResult.success(CompileStage.LEXER, new DummyOutput(input.source()));
        }
    }
}
