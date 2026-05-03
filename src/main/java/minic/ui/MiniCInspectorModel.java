package minic.ui;

import java.util.Objects;

/**
 * Inspector 面板展示数据。
 *
 * @param currentState 当前状态文本
 * @param currentItem 当前项文本
 * @param accumulatedOutput 累计输出文本
 */
public record MiniCInspectorModel(
        String currentState,
        String currentItem,
        String accumulatedOutput
) {
    public MiniCInspectorModel {
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(currentItem, "currentItem");
        Objects.requireNonNull(accumulatedOutput, "accumulatedOutput");
    }
}
