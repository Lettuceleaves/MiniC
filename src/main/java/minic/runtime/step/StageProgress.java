package minic.runtime.step;

/**
 * 阶段进度。
 *
 * @param completedSteps 已完成步骤数
 * @param totalSteps 已知总步骤数；未知时为 {@code -1}
 * @param completed 阶段是否已完成
 */
public record StageProgress(long completedSteps, long totalSteps, boolean completed) {
    /**
     * 创建阶段进度。
     *
     * @param completedSteps 已完成步骤数
     * @param totalSteps 已知总步骤数；未知时为 {@code -1}
     * @param completed 阶段是否已完成
     */
    public StageProgress {
        if (completedSteps < 0) {
            throw new IllegalArgumentException("completedSteps must not be negative");
        }
        if (totalSteps < -1) {
            throw new IllegalArgumentException("totalSteps must be -1 or greater");
        }
        if (totalSteps >= 0 && completedSteps > totalSteps) {
            throw new IllegalArgumentException("completedSteps must not exceed totalSteps");
        }
    }

    /**
     * 创建未知总步数的进度。
     *
     * @param completedSteps 已完成步骤数
     * @return 阶段进度
     */
    public static StageProgress unknownTotal(long completedSteps) {
        return new StageProgress(completedSteps, -1, false);
    }

    /**
     * 创建已完成进度。
     *
     * @param totalSteps 总步骤数
     * @return 阶段进度
     */
    public static StageProgress completed(long totalSteps) {
        return new StageProgress(totalSteps, totalSteps, true);
    }

    /**
     * 判断总步数是否已知。
     *
     * @return 已知时为 {@code true}
     */
    public boolean hasKnownTotal() {
        return totalSteps >= 0;
    }
}
