package minic.compiler.semantic;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.FieldAccessExpr;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IndexExpr;
import minic.compiler.ast.expr.UnaryExpr;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.ForStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ast.stmt.WhileStmt;
import minic.compiler.stage.CompilerStageInput;
import minic.compiler.stage.CompilerStageOutput;
import minic.compiler.stage.CompilerStageResult;
import minic.compiler.stage.CompilerStageSnapshot;
import minic.compiler.stage.CompilerStageState;
import minic.compiler.stage.CompilerStageStatus;
import minic.compiler.stage.CompilerStageWork;
import minic.compiler.type.MiniType;
import minic.diagnostics.Diagnostic;
import minic.runtime.step.CompileStage;
import minic.runtime.step.StageProgress;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 可正向步进的语义分析状态。
 */
public final class SemanticStepState implements CompilerStageState<SemanticStepState.Input, SemanticStepState.Work, SemanticStepState.Output> {
    private final Input input;
    private final Work work;
    private final List<PlannedAction> plannedActions;
    private SemanticAction currentAction;
    private int nextActionIndex;

    /**
     * 创建语义分析状态。
     *
     * @param program AST 程序
     */
    public SemanticStepState(Program program) {
        input = new Input(Objects.requireNonNull(program, "program"));
        SemanticReporter reporter = new SemanticReporter();
        Scope globalScope = new Scope();
        Map<Expression, MiniType> expressionTypes = new IdentityHashMap<>();
        StructRegistry structRegistry = new StructRegistry(globalScope, reporter);
        FunctionRegistry functionRegistry = new FunctionRegistry(globalScope, reporter);
        StatementSemanticAnalyzer statementAnalyzer = new StatementSemanticAnalyzer(
                globalScope,
                functionRegistry,
                structRegistry,
                reporter,
                expressionTypes
        );
        work = new Work(reporter, globalScope, expressionTypes, structRegistry, functionRegistry, statementAnalyzer);
        plannedActions = planActions(program);
    }

    @Override
    public CompileStage stage() {
        return CompileStage.SEMANTIC;
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
        CompilerStageStatus status = canNext()
                ? nextActionIndex == 0 ? CompilerStageStatus.NOT_STARTED : CompilerStageStatus.RUNNING
                : CompilerStageStatus.COMPLETED;
        return new CompilerStageSnapshot(
                CompileStage.SEMANTIC,
                status,
                new StageProgress(nextActionIndex, plannedActionCount(), !canNext()),
                currentAction == null ? "" : currentAction.kind() + " " + currentAction.subject(),
                work.reporter.diagnostics()
        );
    }

    @Override
    public boolean canNext() {
        return nextActionIndex < plannedActionCount();
    }

    /**
     * 执行一个语义动作。
     *
     * @return 本步动作
     */
    public SemanticAction next() {
        if (!canNext()) {
            throw new IllegalStateException("semantic state is already completed");
        }
        int diagnosticsBefore = work.reporter.diagnostics().size();
        currentAction = executeAction(nextActionIndex);
        nextActionIndex++;
        if (work.reporter.diagnostics().size() > diagnosticsBefore) {
            Diagnostic diagnostic = work.reporter.diagnostics().getLast();
            currentAction = SemanticAction.diagnostic(
                    currentAction.subject(),
                    diagnostic,
                    currentAction.astNode(),
                    currentAction.scope()
            );
        }
        return currentAction;
    }

    @Override
    public CompilerStageSnapshot advance() {
        next();
        return snapshot();
    }

    @Override
    public CompilerStageResult<Output> result() {
        return CompilerStageResult.success(CompileStage.SEMANTIC, new Output(toSemanticResult()));
    }

    /**
     * 返回当前语义动作。
     *
     * @return 语义动作 Optional
     */
    public Optional<SemanticAction> currentAction() {
        return Optional.ofNullable(currentAction);
    }

