package minic.uilocal;

import minic.uiapi.UiCurrentStateDto;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiInspectorModelDto;
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
        UiInspectorModelDto model = UiInspectorModelDto.from(state, stageData, globalData);
        return new MiniCInspectorModel(model.currentState(), model.currentItem(), model.accumulatedOutput());
    }
}
