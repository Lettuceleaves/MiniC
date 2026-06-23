package minic.uiapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * UI API 暴露的 pipeline 阶段卡片展示语义。
 *
 * @param id 阶段 ID
 * @param title 展示标题
 * @param state 展示状态
 * @param detail 细节文本
 * @param progressPercent 进度百分比
 */
public record UiStageViewDto(
        String id,
        String title,
        String state,
        String detail,
        int progressPercent
) {
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

    public UiStageViewDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("progressPercent must be between 0 and 100");
        }
    }

    /**
     * 返回等待会话启动时的默认阶段卡片。
     *
     * @return 默认阶段卡片
     */
    public static List<UiStageViewDto> initialViews() {
        return from(null, null, null);
    }

    /**
     * 根据 UIAPI 状态 DTO 构建阶段卡片语义。
     *
     * @param currentState 当前状态
     * @param currentStageData 当前阶段数据
     * @param globalData 全局数据
     * @return 阶段卡片列表
     */
    public static List<UiStageViewDto> from(
            UiCurrentStateDto currentState,
            UiStageDataDto currentStageData,
            UiGlobalDataDto globalData
    ) {
        List<UiStageViewDto> views = new ArrayList<>();
        String currentStage = currentState == null ? "source" : currentState.currentStage();
        int currentIndex = stageIndex(currentStage);
        for (int index = 0; index < STAGES.size(); index++) {
            StageInfo stage = STAGES.get(index);
            boolean active = stage.id().equals(currentStage);
            boolean done = currentState != null
                    && (index < currentIndex || active && currentStageData != null && currentStageData.completed());
            views.add(new UiStageViewDto(
                    stage.id(),
                    stage.title(),
                    state(active, done, globalData),
                    detail(stage.id(), active, currentStageData, globalData),
                    progress(stage.id(), active, done, currentState, currentStageData)
            ));
        }
        return List.copyOf(views);
    }

    private static int stageIndex(String id) {
        for (int index = 0; index < STAGES.size(); index++) {
            if (STAGES.get(index).id().equals(id)) {
                return index;
            }
        }
        return 0;
    }

    private static String state(boolean active, boolean done, UiGlobalDataDto globalData) {
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

    private static boolean hasErrors(UiGlobalDataDto globalData) {
        return globalData != null
                && globalData.diagnostics().stream().anyMatch(diagnostic -> "ERROR".equals(diagnostic.severity()));
    }

    private static int progress(
            String stage,
            boolean active,
            boolean done,
            UiCurrentStateDto currentState,
            UiStageDataDto currentStageData
    ) {
        if (done) {
            return 100;
        }
        if ("source".equals(stage) && active && currentState != null) {
            return 100;
        }
        if (!active || currentStageData == null || currentStageData.totalSteps() <= 0) {
            return 0;
        }
        long completed = currentStageData.completedSteps();
        long total = currentStageData.totalSteps();
        return (int) Math.max(0, Math.min(100, completed * 100 / total));
    }

    private static String detail(
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
