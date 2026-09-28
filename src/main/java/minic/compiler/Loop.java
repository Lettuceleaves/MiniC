package minic.compiler;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 编译流水线的唯一循环控制器。
 *
 * <p>Loop 只持有 Stage 列表并调用当前阶段的 step。当前阶段耗尽后，成功则进入
 * 下一阶段，失败则结束流水线。Loop 不读取也不传递任何阶段内部数据。</p>
 */
public final class Loop {
    private final List<Stage> stages;
    private int currentStageIndex;
    private Stage currentStage;
    private boolean completed;
    private long stepCount;
    private SourceRange lastSourceRange;

    public Loop(List<? extends Stage> stages) {
        Objects.requireNonNull(stages, "stages");
        this.stages = List.copyOf(stages);
        if (this.stages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("stages must not contain null");
        }
        completed = this.stages.isEmpty();
        currentStage = completed ? null : this.stages.getFirst();
    }

    /** 执行到整个编译流水线结束。 */
    public void run() {
        while (canNext()) {
            step();
        }
    }

    /** 执行到当前 Stage 结束；成功时停在下一 Stage 的入口。 */
    public void runCurrentStage() {
        if (!canNext()) {
            return;
        }
        Stage stage = currentStage;
        while (canNext() && currentStage == stage) {
            step();
        }
    }

    /** 执行当前阶段的一步，并在该阶段耗尽时自动切换。 */
    public SourceRange step() {
        if (!canNext()) {
            throw new IllegalStateException("loop is already completed");
        }
        SourceRange sourceRange = currentStage.step();
        lastSourceRange = sourceRange;
        stepCount++;
        if (!currentStage.canNext()) {
            if (!currentStage.succeeded()) {
                completed = true;
                return sourceRange;
            }
            currentStageIndex++;
            if (currentStageIndex >= stages.size()) {
                completed = true;
            } else {
                currentStage = stages.get(currentStageIndex);
            }
        }
        return sourceRange;
    }

    public boolean canNext() {
        return !completed;
    }

    public boolean completed() {
        return completed;
    }

    public int currentStageIndex() {
        return currentStageIndex;
    }

    /** 返回整个流水线已经执行的单步数。 */
    public long stepCount() {
        return stepCount;
    }

    /** 返回最近一次单步对应的源码范围。 */
    public Optional<SourceRange> lastSourceRange() {
        return Optional.ofNullable(lastSourceRange);
    }

    public Optional<Stage> currentStage() {
        return Optional.ofNullable(currentStage);
    }

    public List<Stage> stages() {
        return stages;
    }
}
