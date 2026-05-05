package minic.ui;

import minic.uiapi.UiCurrentStateDto;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiStageDataDto;

/**
 * 根据 UI API DTO 生成 Inspector 展示数据。
 */
public final class MiniCInspectorModelFactory {
    /**
     * 创建 Inspector 模型。
     *
     * @param state 当前状态
     * @param stageData 当前阶段数据
     * @param globalData 全局数据
     * @return Inspector 模型
     */
    public MiniCInspectorModel create(
            UiCurrentStateDto state,
            UiStageDataDto stageData,
            UiGlobalDataDto globalData
    ) {
        return new MiniCInspectorModel(currentState(state), currentItem(stageData), accumulatedOutput(globalData));
    }

    private String currentState(UiCurrentStateDto state) {
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

    private String currentItem(UiStageDataDto stageData) {
        if (stageData == null) {
            return "等待开始观测会话。";
        }
        return stageData.currentItem().isBlank() ? stageName(stageData.stage()) + " 暂无当前项" : stageData.currentItem();
    }

    private String accumulatedOutput(UiGlobalDataDto globalData) {
        if (globalData == null) {
            return "token: 0\nAST: 0\n语义: 0\nIR: 0\n汇编: 0\n产物: 0";
        }
        return "token: " + globalData.tokenSummary().size()
                + "\nAST: " + globalData.astSummary().size()
                + "\n语义: " + globalData.semanticSummary().size()
                + "\nIR: " + globalData.irSummary().size()
                + "\n汇编: " + globalData.assemblySummary().size()
                + "\n产物: " + globalData.artifactSummary().size();
    }

    private String stageName(String stage) {
        return switch (stage) {
            case "source" -> "源码";
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

    private String playbackMode(String mode) {
        return switch (mode) {
            case "PLAYING" -> "播放中";
            case "FAST_PLAYING" -> "快速播放";
            case "PAUSED" -> "暂停";
            default -> mode;
        };
    }
}
