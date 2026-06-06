package minic.uilocal;

import java.util.Objects;

/**
 * UI 阶段列表中的单个阶段展示数据。
 *
 * @param id 阶段 ID
 * @param title 展示标题
 * @param state 阶段状态
 * @param detail 细节文本
 * @param progressPercent 进度百分比
 */
public record MiniCStageView(
        String id,
        String title,
        String state,
        String detail,
        int progressPercent
) {
    public MiniCStageView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("progressPercent must be between 0 and 100");
        }
    }
}
