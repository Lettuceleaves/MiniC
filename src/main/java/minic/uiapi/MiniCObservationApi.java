package minic.uiapi;

import minic.runtime.step.CurrentStepState;
import minic.runtime.step.GlobalStepData;
import minic.runtime.step.StageStepData;
import minic.runtime.step.StepResult;
import minic.session.CompileObservationSession;
import minic.source.SourceFile;

import java.util.Objects;

/**
 * UI 层使用的 MiniC 编译观测控制门面。
 *
 * <p>该 API 不暴露内部 stepper 或编译层状态，也不依赖 JavaFX。</p>
 */
public final class MiniCObservationApi {
    private SourceFile sourceFile;
    private CompileObservationSession session;

    /**
     * 加载源码文本。
     *
     * @param sourceName 源码名称
     * @param source 源码文本
     */
    public void loadSource(String sourceName, String source) {
        loadSource(new SourceFile(sourceName, source));
    }

    /**
     * 加载源码文件。
     *
     * @param sourceFile 源码文件
     */
    public void loadSource(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        session = null;
    }

    /**
     * 开始编译观测会话。
     */
    public void startSession() {
        ensureSourceLoaded();
        session = CompileObservationSession.fromSource(sourceFile);
    }

    /**
     * 下一步。
     *
     * @return 单步结果
     */
    public StepResult next() {
        return requireSession().next();
    }

    /**
     * 开启自动播放。
     *
     * @return 控制结果
     */
    public StepResult play() {
        return requireSession().play();
    }

    /**
     * 开启两倍速自动播放。
     *
     * @return 控制结果
     */
    public StepResult playFast() {
        return requireSession().playFast();
    }

    /**
     * 手动驱动一个播放 tick。
     *
     * @return 单步结果
     */
    public StepResult tick() {
        return requireSession().tick();
    }

    /**
     * 暂停播放。
     *
     * @return 控制结果
     */
    public StepResult pause() {
        return requireSession().pause();
    }

    /**
     * 上一步预留接口，当前返回 unsupported。
     *
     * @return unsupported 结果
     */
    public StepResult previous() {
        return requireSession().previous();
    }

    /**
     * 自动倒放预留接口，当前返回 unsupported。
     *
     * @return unsupported 结果
     */
    public StepResult reversePlay() {
        return requireSession().reversePlay();
    }

    /**
     * 查询当前状态数据。
     *
     * @return 当前状态数据
     */
    public CurrentStepState currentState() {
        return requireSession().currentState();
    }

    /**
     * 查询当前阶段数据。
     *
     * @return 当前阶段数据
     */
    public StageStepData currentStageData() {
        return requireSession().currentStageData();
    }

    /**
     * 查询全局数据。
     *
     * @return 全局数据
     */
    public GlobalStepData globalData() {
        return requireSession().globalData();
    }

    private void ensureSourceLoaded() {
        if (sourceFile == null) {
            throw new IllegalStateException("source must be loaded before starting a session");
        }
    }

    private CompileObservationSession requireSession() {
        if (session == null) {
            throw new IllegalStateException("session must be started before using compile controls");
        }
        return session;
    }
}