    /**
     * 返回已产出 diagnostics。
     *
     * @return diagnostics
     */
    public List<Diagnostic> diagnostics() {
        return List.copyOf(work.reporter.diagnostics());
    }

    /**
     * 构建与原 semantic API 等价的语义结果。
     *
     * @return 语义结果
     */
    public SemanticResult toSemanticResult() {
        while (canNext()) {
            next();
        }
        return new SemanticResult(
                work.globalScope,
                work.expressionTypes,
                work.structLayouts,
                work.reporter.diagnostics()
        );
    }

    private SemanticAction executeAction(int actionIndex) {
        if (actionIndex < 5) {
            return switch (actionIndex) {
            case 0 -> {
                work.structRegistry.defineStructs(input.program);
                yield SemanticAction.of(SemanticActionKind.REGISTER_STRUCTS, "structs=" + input.program.structs().size(), input.program, work.globalScope);
            }
            case 1 -> {
                work.structRegistry.validateProgramTypes(input.program);
                yield SemanticAction.of(SemanticActionKind.CHECK_TYPES, "program types", input.program, work.globalScope);
            }
            case 2 -> {
                work.structLayouts = work.reporter.diagnostics().isEmpty()
                        ? work.structRegistry.computeLayouts()
                        : Map.of();
                yield SemanticAction.of(SemanticActionKind.COMPUTE_STRUCT_LAYOUTS, "struct layouts", input.program, work.globalScope);
            }
            case 3 -> {
                work.functionRegistry.defineFunctions(input.program);
                yield SemanticAction.of(SemanticActionKind.REGISTER_FUNCTIONS, "functions=" + input.program.functions().size(), input.program, work.globalScope);
            }
            case 4 -> {
                work.functionRegistry.validateMain(input.program);
                yield SemanticAction.of(SemanticActionKind.VALIDATE_MAIN, "main", input.program, work.globalScope);
            }
            default -> throw new IllegalArgumentException("unsupported semantic action: " + actionIndex);
            };
        }
        return executePlannedAction(plannedActions.get(actionIndex - 5));
    }

    private SemanticAction executePlannedAction(PlannedAction action) {
        return switch (action.kind()) {
            case ANALYZE_FUNCTION_BODY -> {
                work.statementAnalyzer.beginFunction(action.functionDecl());
                yield SemanticAction.of(
                        SemanticActionKind.ANALYZE_FUNCTION_BODY,
                        action.functionDecl().name(),
                        action.functionDecl().bodyOptional().orElseThrow(),
                        work.statementAnalyzer.currentFunctionScope()
                );
            }
            case ANALYZE_STATEMENT -> {
                work.statementAnalyzer.analyzeCurrentFunctionTopLevelStatement(action.statement());
                yield SemanticAction.of(
                        SemanticActionKind.ANALYZE_STATEMENT,
                        statementSubject(action),
                        action.statement(),
                        innermostScopeFor(action.statement().range(), work.statementAnalyzer.currentFunctionScope())
                );
            }
            case VISIT_AST_NODE -> SemanticAction.of(
                    SemanticActionKind.VISIT_AST_NODE,
                    nodeSubject(action.astNode()),
                    action.astNode(),
                    innermostScopeFor(rangeOf(action.astNode()), work.statementAnalyzer.currentFunctionScope())
            );
            case VALIDATE_FUNCTION_RETURN -> {
                try {
                    work.statementAnalyzer.validateCurrentFunctionReturn();
                    yield SemanticAction.of(
                            SemanticActionKind.VALIDATE_FUNCTION_RETURN,
                            action.functionDecl().name(),
                            action.functionDecl(),
                            work.statementAnalyzer.currentFunctionScope()
                    );
                } finally {
                    work.statementAnalyzer.endFunction();
                }
            }
            default -> throw new IllegalArgumentException("unsupported planned action: " + action.kind());
        };
    }

    private int plannedActionCount() {
        return 5 + plannedActions.size();
    }

