package minic.runtime.step;

import minic.compiler.lexer.LexStep;
import minic.compiler.lexer.LexerState;
import minic.compiler.lexer.Token;
import minic.diagnostics.Diagnostic;
import minic.source.SourceFile;
import minic.source.SourceRange;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Lexer 阶段统一兼容层适配器。
 */
public final class LexerStageStepper implements StageStepper {
    private final LexerState lexerState;
    private final SourceFile sourceFile;
    private long globalStepIndex;
    private StepResult lastResult;

    /**
     * 创建 Lexer 阶段适配器。
     *
     * @param sourceFile 源码文件
     */
    public LexerStageStepper(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        lexerState = new LexerState(sourceFile);
        lastResult = StepResult.advanced(CompileStage.LEXER, "词法分析待开始", "等待读取第一个 token。");
    }

    @Override
    public CompileStage stage() {
        return CompileStage.LEXER;
    }

    @Override
    public boolean canNext() {
        return lexerState.canNext();
    }

    @Override
    public StepResult next() {
        if (!lexerState.canNext()) {
            lastResult = StepResult.cannotAdvance(CompileStage.LEXER, "词法分析已完成", "没有更多 token 或词法诊断。");
            return lastResult;
        }
        LexStep step = lexerState.next();
        globalStepIndex++;
        if (step.diagnosticOptional().isPresent()) {
            Diagnostic diagnostic = step.diagnosticOptional().orElseThrow();
            lastResult = StepResult.failed(
                    CompileStage.LEXER,
                    "词法诊断",
                    diagnostic.message(),
                    List.of(diagnostic)
            );
            return lastResult;
        }
        Token token = step.tokenOptional().orElseThrow();
        if (!lexerState.canNext()) {
            lastResult = StepResult.stageCompleted(
                    CompileStage.LEXER,
                    "词法分析完成",
                    "已产出 EOF token。"
            );
            return lastResult;
        }
        lastResult = StepResult.advanced(
                CompileStage.LEXER,
                "读取 token",
                tokenSummary(token)
        );
        return lastResult;
    }

    @Override
    public CurrentStepState snapshot() {
        SourceRange range = lexerState.currentToken()
                .map(Token::range)
                .or(() -> lexerState.currentDiagnostic().map(Diagnostic::range))
                .orElse(null);
        return new CurrentStepState(
                sourceFile.path(),
                CompileStage.LEXER,
                globalStepIndex,
                lexerState.snapshot().progress().completedSteps(),
                PlaybackMode.PAUSED,
                Duration.ofSeconds(1),
                range,
                lastResult.title(),
                lastResult.description(),
                lastResult.diagnostics(),
                new StepCapabilities(lexerState.canNext(), false, lexerState.canNext(), lexerState.canNext(), true, false)
        );
    }

    @Override
    public StageStepData data() {
        return new StageStepData(
                CompileStage.LEXER,
                lexerState.snapshot().progress(),
                List.of(
                        "source=" + sourceFile.path(),
                        "length=" + sourceFile.content().length()
                ),
                currentItem(),
                lexerState.tokens().stream().map(LexerStageStepper::tokenSummary).toList(),
                lexerState.diagnostics()
        );
    }

    /**
     * 返回底层 lexer 状态，供后续阶段读取词法结果。
     *
     * @return lexer 状态
     */
    public LexerState lexerState() {
        return lexerState;
    }

    /**
     * 返回 Lexer 正在处理的源码。
     *
     * @return 源码文件
     */
    public SourceFile sourceFile() {
        return sourceFile;
    }

    private String currentItem() {
        return lexerState.currentToken().map(LexerStageStepper::tokenSummary)
                .or(() -> lexerState.currentDiagnostic().map(diagnostic -> diagnostic.code() + " " + diagnostic.message()))
                .orElse("");
    }

    private static String tokenSummary(Token token) {
        String lexeme = token.lexeme().isEmpty() ? "<empty>" : token.lexeme();
        return token.kind() + " " + lexeme;
    }
}
