package minic.compiler.semantic;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.stmt.Statement;
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
            currentAction = SemanticAction.diagnostic(currentAction.subject(), diagnostic);
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
                yield SemanticAction.of(SemanticActionKind.REGISTER_STRUCTS, "structs=" + input.program.structs().size());
            }
            case 1 -> {
                work.structRegistry.validateProgramTypes(input.program);
                yield SemanticAction.of(SemanticActionKind.CHECK_TYPES, "program types");
            }
            case 2 -> {
                work.structLayouts = work.reporter.diagnostics().isEmpty()
                        ? work.structRegistry.computeLayouts()
                        : Map.of();
                yield SemanticAction.of(SemanticActionKind.COMPUTE_STRUCT_LAYOUTS, "struct layouts");
            }
            case 3 -> {
                work.functionRegistry.defineFunctions(input.program);
                yield SemanticAction.of(SemanticActionKind.REGISTER_FUNCTIONS, "functions=" + input.program.functions().size());
            }
            case 4 -> {
                work.functionRegistry.validateMain(input.program);
                yield SemanticAction.of(SemanticActionKind.VALIDATE_MAIN, "main");
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
                yield SemanticAction.of(SemanticActionKind.ANALYZE_FUNCTION_BODY, action.functionDecl().name());
            }
            case ANALYZE_STATEMENT -> {
                work.statementAnalyzer.analyzeCurrentFunctionTopLevelStatement(action.statement());
                yield SemanticAction.of(SemanticActionKind.ANALYZE_STATEMENT, statementSubject(action));
            }
            case VALIDATE_FUNCTION_RETURN -> {
                try {
                    work.statementAnalyzer.validateCurrentFunctionReturn();
                    yield SemanticAction.of(SemanticActionKind.VALIDATE_FUNCTION_RETURN, action.functionDecl().name());
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
            functionDecl.bodyOptional().orElseThrow().statements().stream()
                    .map(statement -> PlannedAction.statement(functionDecl, statement))
                    .forEach(actions::add);
            actions.add(PlannedAction.returnCheck(functionDecl));
        }
        return List.copyOf(actions);
    }

    private String statementSubject(PlannedAction action) {
        return action.functionDecl().name() + " " + action.statement().getClass().getSimpleName();
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

    private record PlannedAction(SemanticActionKind kind, FunctionDecl functionDecl, Statement statement) {
        private PlannedAction {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(functionDecl, "functionDecl");
        }

        static PlannedAction function(FunctionDecl functionDecl) {
            return new PlannedAction(SemanticActionKind.ANALYZE_FUNCTION_BODY, functionDecl, null);
        }

        static PlannedAction statement(FunctionDecl functionDecl, Statement statement) {
            return new PlannedAction(
                    SemanticActionKind.ANALYZE_STATEMENT,
                    functionDecl,
                    Objects.requireNonNull(statement, "statement")
            );
        }

        static PlannedAction returnCheck(FunctionDecl functionDecl) {
            return new PlannedAction(SemanticActionKind.VALIDATE_FUNCTION_RETURN, functionDecl, null);
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