    private List<PlannedAction> planActions(Program program) {
        ArrayList<PlannedAction> actions = new ArrayList<>();
        for (FunctionDecl functionDecl : program.functions().stream().filter(FunctionDecl::hasBody).toList()) {
            actions.add(PlannedAction.function(functionDecl));
            for (Statement statement : functionDecl.bodyOptional().orElseThrow().statements()) {
                actions.add(PlannedAction.statement(functionDecl, statement));
                visitChildren(statement).stream()
                        .map(node -> PlannedAction.visit(functionDecl, node))
                        .forEach(actions::add);
            }
            actions.add(PlannedAction.returnCheck(functionDecl));
        }
        return List.copyOf(actions);
    }

    private String statementSubject(PlannedAction action) {
        return action.functionDecl().name() + " " + action.statement().getClass().getSimpleName();
    }

    private String nodeSubject(Object node) {
        return node.getClass().getSimpleName();
    }

    private List<Object> visitChildren(Object node) {
        ArrayList<Object> nodes = new ArrayList<>();
        appendChildNodes(node, nodes);
        return List.copyOf(nodes);
    }

    private void appendChildNodes(Object node, ArrayList<Object> nodes) {
        switch (node) {
            case BlockStmt blockStmt -> blockStmt.statements().forEach(statement -> appendVisitNode(statement, nodes));
            case VarDeclStmt varDeclStmt -> varDeclStmt.initializerOptional().ifPresent(expression -> appendVisitNode(expression, nodes));
            case ReturnStmt returnStmt -> returnStmt.expressionOptional().ifPresent(expression -> appendVisitNode(expression, nodes));
            case ExprStmt exprStmt -> appendVisitNode(exprStmt.expression(), nodes);
            case IfStmt ifStmt -> {
                appendVisitNode(ifStmt.condition(), nodes);
                appendVisitNode(ifStmt.thenBranch(), nodes);
                ifStmt.elseBranchOptional().ifPresent(statement -> appendVisitNode(statement, nodes));
            }
            case WhileStmt whileStmt -> {
                appendVisitNode(whileStmt.condition(), nodes);
                appendVisitNode(whileStmt.body(), nodes);
            }
            case ForStmt forStmt -> {
                forStmt.initializerOptional().ifPresent(statement -> appendVisitNode(statement, nodes));
                forStmt.conditionOptional().ifPresent(expression -> appendVisitNode(expression, nodes));
                forStmt.stepOptional().ifPresent(expression -> appendVisitNode(expression, nodes));
                appendVisitNode(forStmt.body(), nodes);
            }
            case AssignmentExpr assignmentExpr -> {
                appendVisitNode(assignmentExpr.target(), nodes);
                appendVisitNode(assignmentExpr.value(), nodes);
            }
            case BinaryExpr binaryExpr -> {
                appendVisitNode(binaryExpr.left(), nodes);
                appendVisitNode(binaryExpr.right(), nodes);
            }
            case GroupingExpr groupingExpr -> appendVisitNode(groupingExpr.expression(), nodes);
            case IndexExpr indexExpr -> {
                appendVisitNode(indexExpr.target(), nodes);
                appendVisitNode(indexExpr.index(), nodes);
            }
            case FieldAccessExpr fieldAccessExpr -> appendVisitNode(fieldAccessExpr.target(), nodes);
            case UnaryExpr unaryExpr -> appendVisitNode(unaryExpr.operand(), nodes);
            case CallExpr callExpr -> {
                appendVisitNode(callExpr.callee(), nodes);
                callExpr.arguments().forEach(argument -> appendVisitNode(argument, nodes));
            }
            default -> {
                // Leaf AST nodes do not enqueue additional visit actions.
            }
        }
    }

    private void appendVisitNode(Object node, ArrayList<Object> nodes) {
        nodes.add(node);
        appendChildNodes(node, nodes);
    }

    private Scope innermostScopeFor(minic.source.SourceRange range, Scope root) {
        Scope best = root;
        for (Scope child : root.children()) {
            if (contains(child, range)) {
                best = innermostScopeFor(range, child);
            }
        }
        return best;
    }

