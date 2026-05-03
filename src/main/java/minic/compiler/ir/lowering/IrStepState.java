package minic.compiler.ir.lowering;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrModule;
import minic.compiler.ir.model.IrType;
import minic.compiler.semantic.SemanticResult;
import minic.compiler.semantic.StructLayout;
import minic.compiler.stage.CompilerStageInput;
import minic.compiler.stage.CompilerStageOutput;
import minic.compiler.stage.CompilerStageResult;
import minic.compiler.stage.CompilerStageSnapshot;
import minic.compiler.stage.CompilerStageState;
import minic.compiler.stage.CompilerStageStatus;
import minic.compiler.stage.CompilerStageWork;
import minic.compiler.type.MiniType;
import minic.runtime.step.CompileStage;
import minic.runtime.step.StageProgress;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 可正向步进的 IR lowering 状态。
 */
public final class IrStepState implements CompilerStageState<IrStepState.Input, IrStepState.Work, IrStepState.Output> {
    private final Input input;
    private final Work work;
    private IrLoweringAction currentAction;
    private int nextFunctionIndex;
    private IncrementalIrFunctionLowerer currentFunctionLowerer;
    private FunctionDecl currentFunction;
    private int completedStepCount;
    private boolean moduleCompleted;

    /**
     * 创建 IR lowering 状态。
     *
     * @param program AST 程序
     * @param semanticResult 语义结果
     */
    public IrStepState(Program program, SemanticResult semanticResult) {
        this(program, semanticResult.structLayouts(), semanticResult.expressionTypes());
    }

    /**
     * 创建 IR lowering 状态。
     *
     * @param program AST 程序
     * @param structLayouts 结构体布局
     * @param expressionTypes 表达式类型
     */
    public IrStepState(
            Program program,
            Map<String, StructLayout> structLayouts,
            Map<Expression, MiniType> expressionTypes
    ) {
        input = new Input(program, structLayouts, expressionTypes);
        work = new Work(collectFunctionSignatures(program));
    }

    @Override
    public CompileStage stage() {
        return CompileStage.IR;
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
        CompilerStageStatus status = moduleCompleted
                ? CompilerStageStatus.COMPLETED
                : nextFunctionIndex == 0 ? CompilerStageStatus.NOT_STARTED : CompilerStageStatus.RUNNING;
        return new CompilerStageSnapshot(
                CompileStage.IR,
                status,
                new StageProgress(completedStepCount, plannedActionCount(), moduleCompleted),
                currentAction == null ? "" : currentAction.kind() + " " + currentAction.subject(),
                List.of()
        );
    }

    @Override
    public boolean canNext() {
        return !moduleCompleted;
    }

    /**
     * 推进一个 IR lowering 结构动作。
     *
     * @return 本步动作
     */
    public IrLoweringAction next() {
        if (!canNext()) {
            throw new IllegalStateException("ir state is already completed");
        }
        if (currentFunctionLowerer != null) {
            if (currentFunctionLowerer.hasNextStatement()) {
                currentAction = currentFunctionLowerer.lowerNextStatement();
                completedStepCount++;
                return currentAction;
            }
            work.functions.add(currentFunctionLowerer.complete());
            currentAction = new IrLoweringAction(IrLoweringActionKind.COMPLETE_FUNCTION, currentFunction.name());
            currentFunctionLowerer = null;
            currentFunction = null;
            nextFunctionIndex++;
            completedStepCount++;
            return currentAction;
        }
        if (nextFunctionIndex < input.program.functions().size()) {
            FunctionDecl function = input.program.functions().get(nextFunctionIndex);
            if (function.external()) {
                work.externalFunctionNames.add(function.name());
                currentAction = new IrLoweringAction(IrLoweringActionKind.REGISTER_EXTERNAL, function.name());
                nextFunctionIndex++;
                completedStepCount++;
                return currentAction;
            }
            if (function.hasBody()) {
                currentFunction = function;
                currentFunctionLowerer = new IncrementalIrFunctionLowerer(
                        function,
                        work.stringLiteralRegistry,
                        input.structLayouts,
                        input.expressionTypes,
                        work.functionSignatures
                );
                currentAction = currentFunctionLowerer.begin();
                completedStepCount++;
                return currentAction;
            }
            currentAction = new IrLoweringAction(IrLoweringActionKind.REGISTER_EXTERNAL, function.name());
            nextFunctionIndex++;
            completedStepCount++;
            return currentAction;
        }
        moduleCompleted = true;
        currentAction = new IrLoweringAction(IrLoweringActionKind.COMPLETE_MODULE, "module");
        completedStepCount++;
        return currentAction;
    }

