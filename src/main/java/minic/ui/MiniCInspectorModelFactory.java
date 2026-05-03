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
            return "stage: pending\nglobalStep: 0\nstageStep: 0\nframeInterval: 0ms\ndiagnostics: 0";
        }
        return "stage: " + state.currentStage()
                + "\nglobalStep: " + state.globalStepIndex()
                + "\nstageStep: " + state.stageStepIndex()
                + "\nplayback: " + state.playbackMode()
                + "\nframeInterval: " + state.frameIntervalMillis() + "ms"
                + "\ndiagnostics: " + state.diagnostics().size();
    }

    private String currentItem(UiStageDataDto stageData) {
        if (stageData == null) {
            return "等待开始观测会话。";
        }
        return stageData.currentItem().isBlank() ? stageData.stage() + " 暂无当前项" : stageData.currentItem();
    }

    private String accumulatedOutput(UiGlobalDataDto globalData) {
        if (globalData == null) {
            return "tokens: 0\nast: 0\nsemantic: 0\nir: 0\nassembly: 0\nartifact: 0";
        }
        return "tokens: " + globalData.tokenSummary().size()
                + "\nast: " + globalData.astSummary().size()
                + "\nsemantic: " + globalData.semanticSummary().size()
                + "\nir: " + globalData.irSummary().size()
                + "\nassembly: " + globalData.assemblySummary().size()
                + "\nartifact: " + globalData.artifactSummary().size();
    }
}
