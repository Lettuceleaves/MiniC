package minic.uilocal;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import minic.uiapi.MiniCObservationApi;
import minic.uiapi.MiniCDebugApi;
import minic.uiapi.UiControlResultDto;
import minic.uiapi.UiCurrentStateDto;
import minic.uiapi.UiDebugAsmViewDto;
import minic.uiapi.UiDebugAstViewDto;
import minic.uiapi.UiDebugDataStructureViewDto;
import minic.uiapi.UiDebugIrViewDto;
import minic.uiapi.UiDebugMetadataViewDto;
import minic.uiapi.UiDebugStateDto;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiRealtimeAnalysisDto;
import minic.uiapi.UiStageDataDto;
import minic.uiapi.UiStageVisualDto;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JavaFX UI 使用的 MiniC 观测状态模型。
 *
 * <p>该类型只持有 UI API 门面和 DTO，不暴露 compiler、runtime 或 session 内部对象。</p>
 */
public final class MiniCWorkbenchViewModel {
    private final MiniCObservationApi api;
    private final MiniCDebugApi debugApi = new MiniCDebugApi();
    private final MiniCRealtimeAnalyzer realtimeAnalyzer;
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
    private final ReadOnlyObjectWrapper<UiStageVisualDto> irVisualData = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiStageVisualDto> codegenVisualData = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiGlobalDataDto> globalData = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiRealtimeAnalysisDto> realtimeAnalysis = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiControlResultDto> lastControlResult = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper selectedVisualStage = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper debugStarted = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<UiDebugStateDto> debugState = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiDebugMetadataViewDto> debugMetadataView = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiDebugDataStructureViewDto> debugDataStructureView = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiDebugAstViewDto> debugAstView = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiDebugIrViewDto> debugIrView = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiDebugAsmViewDto> debugAsmView = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<List<Integer>> debugBreakpointLines = new ReadOnlyObjectWrapper<>(List.of());
    private final Map<String, UiViewportState> viewportStates = new ConcurrentHashMap<>();
    private String executionInputDraft = "";

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
        realtimeAnalyzer = new MiniCRealtimeAnalyzer(this::applyRealtimeAnalysis);
    }

    /**
     * 加载源码文本。加载后需要调用 {@link #startSession()} 开始观测会话。
     *
     * @param name 源码名称
     * @param source 源码文本
     */
    public void loadSource(String name, String source) {
        api.loadSource(name, source);
        debugApi.loadSource(name, source);
        sourceName.set(name);
        sourceText.set(source);
        clearSessionState();
    }

    /**
     * 重命名当前源码。源码名属于编译输入，因此会清空当前观测会话。
     *
     * @param name 新源码名称
     */
    public void renameSource(String name) {
        api.loadSource(name, sourceText.get());
        debugApi.loadSource(name, sourceText.get());
        sourceName.set(name);
        clearSessionState();
    }

    private void clearSessionState() {
        sessionStarted.set(false);
        currentState.set(null);
        currentStageData.set(null);
        currentStageVisualData.set(null);
        lexerVisualData.set(null);
        astVisualData.set(null);
        semanticVisualData.set(null);
        irVisualData.set(null);
        codegenVisualData.set(null);
        globalData.set(null);
        realtimeAnalysis.set(null);
        lastControlResult.set(null);
        selectedVisualStage.set("");
        lastOutcome.set("");
        clearDebugState();
    }

    private void clearDebugState() {
        debugStarted.set(false);
        debugState.set(null);
        debugMetadataView.set(null);
        debugDataStructureView.set(null);
        debugAstView.set(null);
        debugIrView.set(null);
        debugAsmView.set(null);
    }

    /**
     * 提交实时编辑分析输入。
     *
     * @param name 源码名称
     * @param source 源码文本
     */
    public void submitRealtimeSource(String name, String source) {
        sourceName.set(name);
        sourceText.set(source);
        realtimeAnalyzer.submit(name, source);
    }

    /**
     * 开始编译观测会话并刷新全部 UI 数据。
     */
    public void startSession() {
        api.startSession();
        sessionStarted.set(true);
        selectedVisualStage.set("");
        refreshAll();
    }

    /**
     * 执行下一步并刷新全部 UI 数据。
     *
     * @return 控制结果
     */
    public UiControlResultDto next() {
        selectedVisualStage.set("");
        autoConfirmExecutionInput();
        UiControlResultDto result = api.next();
        applyControlResult(result);
        refreshAll();
        if ("CANNOT_ADVANCE".equals(result.outcome())
                && "execution".equals(result.stage())
                && currentState.get() != null
                && currentStageData.get() != null
                && "execution".equals(currentState.get().currentStage())
                && !currentState.get().canNext()
                && currentStageData.get().completed()) {
            loadSource(sourceName.get(), sourceText.get());
        }
        return result;
    }

    /**
     * 跳转到下一编译环节并刷新全部 UI 数据。
     *
     * @return 控制结果
     */
    public UiControlResultDto nextStage() {
        selectedVisualStage.set("");
        autoConfirmExecutionInput();
        UiControlResultDto result = currentState.get() != null
                && "execution".equals(currentState.get().currentStage())
                && currentState.get().canNext()
                ? api.next()
                : api.nextStage();
        applyControlResult(result);
        refreshAll();
        if ("CANNOT_ADVANCE".equals(result.outcome())
                && "execution".equals(result.stage())
                && currentState.get() != null
                && currentStageData.get() != null
                && "execution".equals(currentState.get().currentStage())
                && !currentState.get().canNext()
                && currentStageData.get().completed()) {
            loadSource(sourceName.get(), sourceText.get());
        }
        return result;
    }

    /**
     * 一步推进到执行阶段入口并刷新全部 UI 数据。
     *
     * @return 最后一次控制结果
     */
    public UiControlResultDto runToExecution() {
        selectedVisualStage.set("");
        UiControlResultDto result = api.runToExecution();
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
        selectedVisualStage.set("");
        autoConfirmExecutionInput();
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
        selectedVisualStage.set("");
        autoConfirmExecutionInput();
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
        autoConfirmExecutionInput();
        UiControlResultDto result = api.tick();
        applyControlResult(result);
        refreshAll();
        if ("CANNOT_ADVANCE".equals(result.outcome())
                && "execution".equals(result.stage())
                && currentState.get() != null
                && currentStageData.get() != null
                && "execution".equals(currentState.get().currentStage())
                && !currentState.get().canNext()
                && currentStageData.get().completed()) {
            loadSource(sourceName.get(), sourceText.get());
        }
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
     * 确认运行阶段标准输入并刷新全部 UI 数据。
     *
     * @param standardInput 标准输入文本
     * @return 控制结果
     */
    public UiControlResultDto confirmExecutionInput(String standardInput) {
        selectedVisualStage.set("");
        executionInputDraft = standardInput == null ? "" : standardInput;
        UiControlResultDto result = api.confirmExecutionInput(standardInput);
        applyControlResult(result);
        refreshAll();
        return result;
    }

    /**
     * 更新执行阶段标准输入草稿。
     *
     * @param standardInput 标准输入文本
     */
    public void updateExecutionInputDraft(String standardInput) {
        executionInputDraft = standardInput == null ? "" : standardInput;
    }

    /**
     * 返回执行阶段标准输入草稿。
     *
     * @return 标准输入文本
     */
    public String executionInputDraft() {
        return executionInputDraft;
    }

    /**
     * UI 控制栏是否允许执行下一步。
     *
     * @return 允许时为 {@code true}
     */
    public boolean canNextControl() {
        return currentState.get() != null && (currentState.get().canNext() || executionAwaitingInput());
    }

    /**
     * UI 控制栏是否允许跳转下一阶段。
     *
     * @return 允许时为 {@code true}
     */
    public boolean canNextStageControl() {
        return canNextControl();
    }

    /**
     * UI 控制栏是否允许一步推进到执行阶段。
     *
     * @return 允许时为 {@code true}
     */
    public boolean canRunToExecutionControl() {
        return currentState.get() != null
                && currentState.get().canNext()
                && !"execution".equals(currentState.get().currentStage());
    }

    /**
     * UI 控制栏是否允许播放。
     *
     * @return 允许时为 {@code true}
     */
    public boolean canPlayControl() {
        return currentState.get() != null && (currentState.get().canPlay() || executionAwaitingInput());
    }

    /**
     * UI 控制栏是否允许两倍速播放。
     *
     * @return 允许时为 {@code true}
     */
    public boolean canPlayFastControl() {
        return currentState.get() != null && (currentState.get().canPlayFast() || executionAwaitingInput());
    }

    /**
     * 选择要在中间可视化区域展示的 pipeline 阶段。
     *
     * @param stage 阶段 ID；空字符串表示跟随当前阶段
     */
    public void selectVisualStage(String stage) {
        selectedVisualStage.set(stage == null ? "" : stage);
        if (stage != null && !stage.isEmpty()) {
            var state = currentState.get();
            if (state != null && !"PAUSED".equals(state.playbackMode())) {
                pause();
            }
        }
    }

    /**
     * 启动 Debug 模式并刷新 Debug DTO。
     */
    public void startDebug() {
        String name = sourceName.get() == null || sourceName.get().isBlank() ? "untitled.mc" : sourceName.get();
        debugApi.loadSource(name, sourceText.get());
        debugApi.startDebug();
        debugStarted.set(true);
        applyPendingDebugBreakpoints();
        refreshDebug();
    }

    /**
     * 使用编辑器断点同步 Debug 会话断点。
     *
     * @param lines 一基源码行号列表
     */
    public void setDebugBreakpoints(List<Integer> lines) {
        debugBreakpointLines.set(normalizeBreakpoints(lines));
        syncDebugBreakpoints();
    }

    /**
     * 将待同步断点应用到已启动的 Debug 会话。
     */
    public void syncDebugBreakpoints() {
        if (!debugStarted.get()) {
            return;
        }
        applyPendingDebugBreakpoints();
        refreshDebug();
    }

    /**
     * 设置 Debug 断点。
     *
     * @param line 源码行
     */
    public void setDebugBreakpoint(int line) {
        ensureDebugStarted();
        debugBreakpointLines.set(mergeBreakpoint(line));
        debugApi.setBreakpoint(line);
        refreshDebug();
    }

    /**
     * 清除 Debug 断点。
     *
     * @param line 源码行
     */
    public void clearDebugBreakpoint(int line) {
        ensureDebugStarted();
        debugBreakpointLines.set(debugBreakpointLines.get().stream()
                .filter(breakpoint -> breakpoint != line)
                .toList());
        debugApi.clearBreakpoint(line);
        refreshDebug();
    }

    /**
     * 运行到断点。
     */
    public void debugRunToBreakpoint() {
        ensureDebugStarted();
        debugApi.runToBreakpoint();
        refreshDebug();
    }

    /**
     * Debug 运行到结束。
     */
    public void debugRunToEnd() {
        ensureDebugStarted();
        debugApi.runToEnd();
        refreshDebug();
    }

    /**
     * Debug 快进。
     */
    public void debugFastForward() {
        ensureDebugStarted();
        debugApi.fastForward();
        refreshDebug();
    }

    /**
     * Debug 单步。
     */
    public void debugStepOver() {
        ensureDebugStarted();
        debugApi.stepOver();
        refreshDebug();
    }

    /**
     * Debug 步入。
     */
    public void debugStepInto() {
        ensureDebugStarted();
        debugApi.stepInto();
        refreshDebug();
    }

    /**
     * Debug 步返。
     */
    public void debugStepOut() {
        ensureDebugStarted();
        debugApi.stepOut();
        refreshDebug();
    }

    /**
     * Debug 暂停。
     */
    public void debugPause() {
        ensureDebugStarted();
        debugApi.pause();
        refreshDebug();
    }

    /**
     * Debug 重启。
     */
    public void debugRestart() {
        ensureDebugStarted();
        debugApi.restart();
        refreshDebug();
    }

    /**
     * Debug 关闭。
     */
    public void debugClose() {
        ensureDebugStarted();
        debugApi.close();
        refreshDebug();
    }

    /**
     * 单退。
     */
    public void debugStepBack() {
        ensureDebugStarted();
        debugApi.stepBack();
        refreshDebug();
    }

    /**
     * 本层单退。
     */
    public void debugStepBackOver() {
        ensureDebugStarted();
        debugApi.stepBackOver();
        refreshDebug();
    }

    /**
     * Debug 步退到上一个断点。
     */
    public void debugBackToBreakpoint() {
        ensureDebugStarted();
        debugApi.backToBreakpoint();
        refreshDebug();
    }

    /**
     * Debug 返回调用处。
     */
    public void debugBackToCallSite() {
        ensureDebugStarted();
        debugApi.backToCallSite();
        refreshDebug();
    }

    /**
     * 刷新 Debug DTO。
     */
    public void refreshDebug() {
        if (!debugStarted.get()) {
            return;
        }
        debugState.set(debugApi.currentState());
        debugMetadataView.set(debugApi.metadataView());
        debugDataStructureView.set(debugApi.dataStructureDebugView());
        debugAstView.set(debugApi.astDebugView());
        debugIrView.set(debugApi.irDebugView());
        debugAsmView.set(debugApi.asmDebugView());
    }

    /**
     * 手动刷新全部 UI 数据。
     */
    public void refreshAll() {
        if (!sessionStarted.get()) {
            return;
        }
        UiStageVisualDto currentVisual = api.currentStageVisualData();
        currentState.set(api.currentState());
        currentStageData.set(api.currentStageData());
        currentStageVisualData.set(currentVisual);
        lexerVisualData.set(api.lexerVisualData());
        astVisualData.set(api.astVisualData());
        semanticVisualData.set(api.semanticVisualData());
        irVisualData.set("ir".equals(currentVisual.stage()) ? currentVisual : api.irVisualData());
        codegenVisualData.set(api.codegenVisualData());
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
     * IR 图形化 DTO 属性。
     *
     * @return IR 图形化 DTO 属性
     */
    public ReadOnlyObjectProperty<UiStageVisualDto> irVisualDataProperty() {
        return irVisualData.getReadOnlyProperty();
    }

    /**
     * Codegen 汇编图形化 DTO 属性。
     *
     * @return 汇编图形化 DTO 属性
     */
    public ReadOnlyObjectProperty<UiStageVisualDto> codegenVisualDataProperty() {
        return codegenVisualData.getReadOnlyProperty();
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
     * 实时分析结果属性。
     *
     * @return 实时分析结果属性
     */
    public ReadOnlyObjectProperty<UiRealtimeAnalysisDto> realtimeAnalysisProperty() {
        return realtimeAnalysis.getReadOnlyProperty();
    }

    /**
     * 最近控制结果 DTO 属性。
     *
     * @return 最近控制结果 DTO 属性
     */
    public ReadOnlyObjectProperty<UiControlResultDto> lastControlResultProperty() {
        return lastControlResult.getReadOnlyProperty();
    }

    /**
     * 当前手动选择展示的 pipeline 阶段。
     *
     * @return 阶段 ID 属性
     */
    public ReadOnlyStringProperty selectedVisualStageProperty() {
        return selectedVisualStage.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty debugStartedProperty() {
        return debugStarted.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<UiDebugStateDto> debugStateProperty() {
        return debugState.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<UiDebugMetadataViewDto> debugMetadataViewProperty() {
        return debugMetadataView.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<UiDebugDataStructureViewDto> debugDataStructureViewProperty() {
        return debugDataStructureView.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<UiDebugAstViewDto> debugAstViewProperty() {
        return debugAstView.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<UiDebugIrViewDto> debugIrViewProperty() {
        return debugIrView.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<UiDebugAsmViewDto> debugAsmViewProperty() {
        return debugAsmView.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<List<Integer>> debugBreakpointLinesProperty() {
        return debugBreakpointLines.getReadOnlyProperty();
    }

    public UiViewportState viewportState(String key) {
        return viewportStates.getOrDefault(key, UiViewportState.DEFAULT);
    }

    public void saveViewportState(String key, double hvalue, double vvalue) {
        viewportStates.put(Objects.requireNonNull(key, "key"), new UiViewportState(clampUnit(hvalue), clampUnit(vvalue)));
    }

    private double clampUnit(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private void applyControlResult(UiControlResultDto result) {
        lastControlResult.set(result);
        lastOutcome.set(result.outcome());
    }

    private void autoConfirmExecutionInput() {
        if (!sessionStarted.get()) {
            return;
        }
        UiCurrentStateDto state = currentState.get();
        UiGlobalDataDto data = globalData.get();
        if (state == null || data == null || !"execution".equals(state.currentStage())) {
            return;
        }
        if (data.executionInputConfirmed()) {
            return;
        }
        UiControlResultDto result = api.confirmExecutionInput(executionInputDraft);
        applyControlResult(result);
        refreshAll();
    }

    private boolean executionAwaitingInput() {
        UiCurrentStateDto state = currentState.get();
        UiGlobalDataDto data = globalData.get();
        return state != null
                && data != null
                && "execution".equals(state.currentStage())
                && data.executionInputPending();
    }

    private void applyRealtimeAnalysis(UiRealtimeAnalysisDto result) {
        if (Objects.equals(sourceName.get(), result.sourceName())
                && Objects.equals(sourceText.get(), result.sourceText())) {
            realtimeAnalysis.set(result);
        }
    }

    private void ensureDebugStarted() {
        if (!debugStarted.get()) {
            startDebug();
        }
    }

    private void applyPendingDebugBreakpoints() {
        if (debugState.get() != null) {
            debugState.get().breakpoints().forEach(breakpoint -> debugApi.clearBreakpoint(breakpoint.line()));
        }
        debugBreakpointLines.get().forEach(debugApi::setBreakpoint);
    }

    private List<Integer> mergeBreakpoint(int line) {
        List<Integer> current = debugBreakpointLines.get();
        if (line < 1 || current.contains(line)) {
            return current;
        }
        java.util.ArrayList<Integer> lines = new java.util.ArrayList<>(current);
        lines.add(line);
        return normalizeBreakpoints(lines);
    }

    private List<Integer> normalizeBreakpoints(List<Integer> lines) {
        return Objects.requireNonNull(lines, "lines").stream()
                .filter(line -> line != null && line >= 1)
                .distinct()
                .sorted()
                .toList();
    }

    public record UiViewportState(double hvalue, double vvalue) {
        public static final UiViewportState DEFAULT = new UiViewportState(0.0, 0.0);
    }
}
