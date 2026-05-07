package minic.ui;

import minic.uiapi.UiCurrentStateDto;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiStageDataDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据 UI API DTO 生成侧边栏阶段列表展示数据。
 */
public final class MiniCStageListFactory {
    private static final List<StageInfo> STAGES = List.of(
            new StageInfo("source", "源码"),
            new StageInfo("preprocess", "预编译"),
            new StageInfo("lexer", "词法分析"),
            new StageInfo("parser", "语法分析"),
            new StageInfo("semantic", "语义分析"),
            new StageInfo("ir", "IR 降级"),
            new StageInfo("codegen", "代码生成"),
            new StageInfo("toolchain", "工具链"),
            new StageInfo("execution", "执行")
    );

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
        List<MiniCStageView> views = new ArrayList<>();
        String currentStage = currentState == null ? "lexer" : currentState.currentStage();
        int currentIndex = stageIndex(currentStage);
        for (int index = 0; index < STAGES.size(); index++) {
            StageInfo stage = STAGES.get(index);
            boolean active = stage.id().equals(currentStage);
            boolean done = index < currentIndex || active && currentStageData != null && currentStageData.completed();
            String state = state(active, done, globalData);
            int progress = progress(stage.id(), active, done, currentStageData);
            String detail = detail(stage.id(), active, currentStageData, globalData);
            views.add(new MiniCStageView(stage.id(), stage.title(), state, detail, progress));
        }
        return List.copyOf(views);
    }

    private int stageIndex(String id) {
        for (int index = 0; index < STAGES.size(); index++) {
            if (STAGES.get(index).id().equals(id)) {
                return index;
            }
        }
        return 0;
    }

    private String state(boolean active, boolean done, UiGlobalDataDto globalData) {
        if (hasErrors(globalData)) {
            return active ? "error" : done ? "done" : "queued";
        }
        if (active) {
            return "running";
        }
        if (done) {
            return "done";
        }
        return "queued";
    }

    private boolean hasErrors(UiGlobalDataDto globalData) {
        return globalData != null
                && globalData.diagnostics().stream().anyMatch(diagnostic -> "ERROR".equals(diagnostic.severity()));
    }

    private int progress(String stage, boolean active, boolean done, UiStageDataDto currentStageData) {
        if (done) {
            return 100;
        }
        if (!active || currentStageData == null || currentStageData.totalSteps() <= 0) {
            return 0;
        }
        long completed = currentStageData.completedSteps();
        long total = currentStageData.totalSteps();
        return (int) Math.max(0, Math.min(100, completed * 100 / total));
    }

    private String detail(
            String stage,
            boolean active,
            UiStageDataDto currentStageData,
            UiGlobalDataDto globalData
    ) {
        if (active && currentStageData != null) {
            return currentStageData.completedSteps() + " / " + currentStageData.totalSteps() + " · 当前阶段";
        }
        if (globalData == null) {
            return "等待会话启动";
        }
        return switch (stage) {
            case "source" -> "源码已加载";
            case "preprocess" -> globalData.preprocessSummary().isEmpty() ? "等待预编译" : "预处理产物已生成";
            case "lexer" -> globalData.tokenSummary().size() + " 个 token";
            case "parser" -> globalData.astSummary().size() + " 个 AST 项";
            case "semantic" -> globalData.semanticSummary().size() + " 个语义项";
            case "ir" -> globalData.irSummary().size() + " 个 IR 项";
            case "codegen" -> globalData.assemblySummary().size() + " 行汇编";
            case "toolchain" -> globalData.artifactSummary().isEmpty() ? "尚未生成产物" : "产物已就绪";
            case "execution" -> globalData.executionOutputSummary().isEmpty() ? "等待输入" : "运行完成";
            default -> "排队中";
        };
    }

    private record StageInfo(String id, String title) {
    }
}
