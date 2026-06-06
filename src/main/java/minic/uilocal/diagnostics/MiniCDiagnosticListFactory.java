package minic.uilocal;

import minic.uiapi.UiDiagnosticDto;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiRealtimeAnalysisDto;
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
        return create(stageData, globalData, null);
    }

    /**
     * 创建诊断项。
     *
     * @param stageData 当前阶段数据
     * @param globalData 全局数据
     * @param realtimeAnalysis 实时分析数据
     * @return 诊断项
     */
    public List<MiniCDiagnosticItem> create(
            UiStageDataDto stageData,
            UiGlobalDataDto globalData,
            UiRealtimeAnalysisDto realtimeAnalysis
    ) {
        if (realtimeAnalysis != null && !realtimeAnalysis.diagnostics().isEmpty()) {
            return realtimeAnalysis.diagnostics().stream()
                    .map(diagnostic -> from(diagnostic, realtimeAnalysis))
                    .toList();
        }
        List<UiDiagnosticDto> diagnostics = globalData != null && !globalData.diagnostics().isEmpty()
                ? globalData.diagnostics()
                : stageData == null ? List.of() : stageData.diagnostics();
        return diagnostics.stream().map(this::from).toList();
    }

    private MiniCDiagnosticItem from(UiDiagnosticDto diagnostic) {
        return from(diagnostic, null);
    }

    private MiniCDiagnosticItem from(UiDiagnosticDto diagnostic, UiRealtimeAnalysisDto analysis) {
        SourceLocation location = analysis == null
                ? new SourceLocation(1, Math.max(1, diagnostic.startOffset() + 1))
                : locationAt(analysis.sourceText(), diagnostic.startOffset());
        return new MiniCDiagnosticItem(
                diagnostic.code(),
                diagnostic.severity(),
                diagnostic.message(),
                new UiSourceRangeDto(diagnostic.sourceName(), diagnostic.startOffset(), diagnostic.endOffset()),
                location.line(),
                location.column()
        );
    }

    private SourceLocation locationAt(String source, int offset) {
        int safeOffset = Math.max(0, Math.min(offset, source.length()));
        int line = 1;
        int column = 1;
        for (int index = 0; index < safeOffset; index++) {
            char value = source.charAt(index);
            if (value == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new SourceLocation(line, column);
    }

    private record SourceLocation(int line, int column) {
    }
}
