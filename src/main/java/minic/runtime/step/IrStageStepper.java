package minic.runtime.step;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ir.lowering.IrLoweringAction;
import minic.compiler.ir.lowering.IrLoweringActionKind;
import minic.compiler.ir.lowering.IrStepState;
import minic.compiler.semantic.SemanticResult;
import minic.source.SourceRange;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * IR lowering 阶段统一兼容层适配器。
 */
public final class IrStageStepper implements StageStepper {
    private final IrStepState irState;
    private final Program program;
    private long globalStepIndex;
    private StepResult lastResult;

    /**
     * 创建 IR 阶段适配器。
     *
     * @param program AST 程序
     * @param semanticResult 语义结果
     */
    public IrStageStepper(Program program, SemanticResult semanticResult) {
        this.program = Objects.requireNonNull(program, "program");
        irState = new IrStepState(program, Objects.requireNonNull(semanticResult, "semanticResult"));
        lastResult = StepResult.advanced(CompileStage.IR, "IR lowering 待开始", "等待处理第一个 IR 结构动作。");
    }

    @Override
    public CompileStage stage() {
        return CompileStage.IR;
    }

    @Override
    public boolean canNext() {
        return irState.canNext();
    }

    @Override
    public StepResult next() {
        if (!irState.canNext()) {
            lastResult = StepResult.cannotAdvance(CompileStage.IR, "IR lowering 已完成", "没有更多 IR 结构动作。");
            return lastResult;
        }
        IrLoweringAction action = irState.next();
        globalStepIndex++;
        if (action.kind() == IrLoweringActionKind.COMPLETE_MODULE) {
            lastResult = StepResult.stageCompleted(CompileStage.IR, "IR lowering 完成", actionSummary(action));
            return lastResult;
        }
        lastResult = StepResult.advanced(CompileStage.IR, "产出 IR 项", actionSummary(action));
        return lastResult;
    }

    @Override
    public CurrentStepState snapshot() {
        return new CurrentStepState(
                program.range().sourceFile().path(),
                CompileStage.IR,
                globalStepIndex,
                irState.snapshot().progress().completedSteps(),
                PlaybackMode.PAUSED,
                Duration.ofSeconds(1),
                currentRange(),
                lastResult.title(),
                lastResult.description(),
                lastResult.diagnostics(),
                new StepCapabilities(irState.canNext(), false, irState.canNext(), irState.canNext(), true, false)
        );
    }

    @Override
    public StageStepData data() {
        return new StageStepData(
                CompileStage.IR,
                irState.snapshot().progress(),
                List.of(
                        "functions=" + program.functions().size(),
                        "structLayouts=" + irState.input().structLayouts().size(),
                        "expressionTypes=" + irState.input().expressionTypes().size()
                ),
                currentItem(),
                irSummary(),
                List.of()
        );
    }

    /**
     * 返回底层 IR 状态，供后续阶段读取 IR 模块。
     *
     * @return IR 状态
     */
    public IrStepState irState() {
        return irState;
    }

    private SourceRange currentRange() {
        return irState.currentAction()
                .map(IrLoweringAction::subject)
                .flatMap(this::functionRangeBySubject)
                .orElse(program.range());
    }

    private java.util.Optional<SourceRange> functionRangeBySubject(String subject) {
        return program.functions().stream()
                .filter(function -> function.name().equals(subject))
                .map(FunctionDecl::range)
                .findFirst();
    }

    private String currentItem() {
        return irState.currentAction()
                .map(action -> actionSummary(action) + " function=" + currentFunction(action) + " block=" + currentBlock(action))
                .orElse("");
    }

    private List<String> irSummary() {
        ArrayList<String> summary = new ArrayList<>();
        irState.work().externalFunctionNames().stream()
                .map(name -> "extern " + name)
                .forEach(summary::add);
        summary.addAll(irState.work().functionSummaries());
        return summary;
    }

    private static String actionSummary(IrLoweringAction action) {
        return action.kind() + " " + action.subject();
    }

    private static String currentFunction(IrLoweringAction action) {
        return switch (action.kind()) {
            case BEGIN_FUNCTION, COMPLETE_FUNCTION -> action.subject();
            case LOWER_STATEMENT -> action.subject().split(" ", 2)[0];
            default -> "";
        };
    }

    private static String currentBlock(IrLoweringAction action) {
        return switch (action.kind()) {
            case BEGIN_FUNCTION -> "entry";
            case LOWER_STATEMENT -> "<statement>";
            case COMPLETE_FUNCTION -> "<function>";
            default -> "";
        };
    }
}
