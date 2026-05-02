package minic.compiler.stage;

import minic.diagnostics.Diagnostic;
import minic.runtime.step.CompileStage;
import minic.runtime.step.StageProgress;

import java.util.List;
import java.util.Objects;

/**
 * 编译阶段内部执行快照。
 *
 * @param stage 阶段标识
 * @param status 阶段执行状态
 * @param progress 阶段进度
 * @param currentItem 当前内部项摘要
 * @param diagnostics 当前阶段 diagnostics
 */
public record CompilerStageSnapshot(
        CompileStage stage,
        CompilerStageStatus status,
        StageProgress progress,
        String currentItem,
        List<Diagnostic> diagnostics
) {
    /**
     * 创建阶段快照，并防御性复制 diagnostics。
     *
     * @param stage 阶段标识
     * @param status 阶段执行状态
     * @param progress 阶段进度
     * @param currentItem 当前内部项摘要
     * @param diagnostics 当前阶段 diagnostics
     */
    public CompilerStageSnapshot {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(currentItem, "currentItem");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * 创建尚未开始的阶段快照。
     *
     * @param stage 阶段标识
     * @return 阶段快照
     */
    public static CompilerStageSnapshot notStarted(CompileStage stage) {
        return new CompilerStageSnapshot(
                stage,
                CompilerStageStatus.NOT_STARTED,
                StageProgress.unknownTotal(0),
                "",
                List.of()
        );
    }
}
