package minic.ui.run;

import minic.compiler.CompilerApi;
import minic.compiler.Diagnostic;
import minic.compiler.SourceFile;
import minic.compiler.Stage;
import minic.compiler.link.ExecutableArtifact;
import minic.compiler.link.Linker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 将一次编辑缓冲区快照编译成独立的原生产物；不启动可执行文件。 */
public final class RunCompilation {
    /**
     * 在后台线程中编译。源码路径保留用于相对 include；产物始终写入新的运行目录。
     * 已有产物和源码不被覆盖或删除，调用者负责决定生成产物的保留期限。
     */
    public Outcome compile(SourceFile source, Path outputRoot) throws IOException, InterruptedException {
        return compile(source, outputRoot, progress -> { });
    }

    /**
     * 在编译线程同步报告阶段进度：开始时为 0，仅成功完成一个阶段后递增一次。
     * 进度截至 Linker，不包括运行阶段；调用者自行将 UI 更新转发到对应事件线程。
     */
    public Outcome compile(SourceFile source, Path outputRoot, Consumer<Progress> onProgress)
            throws IOException, InterruptedException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(outputRoot, "outputRoot");
        Objects.requireNonNull(onProgress, "onProgress");
        checkInterrupted();
        Path root = Files.createDirectories(outputRoot.toAbsolutePath().normalize());
        Path outputDirectory = Files.createTempDirectory(root, "run-");
        // Each run owns its console. Initialize the native client's code pages as well
        // as the ConPTY transport, so source strings and interactive input stay UTF-8.
        CompilerApi api = new CompilerApi(source, outputDirectory, true);
        api.setResultRecording(Stage.class, false);
        Linker linker = api.stages().stream()
                .filter(Linker.class::isInstance)
                .map(Linker.class::cast)
                .findFirst().orElseThrow();

        int totalStages = api.stages().indexOf(linker) + 1;
        int completedStages = 0;
        onProgress.accept(new Progress(0, totalStages));
        checkInterrupted();
        while (api.canNext() && completedStages < totalStages) {
            Stage stage = api.currentStage().orElseThrow();
            api.nextStage(() -> Thread.currentThread().isInterrupted());
            checkInterrupted();
            if (!stage.succeeded()) break;
            onProgress.accept(new Progress(++completedStages, totalStages));
            checkInterrupted();
        }
        List<Diagnostic> diagnostics = api.stages().stream()
                .flatMap(stage -> stage.errors().stream())
                .toList();
        if (!linker.succeeded()) {
            String failedStage = api.currentStage()
                    .map(stage -> stage.getClass().getSimpleName())
                    .orElse("");
            return new Outcome(null, diagnostics, failedStage);
        }
        ExecutableArtifact artifact = linker.result().executableArtifactOptional()
                .orElseThrow(() -> new IllegalStateException("linker completed without an executable artifact"));
        return new Outcome(artifact, diagnostics, "");
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("compilation cancelled");
        }
    }

    /** 已成功完成的编译阶段数量，不保留阶段结果或编译上下文。 */
    public record Progress(int completedStages, int totalStages) {
        public Progress {
            if (totalStages <= 0 || completedStages < 0 || completedStages > totalStages) {
                throw new IllegalArgumentException("progress requires 0 <= completedStages <= totalStages and totalStages > 0");
            }
        }

        public double fraction() {
            return (double) completedStages / totalStages;
        }
    }

    /** 仅保留启动程序或展示错误所需的数据，不保留编译流水线与阶段上下文。 */
    public record Outcome(ExecutableArtifact artifact, List<Diagnostic> diagnostics, String failedStage) {
        public Outcome {
            diagnostics = List.copyOf(diagnostics);
            Objects.requireNonNull(failedStage, "failedStage");
        }

        public boolean succeeded() {
            return artifact != null;
        }
    }
}
