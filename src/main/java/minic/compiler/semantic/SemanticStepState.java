package minic.compiler.semantic;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.expr.Expression;
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
            default -> analyzeFunctionAction(actionIndex - 5);
        };
    }

    private SemanticAction analyzeFunctionAction(int bodyIndex) {
        FunctionDecl functionDecl = bodyFunctions().get(bodyIndex);
        work.statementAnalyzer.analyzeFunction(functionDecl);
        return SemanticAction.of(SemanticActionKind.ANALYZE_FUNCTION_BODY, functionDecl.name());
    }

    private int plannedActionCount() {
        return 5 + bodyFunctions().size();
    }

    private List<FunctionDecl> bodyFunctions() {
        if (work.bodyFunctions == null) {
            work.bodyFunctions = input.program.functions().stream()
                    .filter(FunctionDecl::hasBody)
                    .toList();
        }
        return work.bodyFunctions;
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
        private List<FunctionDecl> bodyFunctions;

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
