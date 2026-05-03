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
            new StageInfo("source", "Source"),
            new StageInfo("lexer", "Lexer"),
            new StageInfo("parser", "Parser"),
            new StageInfo("semantic", "Semantic"),
            new StageInfo("ir", "IR"),
            new StageInfo("codegen", "Codegen"),
            new StageInfo("toolchain", "Toolchain")
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
            return currentStageData.completedSteps() + " / " + currentStageData.totalSteps() + " · current stage";
        }
        if (globalData == null) {
            return "waiting for session";
        }
        return switch (stage) {
            case "source" -> "source loaded";
            case "lexer" -> globalData.tokenSummary().size() + " tokens";
            case "parser" -> globalData.astSummary().size() + " AST items";
            case "semantic" -> globalData.semanticSummary().size() + " semantic items";
            case "ir" -> globalData.irSummary().size() + " IR items";
            case "codegen" -> globalData.assemblySummary().size() + " assembly lines";
            case "toolchain" -> globalData.artifactSummary().isEmpty() ? "not implemented" : "artifact ready";
            default -> "queued";
        };
    }

    private record StageInfo(String id, String title) {
    }
}
