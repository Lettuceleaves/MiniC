package minic.uilocal;

import minic.uiapi.UiDiagnosticDto;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiRealtimeAnalysisDto;
import minic.uiapi.UiStageDataDto;

import java.util.List;

/**
 * 根据 UI API DTO 生成底部面板内容。
 */
public final class MiniCBottomPanelModelFactory {
    /**
     * 创建底部面板模型。
     *
     * @param stageData 当前阶段数据
     * @param globalData 全局数据
     * @return 底部面板模型
     */
    public MiniCBottomPanelModel create(UiStageDataDto stageData, UiGlobalDataDto globalData) {
        return create(stageData, globalData, null);
    }

    /**
     * 创建底部面板模型。
     *
     * @param stageData 当前阶段数据
     * @param globalData 全局数据
     * @param realtimeAnalysis 实时分析数据
     * @return 底部面板模型
     */
    public MiniCBottomPanelModel create(
            UiStageDataDto stageData,
            UiGlobalDataDto globalData,
            UiRealtimeAnalysisDto realtimeAnalysis
    ) {
        List<String> problems = problems(stageData, globalData, realtimeAnalysis);
        List<String> output = stageData == null
                ? List.of("等待开始观测会话")
                : stageData.accumulatedOutput().isEmpty() ? List.of(stageData.currentItem()) : stageData.accumulatedOutput();
        List<String> terminal = terminal(stageData);
        return new MiniCBottomPanelModel(problems, output, terminal);
    }

    private List<String> problems(
            UiStageDataDto stageData,
            UiGlobalDataDto globalData,
            UiRealtimeAnalysisDto realtimeAnalysis
    ) {
        if (realtimeAnalysis != null) {
            if (realtimeAnalysis.diagnostics().isEmpty()) {
                return List.of("OK  实时分析通过");
            }
            return realtimeAnalysis.diagnostics().stream()
                    .map(diagnostic -> diagnostic.severity() + "  " + diagnostic.code() + "  " + diagnostic.message())
                    .toList();
        }
        List<UiDiagnosticDto> diagnostics;
        if (globalData != null && !globalData.diagnostics().isEmpty()) {
            diagnostics = globalData.diagnostics();
        } else if (stageData != null && !stageData.diagnostics().isEmpty()) {
            diagnostics = stageData.diagnostics();
        } else {
            return List.of("OK  暂无 diagnostics");
        }
        return diagnostics.stream()
                .map(diagnostic -> diagnostic.severity() + "  " + diagnostic.code() + "  " + diagnostic.message())
                .toList();
    }

    private List<String> terminal(UiStageDataDto stageData) {
        if (stageData == null) {
            return List.of("PS> minic observe <source.mc>");
        }
        return List.of(
                "PS> minic observe --stage " + stageData.stage(),
                stageData.stage() + "[" + stageData.completedSteps() + "/" + stageData.totalSteps() + "] " + stageData.currentItem()
        );
    }
}
