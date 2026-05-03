package minic.ui;

import minic.uiapi.UiDiagnosticDto;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiSourceRangeDto;
import minic.uiapi.UiStageDataDto;

import java.util.List;

/**
 * 根据 UI API DTO 生成 diagnostics 列表。
 */
public final class MiniCDiagnosticListFactory {
    /**
     * 创建诊断项。
     *
     * @param stageData 当前阶段数据
     * @param globalData 全局数据
     * @return 诊断项
     */
    public List<MiniCDiagnosticItem> create(UiStageDataDto stageData, UiGlobalDataDto globalData) {
        List<UiDiagnosticDto> diagnostics = globalData != null && !globalData.diagnostics().isEmpty()
                ? globalData.diagnostics()
                : stageData == null ? List.of() : stageData.diagnostics();
        return diagnostics.stream().map(this::from).toList();
    }

    private MiniCDiagnosticItem from(UiDiagnosticDto diagnostic) {
        return new MiniCDiagnosticItem(
                diagnostic.code(),
                diagnostic.severity(),
                diagnostic.message(),
                new UiSourceRangeDto(diagnostic.sourceName(), diagnostic.startOffset(), diagnostic.endOffset())
        );
    }
}
