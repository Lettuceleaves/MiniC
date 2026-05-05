package minic.runtime.step;

import minic.compiler.codegen.windows.WindowsX64AssemblyLine;
import minic.compiler.codegen.windows.WindowsX64AssemblyLineKind;
import minic.compiler.codegen.windows.WindowsX64CodegenStepState;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrModule;
import minic.source.SourceRange;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Codegen 阶段统一兼容层适配器。
 */
public final class CodegenStageStepper implements StageStepper {
    private final WindowsX64CodegenStepState codegenState;
    private final IrModule module;
    private long globalStepIndex;
    private StepResult lastResult;

    /**
     * 创建 Codegen 阶段适配器。
     *
     * @param module IR 模块
     */
    public CodegenStageStepper(IrModule module) {
        this.module = Objects.requireNonNull(module, "module");
        codegenState = new WindowsX64CodegenStepState(module);
        lastResult = StepResult.advanced(CompileStage.CODEGEN, "Codegen 待开始", "等待产出第一行汇编。");
    }

    @Override
    public CompileStage stage() {
        return CompileStage.CODEGEN;
    }

    @Override
    public boolean canNext() {
        return codegenState.canNext();
    }

    @Override
    public StepResult next() {
        if (!codegenState.canNext()) {
            lastResult = StepResult.cannotAdvance(CompileStage.CODEGEN, "Codegen 已完成", "没有更多汇编行。");
            return lastResult;
        }
        WindowsX64AssemblyLine line = codegenState.next();
        globalStepIndex++;
        if (line.kind() == WindowsX64AssemblyLineKind.END) {
            lastResult = StepResult.stageCompleted(CompileStage.CODEGEN, "Codegen 完成", lineSummary(line));
            return lastResult;
        }
        lastResult = StepResult.advanced(CompileStage.CODEGEN, "产出汇编行", lineSummary(line));
        return lastResult;
    }

    @Override
    public CurrentStepState snapshot() {
        return new CurrentStepState(
                sourceName(),
                CompileStage.CODEGEN,
                globalStepIndex,
                codegenState.snapshot().progress().completedSteps(),
                PlaybackMode.PAUSED,
                Duration.ofSeconds(1),
                currentRange(),
                lastResult.title(),
                lastResult.description(),
                lastResult.diagnostics(),
                new StepCapabilities(codegenState.canNext(), false, codegenState.canNext(), codegenState.canNext(), true, false)
        );
    }

    @Override
    public StageStepData data() {
        return new StageStepData(
                CompileStage.CODEGEN,
                codegenState.snapshot().progress(),
                List.of(
                        "functions=" + module.functions().size(),
                        "strings=" + module.stringData().size(),
                        "externs=" + module.externalFunctionNames().size()
                ),
                currentItem(),
                codegenState.work().assemblyLineSummaries(),
                List.of()
        );
    }

    /**
     * 返回底层 codegen 状态，供后续阶段读取汇编输出。
     *
     * @return codegen 状态
     */
    public WindowsX64CodegenStepState codegenState() {
        return codegenState;
    }

    /**
     * 返回 codegen 输入 IR 模块。
     *
     * @return IR 模块
     */
    public IrModule module() {
        return module;
    }

    private String currentItem() {
        return codegenState.currentLine()
                .map(line -> lineSummary(line)
                        + " section=" + codegenState.work().currentSection()
                        + " label=" + line.subject())
                .orElse("");
    }

    private SourceRange currentRange() {
        return codegenState.currentLine()
                .map(WindowsX64AssemblyLine::subject)
                .flatMap(this::functionRangeBySubject)
                .orElseGet(() -> module.functions().isEmpty() ? null : module.functions().getFirst().range());
    }

    private java.util.Optional<SourceRange> functionRangeBySubject(String subject) {
        return module.functions().stream()
                .filter(function -> function.name().equals(subject))
                .map(IrFunction::range)
                .findFirst();
    }

    private String sourceName() {
        return module.functions().isEmpty() ? "<generated>" : module.functions().getFirst().range().sourceFile().path();
    }

    private static String lineSummary(WindowsX64AssemblyLine line) {
        return line.kind() + " " + line.subject() + " " + line.text();
    }
}
