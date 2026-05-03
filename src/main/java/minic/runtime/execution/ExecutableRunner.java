package minic.runtime.execution;

import minic.compiler.toolchain.ExecutableArtifact;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 运行可执行产物并捕获输出。
 */
public final class ExecutableRunner implements ExecutableRunService {
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
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(standardInput, "standardInput");
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
            return failed(sourceFile, "读取运行输出失败：" + exception.getCause().getMessage());
        }
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
                        DiagnosticSeverity.ERROR,
                        message,
                        new SourceRange(sourceFile, 0, 0)
                ))
        );
    }
}
