package minic.compiler.parser;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.decl.StructDecl;
import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.compiler.stage.CompilerStageInput;
import minic.compiler.stage.CompilerStageOutput;
import minic.compiler.stage.CompilerStageResult;
import minic.compiler.stage.CompilerStageSnapshot;
import minic.compiler.stage.CompilerStageState;
import minic.compiler.stage.CompilerStageStatus;
import minic.compiler.stage.CompilerStageWork;
import minic.diagnostics.Diagnostic;
import minic.runtime.step.CompileStage;
import minic.runtime.step.StageProgress;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 可正向步进的 parser 状态。
 */
public final class ParserStepState implements CompilerStageState<ParserStepState.Input, ParserStepState.Work, ParserStepState.Output> {
    private final Input input;
    private final Work work;
    private Object currentNode;
    private long stepCount;

    /**
     * 创建 parser 状态。
     *
     * @param tokens lexer 产出的 token 列表，必须包含 EOF token
     */
    public ParserStepState(List<Token> tokens) {
        input = new Input(tokens);
        ParserState parserState = new ParserState(input.tokens);
        ExpressionParser expressionParser = new ExpressionParser(parserState);
        StatementParser statementParser = new StatementParser(parserState, expressionParser);
        work = new Work(parserState, new DeclarationParser(parserState, statementParser));
    }

    @Override
    public CompileStage stage() {
        return CompileStage.PARSER;
    }

    @Override
    public Input input() {
        return input;
    }

    @Override
    public Work work() {
        return work;
    }

    @Override
    public CompilerStageSnapshot snapshot() {
        CompilerStageStatus status = work.state.isAtEnd()
                ? CompilerStageStatus.COMPLETED
                : stepCount == 0 ? CompilerStageStatus.NOT_STARTED : CompilerStageStatus.RUNNING;
        return new CompilerStageSnapshot(
                CompileStage.PARSER,
                status,
                new StageProgress(stepCount, -1, work.state.isAtEnd()),
                currentNode == null ? "" : currentNode.getClass().getSimpleName(),
                work.state.diagnostics()
        );
    }

    @Override
    public boolean canNext() {
        return !work.state.isAtEnd();
    }

    /**
     * 推进 parser，完成一个顶层 AST 节点。
     *
     * @return 本步产物
     */
    public ParserStep next() {
        if (!canNext()) {
            throw new IllegalStateException("parser state is already completed");
        }
        currentNode = null;
        if (work.state.check(TokenKind.STRUCT)) {
            StructDecl structDecl = work.declarationParser.parseStructDecl();
            if (structDecl != null) {
                work.structs.add(structDecl);
                work.completedNodes.add(structDecl);
                currentNode = structDecl;
            } else {
                work.state.synchronizeFunction();
            }
        } else {
            FunctionDecl functionDecl = work.declarationParser.parseFunctionDecl();
            if (functionDecl != null) {
                work.functions.add(functionDecl);
                work.completedNodes.add(functionDecl);
                currentNode = functionDecl;
            } else {
                work.state.synchronizeFunction();
            }
        }
        stepCount++;
        return currentNode == null ? ParserStep.noNode() : ParserStep.node(currentNode);
    }

    @Override
    public CompilerStageSnapshot advance() {
        next();
        return snapshot();
    }

    @Override
    public CompilerStageResult<Output> result() {
        return CompilerStageResult.success(CompileStage.PARSER, new Output(toParseResult()));
    }

    /**
     * 返回当前完成的 AST 节点。
     *
     * @return AST 节点 Optional
     */
    public Optional<Object> currentNode() {
        return Optional.ofNullable(currentNode);
    }

    /**
     * 返回已完成 AST 节点。
     *
     * @return 已完成 AST 节点
     */
    public List<Object> completedNodes() {
        return List.copyOf(work.completedNodes);
    }

    /**
     * 返回 parser diagnostics。
     *
     * @return diagnostics
     */
    public List<Diagnostic> diagnostics() {
        return List.copyOf(work.state.diagnostics());
    }

    /**
     * 构建与原 parser API 等价的解析结果。
     *
     * @return 解析结果
     */
    public ParseResult toParseResult() {
        return new ParseResult(new Program(work.structs, work.functions, programRange()), work.state.diagnostics());
    }

    private SourceRange programRange() {
        ArrayList<SourceRange> ranges = new ArrayList<>();
        work.structs.stream().map(StructDecl::range).forEach(ranges::add);
        work.functions.stream().map(FunctionDecl::range).forEach(ranges::add);
        if (ranges.isEmpty()) {
            return work.state.peek().range();
        }
        SourceRange firstRange = ranges.getFirst();
        SourceRange lastRange = ranges.getLast();
        return new SourceRange(firstRange.sourceFile(), firstRange.startOffset(), lastRange.endOffset());
    }

    /**
     * Parser 阶段输入数据。
     *
     * @param tokens lexer 产出的 token 列表
     */
    public record Input(List<Token> tokens) implements CompilerStageInput {
        /**
         * 创建输入数据。
         *
         * @param tokens lexer 产出的 token 列表
         */
        public Input {
            Objects.requireNonNull(tokens, "tokens");
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("tokens must contain EOF");
            }
            tokens = List.copyOf(tokens);
        }
    }

    /**
     * Parser 阶段内部工作数据。
     */
    public static final class Work implements CompilerStageWork {
        private final ParserState state;
        private final DeclarationParser declarationParser;
        private final ArrayList<StructDecl> structs = new ArrayList<>();
        private final ArrayList<FunctionDecl> functions = new ArrayList<>();
        private final ArrayList<Object> completedNodes = new ArrayList<>();

        private Work(ParserState state, DeclarationParser declarationParser) {
            this.state = state;
            this.declarationParser = declarationParser;
        }

        /**
         * 返回当前 token 游标。
         *
         * @return token 游标
         */
        public int currentIndex() {
            return state.currentIndex();
        }

        /**
         * 返回已完成节点数。
         *
         * @return 已完成节点数
         */
        public int completedNodeCount() {
            return completedNodes.size();
        }
    }

    /**
     * Parser 阶段输出数据。
     *
     * @param parseResult 解析结果
     */
    public record Output(ParseResult parseResult) implements CompilerStageOutput {
        /**
         * 创建输出数据。
         *
         * @param parseResult 解析结果
         */
        public Output {
            Objects.requireNonNull(parseResult, "parseResult");
        }
    }
}
