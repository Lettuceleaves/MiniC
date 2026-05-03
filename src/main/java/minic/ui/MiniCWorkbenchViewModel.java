package minic.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import minic.uiapi.MiniCObservationApi;
import minic.uiapi.UiControlResultDto;
import minic.uiapi.UiCurrentStateDto;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiStageDataDto;
import minic.uiapi.UiStageVisualDto;

import java.util.Objects;

/**
 * JavaFX UI 使用的 MiniC 观测状态模型。
 *
 * <p>该类型只持有 UI API 门面和 DTO，不暴露 compiler、runtime 或 session 内部对象。</p>
 */
public final class MiniCWorkbenchViewModel {
    private final MiniCObservationApi api;
    private final ReadOnlyStringWrapper sourceName = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper sourceText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper lastOutcome = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper sessionStarted = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<UiCurrentStateDto> currentState = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiStageDataDto> currentStageData = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiStageVisualDto> currentStageVisualData = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiStageVisualDto> lexerVisualData = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiStageVisualDto> astVisualData = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiStageVisualDto> semanticVisualData = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiGlobalDataDto> globalData = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiControlResultDto> lastControlResult = new ReadOnlyObjectWrapper<>();

    /**
     * 使用默认 UI API 创建状态模型。
     */
    public MiniCWorkbenchViewModel() {
        this(new MiniCObservationApi());
    }

    /**
     * 使用指定 UI API 创建状态模型。
     *
     * @param api UI API 门面
     */
    MiniCWorkbenchViewModel(MiniCObservationApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    /**
     * 加载源码文本。加载后需要调用 {@link #startSession()} 开始观测会话。
     *
     * @param name 源码名称
     * @param source 源码文本
     */
    public void loadSource(String name, String source) {
        api.loadSource(name, source);
        sourceName.set(name);
        sourceText.set(source);
        sessionStarted.set(false);
        currentState.set(null);
        currentStageData.set(null);
        currentStageVisualData.set(null);
        lexerVisualData.set(null);
        astVisualData.set(null);
        semanticVisualData.set(null);
        globalData.set(null);
        lastControlResult.set(null);
        lastOutcome.set("");
    }

    /**
     * 开始编译观测会话并刷新全部 UI 数据。
     */
    public void startSession() {
        api.startSession();
        sessionStarted.set(true);
        refreshAll();
    }

    /**
     * 执行下一步并刷新全部 UI 数据。
     *
     * @return 控制结果
     */
    public UiControlResultDto next() {
        UiControlResultDto result = api.next();
        applyControlResult(result);
        refreshAll();
        return result;
    }

    /**
     * 开启自动播放状态并刷新当前状态。
     *
     * @return 控制结果
     */
    public UiControlResultDto play() {
        UiControlResultDto result = api.play();
        applyControlResult(result);
        refreshAll();
        return result;
    }

    /**
     * 开启两倍速播放状态并刷新当前状态。
     *
     * @return 控制结果
     */
    public UiControlResultDto playFast() {
        UiControlResultDto result = api.playFast();
        applyControlResult(result);
        refreshAll();
        return result;
    }

    /**
     * 驱动一次播放 tick 并刷新全部 UI 数据。
     *
     * @return 控制结果
     */
    public UiControlResultDto tick() {
        UiControlResultDto result = api.tick();
        applyControlResult(result);
        refreshAll();
        return result;
    }

    /**
     * 暂停播放并刷新当前状态。
     *
     * @return 控制结果
     */
    public UiControlResultDto pause() {
        UiControlResultDto result = api.pause();
        applyControlResult(result);
        refreshAll();
        return result;
    }

    /**
     * 手动刷新全部 UI 数据。
     */
    public void refreshAll() {
        if (!sessionStarted.get()) {
            return;
        }
        currentState.set(api.currentState());
        currentStageData.set(api.currentStageData());
        currentStageVisualData.set(api.currentStageVisualData());
        lexerVisualData.set(api.lexerVisualData());
        astVisualData.set(api.astVisualData());
        semanticVisualData.set(api.semanticVisualData());
        globalData.set(api.globalData());
    }

    /**
     * 源码名称属性。
     *
     * @return 源码名称属性
     */
    public ReadOnlyStringProperty sourceNameProperty() {
        return sourceName.getReadOnlyProperty();
    }

    /**
     * 源码文本属性。
     *
     * @return 源码文本属性
     */
    public ReadOnlyStringProperty sourceTextProperty() {
        return sourceText.getReadOnlyProperty();
    }

    /**
     * 最近控制结果类别属性。
     *
     * @return 最近控制结果类别属性
     */
    public ReadOnlyStringProperty lastOutcomeProperty() {
        return lastOutcome.getReadOnlyProperty();
    }

    /**
     * 会话是否已启动属性。
     *
     * @return 会话是否已启动属性
     */
    public ReadOnlyBooleanProperty sessionStartedProperty() {
        return sessionStarted.getReadOnlyProperty();
    }

    /**
     * 当前状态 DTO 属性。
     *
     * @return 当前状态 DTO 属性
     */
    public ReadOnlyObjectProperty<UiCurrentStateDto> currentStateProperty() {
        return currentState.getReadOnlyProperty();
    }

    /**
     * 当前阶段数据 DTO 属性。
     *
     * @return 当前阶段数据 DTO 属性
     */
    public ReadOnlyObjectProperty<UiStageDataDto> currentStageDataProperty() {
        return currentStageData.getReadOnlyProperty();
    }

    /**
     * 当前阶段图形化 DTO 属性。
     *
     * @return 当前阶段图形化 DTO 属性
     */
    public ReadOnlyObjectProperty<UiStageVisualDto> currentStageVisualDataProperty() {
        return currentStageVisualData.getReadOnlyProperty();
    }

    /**
     * Lexer token 图形化 DTO 属性。
     *
     * @return token 图形化 DTO 属性
     */
    public ReadOnlyObjectProperty<UiStageVisualDto> lexerVisualDataProperty() {
        return lexerVisualData.getReadOnlyProperty();
    }

    /**
     * AST 图形化 DTO 属性。
     *
     * @return AST 图形化 DTO 属性
     */
    public ReadOnlyObjectProperty<UiStageVisualDto> astVisualDataProperty() {
        return astVisualData.getReadOnlyProperty();
    }

    /**
     * Semantic scope 图形化 DTO 属性。
     *
     * @return semantic scope 图形化 DTO 属性
     */
    public ReadOnlyObjectProperty<UiStageVisualDto> semanticVisualDataProperty() {
        return semanticVisualData.getReadOnlyProperty();
    }

    /**
     * 全局数据 DTO 属性。
     *
     * @return 全局数据 DTO 属性
     */
    public ReadOnlyObjectProperty<UiGlobalDataDto> globalDataProperty() {
        return globalData.getReadOnlyProperty();
    }

    /**
     * 最近控制结果 DTO 属性。
     *
     * @return 最近控制结果 DTO 属性
     */
    public ReadOnlyObjectProperty<UiControlResultDto> lastControlResultProperty() {
        return lastControlResult.getReadOnlyProperty();
    }

    private void applyControlResult(UiControlResultDto result) {
        lastControlResult.set(result);
        lastOutcome.set(result.outcome());
    }
}
