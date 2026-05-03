package minic.ui;

import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiStageDataDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据 UI API DTO 生成 Visual Pane 展示项。
 */
public final class MiniCVisualModelFactory {
    /**
     * 创建当前阶段可视化项。
     *
     * @param stageData 当前阶段数据
     * @param globalData 全局数据
     * @return 可视化项
     */
    public List<MiniCVisualItem> create(UiStageDataDto stageData, UiGlobalDataDto globalData) {
        if (stageData == null) {
            return List.of(new MiniCVisualItem("等待开始观测会话", true));
        }
        List<String> source = sourceFor(stageData.stage(), stageData, globalData);
        ArrayList<MiniCVisualItem> items = new ArrayList<>();
        if (!stageData.currentItem().isBlank()) {
            items.add(new MiniCVisualItem(stageData.currentItem(), true));
        }
        source.stream()
                .limit(24)
                .map(item -> new MiniCVisualItem(item, false))
                .forEach(items::add);
        if (items.isEmpty()) {
            items.add(new MiniCVisualItem(stageData.stage() + " 暂无输出", true));
        }
        return List.copyOf(items);
    }

    private List<String> sourceFor(String stage, UiStageDataDto stageData, UiGlobalDataDto globalData) {
        if (globalData == null) {
            return stageData.accumulatedOutput();
        }
        return switch (stage) {
            case "lexer" -> globalData.tokenSummary();
            case "parser" -> globalData.astSummary();
            case "semantic" -> globalData.semanticSummary();
            case "ir" -> globalData.irSummary();
            case "codegen" -> globalData.assemblySummary();
            default -> stageData.accumulatedOutput();
        };
    }
}
