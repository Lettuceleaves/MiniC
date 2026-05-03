package minic.runtime.step;

import minic.compiler.codegen.AssemblySource;
import minic.compiler.toolchain.Toolchain;
import minic.compiler.toolchain.ToolchainResult;
import minic.compiler.toolchain.WindowsMsvcToolchain;
import minic.source.SourceFile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Toolchain 阶段统一兼容层适配器。
 */
public final class ToolchainStageStepper implements StageStepper {
    private final SourceFile sourceFile;
    private final AssemblySource assemblySource;
    private final Toolchain toolchain;
    private ToolchainResult result;
    private StepResult lastResult;
    private boolean completed;

    /**
     * 创建默认 no-op Toolchain 阶段适配器。
     *
     * @param sourceFile 源码文件
     * @param assemblySource 汇编输出
     */
    public ToolchainStageStepper(SourceFile sourceFile, AssemblySource assemblySource) {
        this(sourceFile, assemblySource, new WindowsMsvcToolchain());
    }

    /**
     * 创建 Toolchain 阶段适配器。
     *
     * @param sourceFile 源码文件
     * @param assemblySource 汇编输出
     * @param toolchain 工具链
     */
    public ToolchainStageStepper(SourceFile sourceFile, AssemblySource assemblySource, Toolchain toolchain) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.assemblySource = Objects.requireNonNull(assemblySource, "assemblySource");
        this.toolchain = Objects.requireNonNull(toolchain, "toolchain");
        lastResult = StepResult.advanced(CompileStage.TOOLCHAIN, "Toolchain 待开始", "等待执行工具链。");
    }

    @Override
    public CompileStage stage() {
        return CompileStage.TOOLCHAIN;
    }

    @Override
    public boolean canNext() {
        return !completed;
    }

    @Override
    public StepResult next() {
        if (completed) {
            lastResult = StepResult.cannotAdvance(CompileStage.TOOLCHAIN, "Toolchain 已完成", "没有更多工具链步骤。");
            return lastResult;
        }
        result = toolchain.buildExecutable(sourceFile, assemblySource, Path.of("build", "minic"), artifactName());
        completed = true;
        if (!result.diagnostics().isEmpty()) {
            lastResult = StepResult.failed(
                    CompileStage.TOOLCHAIN,
                    "Toolchain 诊断",
                    result.diagnostics().getLast().message(),
                    result.diagnostics()
            );
            return lastResult;
        }
        lastResult = StepResult.stageCompleted(CompileStage.TOOLCHAIN, "Toolchain 完成", toolchainSummary().getLast());
        return lastResult;
    }

    @Override
    public CurrentStepState snapshot() {
        return new CurrentStepState(
                sourceFile.path(),
                CompileStage.TOOLCHAIN,
                completed ? 1 : 0,
                completed ? 1 : 0,
                PlaybackMode.PAUSED,
                Duration.ofSeconds(1),
                null,
                lastResult.title(),
                lastResult.description(),
                lastResult.diagnostics(),
                new StepCapabilities(canNext(), false, canNext(), canNext(), true, false)
        );
    }

    @Override
    public StageStepData data() {
        return new StageStepData(
                CompileStage.TOOLCHAIN,
                new StageProgress(completed ? 1 : 0, 1, completed),
                List.of("target=" + assemblySource.targetPlatform(), "entry=" + assemblySource.entrySymbol()),
                result == null ? "" : toolchainSummary().getLast(),
                result == null ? List.of() : toolchainSummary(),
                result == null ? List.of() : result.diagnostics()
        );
    }

    /**
     * 返回工具链结果。
     *
     * @return 工具链结果
     */
    public ToolchainResult result() {
        return result == null ? ToolchainResult.notRun() : result;
    }

    private String artifactName() {
        String fileName = Path.of(sourceFile.path()).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        String sanitized = baseName.replaceAll("[^A-Za-z0-9_$-]", "_");
        return sanitized.isBlank() ? "a" : sanitized;
    }

    private List<String> toolchainSummary() {
        ArrayList<String> summary = new ArrayList<>();
        if (result == null) {
            return List.of();
        }
        result.assemblyPathOptional().ifPresent(path -> summary.add("assembly " + path));
        result.objectPathOptional().ifPresent(path -> summary.add("object " + path));
        result.executableArtifactOptional().ifPresent(artifact -> summary.add("executable " + artifact.path()));
        if (summary.isEmpty()) {
            summary.add("toolchain not run");
        }
        return summary;
    }
}
