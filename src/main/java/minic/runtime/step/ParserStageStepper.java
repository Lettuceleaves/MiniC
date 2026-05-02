package minic.runtime.step;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.StructDecl;
import minic.compiler.lexer.Token;
import minic.compiler.parser.ParserStep;
import minic.compiler.parser.ParserStepState;
import minic.diagnostics.Diagnostic;
import minic.source.SourceRange;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Parser 阶段统一兼容层适配器。
 */
public final class ParserStageStepper implements StageStepper {
    private final ParserStepState parserState;
    private long globalStepIndex;
    private StepResult lastResult;

    /**
     * 创建 Parser 阶段适配器。
     *
     * @param tokens lexer 产出的 token 列表
     */
    public ParserStageStepper(List<Token> tokens) {
        parserState = new ParserStepState(Objects.requireNonNull(tokens, "tokens"));
        lastResult = StepResult.advanced(CompileStage.PARSER, "语法分析待开始", "等待解析第一个顶层声明。");
    }

    @Override
    public CompileStage stage() {
        return CompileStage.PARSER;
    }

    @Override
    public boolean canNext() {
        return parserState.canNext();
    }

    @Override
    public StepResult next() {
        if (!parserState.canNext()) {
            lastResult = StepResult.cannotAdvance(CompileStage.PARSER, "语法分析已完成", "没有更多顶层声明。");
            return lastResult;
        }
        int diagnosticsBefore = parserState.diagnostics().size();
        ParserStep step = parserState.next();
        globalStepIndex++;
        List<Diagnostic> newDiagnostics = parserState.diagnostics().stream()
                .skip(diagnosticsBefore)
                .toList();
        if (!newDiagnostics.isEmpty()) {
            lastResult = StepResult.failed(
                    CompileStage.PARSER,
                    "语法诊断",
                    newDiagnostics.getLast().message(),
                    newDiagnostics
            );
            return lastResult;
        }
        if (step.nodeOptional().isEmpty()) {
            lastResult = StepResult.advanced(CompileStage.PARSER, "跳过无效声明", "错误恢复推进到下一个顶层声明。");
            return lastResult;
        }
        String summary = nodeSummary(step.nodeOptional().orElseThrow());
        if (!parserState.canNext()) {
            lastResult = StepResult.stageCompleted(CompileStage.PARSER, "语法分析完成", summary);
            return lastResult;
        }
        lastResult = StepResult.advanced(CompileStage.PARSER, "完成 AST 节点", summary);
        return lastResult;
    }

    @Override
    public CurrentStepState snapshot() {
        return new CurrentStepState(
                sourceName(),
                CompileStage.PARSER,
                globalStepIndex,
                parserState.snapshot().progress().completedSteps(),
                PlaybackMode.PAUSED,
                Duration.ofSeconds(1),
                currentRange(),
                lastResult.title(),
                lastResult.description(),
                lastResult.diagnostics(),
                new StepCapabilities(parserState.canNext(), false, parserState.canNext(), parserState.canNext(), true, false)
        );
    }

    @Override
    public StageStepData data() {
        return new StageStepData(
                CompileStage.PARSER,
                parserState.snapshot().progress(),
                tokenSummary(),
                parserState.currentNode().map(ParserStageStepper::nodeSummary)
                        .orElseGet(() -> parserState.diagnostics().isEmpty() ? "" : diagnosticSummary(parserState.diagnostics().getLast())),
                parserState.completedNodes().stream().map(ParserStageStepper::nodeSummary).toList(),
                parserState.diagnostics()
        );
    }

    /**
     * 返回底层 parser 状态，供后续阶段读取解析结果。
     *
     * @return parser 状态
     */
    public ParserStepState parserState() {
        return parserState;
    }

    private String sourceName() {
        if (parserState.input().tokens().isEmpty()) {
            return "<unknown>";
        }
        return parserState.input().tokens().getFirst().range().sourceFile().path();
    }

    private SourceRange currentRange() {
        return parserState.currentNode()
                .map(ParserStageStepper::nodeRange)
                .or(() -> parserState.diagnostics().isEmpty()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(parserState.diagnostics().getLast().range()))
                .orElse(null);
    }

    private List<String> tokenSummary() {
        List<Token> tokens = parserState.input().tokens();
        return List.of(
                "tokens=" + tokens.size(),
                "first=" + tokenSummary(tokens.getFirst()),
                "last=" + tokenSummary(tokens.getLast())
        );
    }

    private static String tokenSummary(Token token) {
        String lexeme = token.lexeme().isEmpty() ? "<empty>" : token.lexeme();
        return token.kind() + " " + lexeme;
    }

    private static String nodeSummary(Object node) {
        if (node instanceof StructDecl structDecl) {
            return "StructDecl " + structDecl.name() + " fields=" + structDecl.fields().size();
        }
        if (node instanceof FunctionDecl functionDecl) {
            String kind = functionDecl.external() ? "extern" : functionDecl.hasBody() ? "function" : "declaration";
            return "FunctionDecl " + functionDecl.name() + " " + kind + " params=" + functionDecl.parameters().size();
        }
        return node.getClass().getSimpleName();
    }

    private static SourceRange nodeRange(Object node) {
        if (node instanceof StructDecl structDecl) {
            return structDecl.range();
        }
        if (node instanceof FunctionDecl functionDecl) {
            return functionDecl.range();
        }
        return null;
    }

    private static String diagnosticSummary(Diagnostic diagnostic) {
        return diagnostic.code() + " " + diagnostic.message();
    }
}
