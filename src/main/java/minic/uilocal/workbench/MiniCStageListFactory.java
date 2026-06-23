package minic.uilocal;

import minic.uiapi.UiCurrentStateDto;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiStageDataDto;
import minic.uiapi.UiStageViewDto;

import java.util.List;

/**
 * 根据 UI API DTO 生成侧边栏阶段列表展示数据。
 */
public final class MiniCStageListFactory {
    /**
     * 生成阶段列表。
     *
     * @param currentState 当前状态
     * @param currentStageData 当前阶段数据
     * @param globalData 全局数据
     * @return 阶段展示数据
     */
    public List<MiniCStageView> create(
            UiCurrentStateDto currentState,
            UiStageDataDto currentStageData,
            UiGlobalDataDto globalData
    ) {
        return UiStageViewDto.from(currentState, currentStageData, globalData).stream()
                .map(stage -> new MiniCStageView(
                        stage.id(),
                        stage.title(),
                        stage.state(),
                        stage.detail(),
                        stage.progressPercent()
                ))
                .toList();
    }
}