    @Override
    public CompilerStageSnapshot advance() {
        next();
        return snapshot();
    }

    @Override
    public CompilerStageResult<Output> result() {
        return CompilerStageResult.success(CompileStage.IR, new Output(toIrModule()));
    }

    /**
     * 返回当前动作。
     *
     * @return 当前动作 Optional
     */
    public Optional<IrLoweringAction> currentAction() {
        return Optional.ofNullable(currentAction);
    }

    /**
     * 构建 IR 模块。
     *
     * @return IR 模块
     */
    public IrModule toIrModule() {
        while (canNext()) {
            next();
        }
        return new IrModule(work.functions, work.stringLiteralRegistry.stringData(), work.externalFunctionNames);
    }

    private static Map<String, IrFunctionSignature> collectFunctionSignatures(Program program) {
        java.util.LinkedHashMap<String, IrFunctionSignature> signatures = new java.util.LinkedHashMap<>();
        for (FunctionDecl function : program.functions()) {
            ArrayList<IrType> parameterTypes = new ArrayList<>();
            for (var parameter : function.parameters()) {
                parameterTypes.add(IrTypeLowerer.lower(parameter.type()));
            }
            signatures.put(function.name(), new IrFunctionSignature(
                    IrTypeLowerer.lower(function.returnType()),
                    parameterTypes
            ));
        }
        return signatures;
    }

    private int plannedActionCount() {
        int count = 1;
        for (FunctionDecl function : input.program.functions()) {
            if (function.hasBody()) {
                count += 2 + function.bodyOptional().orElseThrow().statements().size();
            } else {
                count++;
            }
        }
        return count;
    }

    /**
     * IR 阶段输入数据。
     *
     * @param program AST 程序
     * @param structLayouts 结构体布局
     * @param expressionTypes 表达式类型
     */
    public record Input(
            Program program,
            Map<String, StructLayout> structLayouts,
            Map<Expression, MiniType> expressionTypes
    ) implements CompilerStageInput {
        /**
         * 创建输入数据。
         *
         * @param program AST 程序
         * @param structLayouts 结构体布局
         * @param expressionTypes 表达式类型
         */
        public Input {
            Objects.requireNonNull(program, "program");
            Objects.requireNonNull(structLayouts, "structLayouts");
            Objects.requireNonNull(expressionTypes, "expressionTypes");
            structLayouts = Map.copyOf(structLayouts);
            expressionTypes = Map.copyOf(expressionTypes);
        }
    }

    /**
     * IR 阶段内部工作数据。
     */
    public static final class Work implements CompilerStageWork {
        private final ArrayList<IrFunction> functions = new ArrayList<>();
        private final LinkedHashSet<String> externalFunctionNames = new LinkedHashSet<>();
        private final StringLiteralRegistry stringLiteralRegistry = new StringLiteralRegistry();
        private final Map<String, IrFunctionSignature> functionSignatures;

        private Work(Map<String, IrFunctionSignature> functionSignatures) {
            this.functionSignatures = Map.copyOf(functionSignatures);
        }

        /**
         * 返回已产出函数数量。
         *
         * @return 函数数量
         */
        public int functionCount() {
            return functions.size();
        }

        /**
         * 返回已注册外部函数数量。
         *
         * @return 外部函数数量
         */
        public int externalFunctionCount() {
            return externalFunctionNames.size();
        }

        /**
         * 返回已产出 IR 函数摘要。
         *
         * @return IR 函数摘要
         */
        public List<String> functionSummaries() {
            return functions.stream()
                    .map(function -> function.name()
                            + " blocks=" + function.blocks().size()
                            + " instructions=" + function.blocks().stream()
                            .mapToInt(block -> block.instructions().size())
                            .sum())
                    .toList();
        }

        /**
         * 返回已注册外部函数名称。
         *
         * @return 外部函数名称
         */
        public List<String> externalFunctionNames() {
            return List.copyOf(externalFunctionNames);
        }
    }

    /**
     * IR 阶段输出数据。
     *
     * @param irModule IR 模块
     */
    public record Output(IrModule irModule) implements CompilerStageOutput {
        /**
         * 创建输出数据。
         *
         * @param irModule IR 模块
         */
        public Output {
            Objects.requireNonNull(irModule, "irModule");
        }
    }
}
