package minic.runtime.step;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.decl.StructDecl;
import minic.compiler.semantic.SemanticAction;
import minic.compiler.semantic.SemanticStepState;
import minic.diagnostics.Diagnostic;
import minic.source.SourceRange;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Semantic 阶段统一兼容层适配器。
 */
public final class SemanticStageStepper implements StageStepper {
    private final SemanticStepState semanticState;
    private final Program program;
    private long globalStepIndex;
    private StepResult lastResult;

    /**
     * 创建 Semantic 阶段适配器。
     *
     * @param program AST 程序
     */
    public SemanticStageStepper(Program program) {
        this.program = Objects.requireNonNull(program, "program");
        semanticState = new SemanticStepState(program);
        lastResult = StepResult.advanced(CompileStage.SEMANTIC, "语义分析待开始", "等待执行第一个语义动作。");
    }

    @Override
    public CompileStage stage() {
        return CompileStage.SEMANTIC;
    }

    @Override
    public boolean canNext() {
        return semanticState.canNext();
    }

    @Override
    public StepResult next() {
        if (!semanticState.canNext()) {
            lastResult = StepResult.cannotAdvance(CompileStage.SEMANTIC, "语义分析已完成", "没有更多语义动作。");
            return lastResult;
        }
        SemanticAction action = semanticState.next();
        globalStepIndex++;
        if (action.diagnosticOptional().isPresent()) {
            Diagnostic diagnostic = action.diagnosticOptional().orElseThrow();
            lastResult = StepResult.failed(
                    CompileStage.SEMANTIC,
                    "语义诊断",
                    actionSummary(action),
                    List.of(diagnostic)
            );
            return lastResult;
        }
        if (!semanticState.canNext()) {
            lastResult = StepResult.stageCompleted(CompileStage.SEMANTIC, "语义分析完成", actionSummary(action));
            return lastResult;
        }
        lastResult = StepResult.advanced(CompileStage.SEMANTIC, "执行语义动作", actionSummary(action));
        return lastResult;
    }

    @Override
    public CurrentStepState snapshot() {
        return new CurrentStepState(
                program.range().sourceFile().path(),
                CompileStage.SEMANTIC,
                globalStepIndex,
                semanticState.snapshot().progress().completedSteps(),
                PlaybackMode.PAUSED,
                Duration.ofSeconds(1),
                currentRange(),
                lastResult.title(),
                lastResult.description(),
                lastResult.diagnostics(),
                new StepCapabilities(semanticState.canNext(), false, semanticState.canNext(), semanticState.canNext(), true, false)
        );
    }

    @Override
    public StageStepData data() {
        return new StageStepData(
                CompileStage.SEMANTIC,
                semanticState.snapshot().progress(),
                List.of(
                        "structs=" + program.structs().size(),
                        "functions=" + program.functions().size(),
                        "bodyFunctions=" + program.functions().stream().filter(FunctionDecl::hasBody).count()
                ),
                semanticState.currentAction().map(SemanticStageStepper::actionSummary).orElse(""),
                semanticSummary(),
                semanticState.diagnostics()
        );
    }

    /**
     * 返回底层 semantic 状态，供后续阶段读取语义结果。
     *
     * @return semantic 状态
     */
    public SemanticStepState semanticState() {
        return semanticState;
    }

    /**
     * 返回 semantic 阶段输入 AST。
     *
     * @return Program AST
     */
    public Program program() {
        return program;
    }

    private SourceRange currentRange() {
        return semanticState.currentAction()
                .flatMap(SemanticAction::diagnosticOptional)
                .map(Diagnostic::range)
                .orElseGet(() -> semanticState.currentAction()
                        .map(SemanticAction::subject)
                        .flatMap(this::functionRangeBySubject)
                        .orElse(program.range()));
    }

    private java.util.Optional<SourceRange> functionRangeBySubject(String subject) {
        return program.functions().stream()
                .filter(function -> function.name().equals(subject))
                .map(FunctionDecl::range)
                .findFirst();
    }

    private List<String> semanticSummary() {
        ArrayList<String> summary = new ArrayList<>();
        summary.add("expressionTypes=" + semanticState.work().expressionTypeCount());
        for (StructDecl structDecl : program.structs()) {
            semanticState.work().globalScope().resolveLocal(structDecl.name())
                    .ifPresent(symbol -> summary.add("symbol " + symbol.name() + " " + symbol.kind()));
        }
        for (FunctionDecl functionDecl : program.functions()) {
            semanticState.work().globalScope().resolveLocal(functionDecl.name())
                    .ifPresent(symbol -> summary.add("symbol " + symbol.name() + " " + symbol.kind()));
        }
        return summary;
    }

    private static String actionSummary(SemanticAction action) {
        return action.kind() + " " + action.subject();
    }
}
