package minic.uiapi;

import java.util.Objects;

/**
 * UI API 暴露的 Inspector 汇总展示语义。
 *
 * @param currentState 当前状态文本
 * @param currentItem 当前项文本
 * @param accumulatedOutput 累计输出文本
 */
public record UiInspectorModelDto(
        String currentState,
        String currentItem,
        String accumulatedOutput
) {
    public UiInspectorModelDto {
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(currentItem, "currentItem");
        Objects.requireNonNull(accumulatedOutput, "accumulatedOutput");
    }

    /**
     * 等待会话启动时的默认 Inspector 语义。
     *
     * @return 默认 Inspector 模型
     */
    public static UiInspectorModelDto initial() {
        return from(null, null, null);
    }

    /**
     * 根据 UIAPI 状态 DTO 构建 Inspector 汇总。
     *
     * @param state 当前状态
     * @param stageData 当前阶段数据
     * @param globalData 全局数据
     * @return Inspector 模型
     */
    public static UiInspectorModelDto from(
            UiCurrentStateDto state,
            UiStageDataDto stageData,
            UiGlobalDataDto globalData
    ) {
        return new UiInspectorModelDto(currentState(state), currentItem(stageData), accumulatedOutput(globalData));
    }

    private static String currentState(UiCurrentStateDto state) {
        if (state == null) {
            return "阶段: 等待中\n全局步: 0\n阶段步: 0\n帧间隔: 0ms\n诊断: 0";
        }
        return "阶段: " + stageName(state.currentStage())
                + "\n全局步: " + state.globalStepIndex()
                + "\n阶段步: " + state.stageStepIndex()
                + "\n播放: " + playbackMode(state.playbackMode())
                + "\n帧间隔: " + state.frameIntervalMillis() + "ms"
                + "\n诊断: " + state.diagnostics().size();
    }

    private static String currentItem(UiStageDataDto stageData) {
        if (stageData == null) {
            return "等待开始观测会话。";
        }
        return stageData.currentItem().isBlank() ? stageName(stageData.stage()) + " 暂无当前项" : stageData.currentItem();
    }

    private static String accumulatedOutput(UiGlobalDataDto globalData) {
        if (globalData == null) {
            return "预编译: 0\ntoken: 0\nAST: 0\n语义: 0\nIR: 0\n汇编: 0\n产物: 0";
        }
        return "预编译: " + globalData.preprocessSummary().size()
                + "\ntoken: " + globalData.tokenSummary().size()
                + "\nAST: " + globalData.astSummary().size()
                + "\n语义: " + globalData.semanticSummary().size()
                + "\nIR: " + globalData.irSummary().size()
                + "\n汇编: " + globalData.assemblySummary().size()
                + "\n产物: " + globalData.artifactSummary().size();
    }

    private static String stageName(String stage) {
        return switch (stage) {
            case "source" -> "源码";
            case "preprocess" -> "预编译";
            case "lexer" -> "词法分析";
            case "parser" -> "语法分析";
            case "semantic" -> "语义分析";
            case "ir" -> "IR 降级";
            case "codegen" -> "代码生成";
            case "toolchain" -> "工具链";
            case "execution" -> "执行";
            default -> stage;
        };
    }

    private static String playbackMode(String mode) {
        return switch (mode) {
            case "PLAYING" -> "播放中";
            case "FAST_PLAYING" -> "快速播放";
            case "PAUSED" -> "暂停";
            default -> mode;
        };
    }
}