    private boolean contains(Scope scope, minic.source.SourceRange range) {
        return scope.range()
                .filter(scopeRange -> scopeRange.sourceFile().equals(range.sourceFile())
                        && scopeRange.startOffset() <= range.startOffset()
                        && scopeRange.endOffset() >= range.endOffset())
                .isPresent();
    }

    private minic.source.SourceRange rangeOf(Object node) {
        return switch (node) {
            case Statement statement -> statement.range();
            case Expression expression -> expression.range();
            case FunctionDecl functionDecl -> functionDecl.range();
            case Program program -> program.range();
            default -> throw new IllegalArgumentException("unsupported AST node: " + node.getClass().getSimpleName());
        };
    }

    /**
     * Semantic 阶段输入数据。
     *
     * @param program AST 程序
     */
    public record Input(Program program) implements CompilerStageInput {
        /**
         * 创建输入数据。
         *
         * @param program AST 程序
         */
        public Input {
            Objects.requireNonNull(program, "program");
        }
    }

    /**
     * Semantic 阶段内部工作数据。
     */
    public static final class Work implements CompilerStageWork {
        private final SemanticReporter reporter;
        private final Scope globalScope;
        private final Map<Expression, MiniType> expressionTypes;
        private final StructRegistry structRegistry;
        private final FunctionRegistry functionRegistry;
        private final StatementSemanticAnalyzer statementAnalyzer;
        private Map<String, StructLayout> structLayouts = Map.of();

        private Work(
                SemanticReporter reporter,
                Scope globalScope,
                Map<Expression, MiniType> expressionTypes,
                StructRegistry structRegistry,
                FunctionRegistry functionRegistry,
                StatementSemanticAnalyzer statementAnalyzer
        ) {
            this.reporter = reporter;
            this.globalScope = globalScope;
            this.expressionTypes = expressionTypes;
            this.structRegistry = structRegistry;
            this.functionRegistry = functionRegistry;
            this.statementAnalyzer = statementAnalyzer;
        }

        /**
         * 返回全局作用域。
         *
         * @return 全局作用域
         */
        public Scope globalScope() {
            return globalScope;
        }

        /**
         * 返回已记录表达式类型数量。
         *
         * @return 表达式类型数量
         */
        public int expressionTypeCount() {
            return expressionTypes.size();
        }
    }

    private record PlannedAction(SemanticActionKind kind, FunctionDecl functionDecl, Statement statement, Object astNode) {
        private PlannedAction {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(functionDecl, "functionDecl");
        }

        static PlannedAction function(FunctionDecl functionDecl) {
            return new PlannedAction(SemanticActionKind.ANALYZE_FUNCTION_BODY, functionDecl, null, null);
        }

        static PlannedAction statement(FunctionDecl functionDecl, Statement statement) {
            return new PlannedAction(
                    SemanticActionKind.ANALYZE_STATEMENT,
                    functionDecl,
                    Objects.requireNonNull(statement, "statement"),
                    null
            );
        }

        static PlannedAction visit(FunctionDecl functionDecl, Object astNode) {
            return new PlannedAction(
                    SemanticActionKind.VISIT_AST_NODE,
                    functionDecl,
                    null,
                    Objects.requireNonNull(astNode, "astNode")
            );
        }

        static PlannedAction returnCheck(FunctionDecl functionDecl) {
            return new PlannedAction(SemanticActionKind.VALIDATE_FUNCTION_RETURN, functionDecl, null, null);
        }
    }

    /**
     * Semantic 阶段输出数据。
     *
     * @param semanticResult 语义结果
     */
    public record Output(SemanticResult semanticResult) implements CompilerStageOutput {
        /**
         * 创建输出数据。
         *
         * @param semanticResult 语义结果
         */
        public Output {
            Objects.requireNonNull(semanticResult, "semanticResult");
        }
    }
}
