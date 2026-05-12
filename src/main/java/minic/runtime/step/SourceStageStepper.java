package minic.runtime.step;

import minic.source.SourceFile;
import minic.source.SourceRange;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 源码阶段 stepper，用于让编译观测会话启动后先停留在原始源码。
 */
public final class SourceStageStepper implements StageStepper {
    private final SourceFile sourceFile;

    /**
     * 创建源码阶段适配器。
     *
     * @param sourceFile 原始源码
     */
    public SourceStageStepper(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
    }

    @Override
    public CompileStage stage() {
        return CompileStage.SOURCE;
    }

    @Override
    public boolean canNext() {
        return false;
    }

    @Override
    public StepResult next() {
        return StepResult.cannotAdvance(CompileStage.SOURCE, "源码已就绪", "源码阶段不需要内部步进。");
    }

    @Override
    public CurrentStepState snapshot() {
        return new CurrentStepState(
                sourceFile.path(),
                CompileStage.SOURCE,
                0,
                0,
                PlaybackMode.PAUSED,
                Duration.ofSeconds(1),
                sourceRange(),
                "源码已加载",
                "检查源码内容后可以进入预编译阶段。",
                List.of(),
                new StepCapabilities(false, false, false, false, true, false)
        );
    }

    @Override
    public StageStepData data() {
        return new StageStepData(
                CompileStage.SOURCE,
                new StageProgress(0, 1, false),
                List.of(
                        "source=" + sourceFile.path(),
                        "length=" + sourceFile.content().length()
                ),
                "源码已加载",
                sourceFile.content().lines().map(line -> "src " + line).toList(),
                List.of()
        );
    }

    private SourceRange sourceRange() {
        if (sourceFile.content().isEmpty()) {
            return null;
        }
        return new SourceRange(sourceFile, 0, sourceFile.content().length());
    }
}
