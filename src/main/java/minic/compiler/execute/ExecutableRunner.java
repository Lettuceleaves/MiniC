package minic.compiler.execute;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.Stage;
import minic.compiler.link.ExecutableArtifact;
import minic.compiler.link.Linker;
import minic.diagnostics.Diagnostic;
import minic.source.SourceRange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 运行可执行产物并捕获输出的流水线阶段。
 *
 * <p>该阶段由 {@link Linker} 提供可执行产物。第一步启动进程并收集输出，
 * 最后一步为不处理数据的结束步。</p>
 */
public final class ExecutableRunner extends Stage {
    private SourceFile sourceFile;
    private Linker linkStage;
    private ExecutableArtifact artifact;
    private String standardInput = "";
    private ExecutionResult result;
    private Phase phase = Phase.RUN_PROCESS;
    private String currentOperation = "";
    private boolean initialized;
    private boolean completed;
    private long stepCount;

    /** 创建等待 {@link #begin(SourceFile, ExecutableArtifact, String)} 提供输入的执行阶段。 */
    public ExecutableRunner() {
    }

    /** 创建由 Link 阶段提供输入的执行阶段。 */
    public ExecutableRunner(SourceFile sourceFile, Linker linkStage) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.linkStage = Objects.requireNonNull(linkStage, "linkStage");
        reset();
        initialized = true;
    }

    /** 重置执行阶段并直接提供可执行产物。 */
    public void begin(SourceFile sourceFile, ExecutableArtifact artifact, String standardInput) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.linkStage = null;
        reset();
        this.artifact = Objects.requireNonNull(artifact, "artifact");
        this.standardInput = Objects.requireNonNull(standardInput, "standardInput");
        initialized = true;
    }

    /**
     * 在执行阶段开始前设置标准输入。
     */
    public void provideStandardInput(String standardInput) {
        if (stepCount != 0 || completed) {
            throw new IllegalStateException("standard input cannot change after execution starts");
        }
        this.standardInput = Objects.requireNonNull(standardInput, "standardInput");
    }

    /**
     * 运行可执行产物。
     *
     * @param sourceFile 源码文件，用于诊断 range
     * @param artifact 可执行产物
     * @return 运行结果
     */
    public ExecutionResult run(SourceFile sourceFile, ExecutableArtifact artifact) {
        return run(sourceFile, artifact, "");
    }

    /**
     * 运行可执行产物，并写入标准输入。
     *
     * @param sourceFile 源码文件，用于诊断 range
     * @param artifact 可执行产物
     * @param standardInput 标准输入文本
     * @return 运行结果
     */
    public ExecutionResult run(SourceFile sourceFile, ExecutableArtifact artifact, String standardInput) {
        begin(sourceFile, artifact, standardInput);
        new CompilerApi(List.of(this)).run();
        return result();
    }

    @Override
    public SourceRange step() {
        if (!canNext()) {
            throw new IllegalStateException("execution stage is already completed");
        }
        ensureInitialized();
        SourceRange range = phase == Phase.COMPLETE
                ? null
                : sourceFile.range(0, sourceFile.content().length());
        switch (phase) {
            case RUN_PROCESS -> {
                currentOperation = "run executable";
                result = executeProcess();
                phase = Phase.COMPLETE;
            }
            case COMPLETE -> {
                currentOperation = "";
                completed = true;
            }
        }
        stepCount++;
        return range;
    }

    @Override
    public boolean canNext() {
        return !completed;
    }

    @Override
    public boolean succeeded() {
        return completed && result != null && result.diagnostics().isEmpty();
    }

    /** 返回执行阶段的最终结果。 */
    public ExecutionResult result() {
        if (!completed || result == null) {
            throw new IllegalStateException("execution result is not ready");
        }
        return result;
    }

    public List<Diagnostic> diagnostics() {
        return result == null ? List.of() : result.diagnostics();
    }

    public String currentOperation() {
        return currentOperation;
    }

    public String standardInput() {
        return standardInput;
    }

    public long stepCount() {
        return stepCount;
    }

    public long plannedStepCount() {
        return Phase.values().length;
    }

    public Phase phase() {
        return phase;
    }

    private ExecutionResult executeProcess() {
        ProcessBuilder processBuilder = new ProcessBuilder(artifact.path().toAbsolutePath().toString());
        processBuilder.directory(artifact.path().toAbsolutePath().getParent().toFile());
        try {
            Process process = processBuilder.start();
            process.getOutputStream().write(standardInput.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            CompletableFuture<String> stdoutFuture = readUtf8(process.getInputStream());
            CompletableFuture<String> stderrFuture = readUtf8(process.getErrorStream());
            int exitCode = process.waitFor();
            String stdout = stdoutFuture.get();
            String stderr = stderrFuture.get();
            return new ExecutionResult(stdout, stderr, exitCode, List.of());
        } catch (IOException exception) {
            return failed(sourceFile, "运行可执行文件失败：" + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed(sourceFile, "运行可执行文件被中断");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return failed(sourceFile, "读取运行输出失败：" + cause.getMessage());
        }
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("execution stage has no input");
        }
        if (artifact != null) {
            return;
        }
        if (linkStage.canNext()) {
            throw new IllegalStateException("Link stage has not completed");
        }
        if (!linkStage.succeeded()) {
            throw new IllegalStateException("Link stage did not succeed");
        }
        artifact = linkStage.result().executableArtifactOptional()
                .orElseThrow(() -> new IllegalStateException("Link stage produced no executable artifact"));
    }

    private void reset() {
        artifact = null;
        standardInput = "";
        result = null;
        phase = Phase.RUN_PROCESS;
        currentOperation = "";
        completed = false;
        stepCount = 0;
    }

    private CompletableFuture<String> readUtf8(java.io.InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private ExecutionResult failed(SourceFile sourceFile, String message) {
        return new ExecutionResult(
                "",
                "",
                null,
                List.of(new Diagnostic(
                        "RUN001",
                        Diagnostic.Severity.ERROR,
                        message,
                        sourceFile.range(0, 0)
                ))
        );
    }

    public enum Phase {
        RUN_PROCESS,
        COMPLETE
    }
}
