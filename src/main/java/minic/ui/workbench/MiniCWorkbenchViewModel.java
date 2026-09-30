package minic.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import minic.compiler.SourceFile;
import minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * JavaFX UI 使用的 MiniC 观测状态模型，直接连接编译会话与 DebugApi。
 */
public final class MiniCWorkbenchViewModel {
    private static final int DEBUG_NAVIGATION_LIMIT = 100_000;
    private final MiniCObservationApi api;
    private DebugApi debugApi;
    private SourceFile debugSource;
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
    private final ReadOnlyObjectWrapper<UiStageVisualDto> asmVisualData = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiGlobalDataDto> globalData = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiRealtimeAnalysisDto> realtimeAnalysis = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<UiControlResultDto> lastControlResult = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper selectedVisualStage = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper debugStarted = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<DebugState> debugState = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<List<Integer>> debugBreakpointLines = new ReadOnlyObjectWrapper<>(List.of());
    private final Map<String, UiViewportState> viewportStates = new ConcurrentHashMap<>();
    private String executionInputDraft = "";
    private String debugNotice = "";

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
        asmVisualData.set(null);
        globalData.set(null);
        realtimeAnalysis.set(null);
        lastControlResult.set(null);
        selectedVisualStage.set("");
        lastOutcome.set("");
        clearDebugState();
    }

    private void clearDebugState() {
        debugStarted.set(false);
        debugApi = null;
        debugSource = null;
        debugState.set(null);
        debugNotice = "";
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
        UiControlResultDto result = api.nextStage();
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
     * 一步推进到包含 Execution 在内的整条流水线结束，并刷新全部 UI 数据。
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

    /** 启动只编译到 IR；首次单步停在第一个 trap。 */
    public void startDebug() {
        clearDebugState();
        String name = sourceName.get() == null || sourceName.get().isBlank() ? "untitled.mc" : sourceName.get();
        debugSource = new SourceFile(name, sourceText.get());
        debugApi = new DebugApi(debugSource);
        debugState.set(debugState());
        debugStarted.set(true);
    }

    public void debugStep() {
        ensureDebugStarted();
        debugNotice = "";
        debugApi.next();
        debugState.set(debugState());
    }

    public void debugStepBack() {
        ensureDebugStarted();
        debugNotice = "";
        debugApi.previous();
        debugState.set(debugState());
    }

    public void debugRunToEnd() {
        ensureDebugStarted();
        moveForward(context -> false);
    }

    public void debugRunToBreakpoint() {
        ensureDebugStarted();
        moveForward(this::isBreakpoint);
    }

    public void debugStepOver() {
        ensureDebugStarted();
        int depth = debugApi.current().runtime().stack().size();
        moveForward(context -> context.runtime().stack().size() <= depth);
    }

    public void debugStepOut() {
        ensureDebugStarted();
        int depth = debugApi.current().runtime().stack().size();
        moveForward(context -> context.runtime().stack().size() < depth);
    }

    public void debugStepBackOver() {
        ensureDebugStarted();
        int depth = debugApi.current().runtime().stack().size();
        moveBackward(context -> context.runtime().stack().size() <= depth);
    }

    public void debugBackToBreakpoint() {
        ensureDebugStarted();
        moveBackward(this::isBreakpoint);
    }

    public void debugBackToCallSite() {
        ensureDebugStarted();
        int depth = debugApi.current().runtime().stack().size();
        moveBackward(context -> "CALL".equals(context.stop().kind() == null ? "" : context.stop().kind().name())
                && context.runtime().stack().size() <= depth);
    }

    public void debugRestart() {
        startDebug();
    }

    public void debugClose() {
        clearDebugState();
    }

    public void setDebugBreakpoints(List<Integer> lines) {
        debugBreakpointLines.set(Objects.requireNonNull(lines, "lines").stream()
                .filter(line -> line != null && line > 0)
                .distinct().sorted().toList());
    }

    private void moveForward(Predicate<minic.debug.Debugger.Context> stop) {
        debugNotice = "";
        int moved = 0;
        do {
            if (!debugApi.canNext()) break;
            var context = debugApi.next();
            moved++;
            if (stop.test(context)) break;
        } while (moved < DEBUG_NAVIGATION_LIMIT);
        if (moved == DEBUG_NAVIGATION_LIMIT && debugApi.canNext()) {
            debugNotice = "已达到单次导航上限，可继续执行";
        }
        debugState.set(debugState());
    }

    private void moveBackward(Predicate<minic.debug.Debugger.Context> stop) {
        debugNotice = "";
        int moved = 0;
        do {
            if (!debugApi.canPrevious()) break;
            var context = debugApi.previous();
            moved++;
            if (stop.test(context)) break;
        } while (moved < DEBUG_NAVIGATION_LIMIT);
        if (moved == DEBUG_NAVIGATION_LIMIT && debugApi.canPrevious()) {
            debugNotice = "已达到单次导航上限，可继续回退";
        }
        debugState.set(debugState());
    }

    private boolean isBreakpoint(minic.debug.Debugger.Context context) {
        return context.stop().range() != null
                && "LINE".equals(context.stop().kind() == null ? "" : context.stop().kind().name())
                && debugBreakpointLines.get().contains(context.stop().range().startLine());
    }

    private void ensureDebugStarted() {
        if (!debugStarted.get()) startDebug();
    }

    private DebugState debugState() {
        var context = debugApi.current();
        var stop = context.stop();
        var runtime = context.runtime();
        StringBuilder ir = new StringBuilder();
        for (var function : context.program().ir().functions()) {
            ir.append(function.name()).append(':').append('\n');
            for (var block : function.blocks()) {
                ir.append("  ").append(block.label()).append(':').append('\n');
                for (int index = 0; index < block.instructions().size(); index++) {
                    boolean active = function.name().equals(stop.function())
                            && block.label().equals(stop.block()) && index == stop.instruction();
                    ir.append(active ? " > " : "   ").append(index).append("  ")
                            .append(block.instructions().get(index)).append('\n');
                }
            }
        }
        String space = runtimeText(context);
        return new DebugState(
                context.index(), stop.status().name(), stop.kind() == null ? "" : stop.kind().name(),
                stop.function(), stop.block(), runtime.stack().size(),
                debugApi.canNext(), debugApi.canPrevious(),
                stop.range() == null ? null : UiSourceSpanDto.from(debugSource, stop.range()),
                ir.toString(), space, runtime.stdout(), stop.error(), debugNotice
        );
    }

    private String runtimeText(minic.debug.Debugger.Context context) {
        var runtime = context.runtime();
        Map<String, IrLocal> locals = new LinkedHashMap<>();
        context.program().ir().functions().stream()
                .flatMap(function -> function.blocks().stream())
                .flatMap(block -> block.instructions().stream())
                .filter(IrDeclareLocalInstruction.class::isInstance)
                .map(IrDeclareLocalInstruction.class::cast)
                .forEach(instruction -> locals.put(instruction.local().name(), instruction.local()));

        StringBuilder text = new StringBuilder("调用栈\n");
        for (int frameIndex = runtime.stack().size() - 1; frameIndex >= 0; frameIndex--) {
            var frame = runtime.stack().get(frameIndex);
            text.append("#").append(frameIndex).append(" ").append(frame.function())
                    .append("  block=").append(frame.block())
                    .append(" instruction=").append(frame.instruction()).append('\n');
            if (!frame.parameters().isEmpty()) text.append("  参数: ").append(frame.parameters()).append('\n');
            for (var entry : frame.locals().entrySet()) {
                IrLocal local = locals.get(entry.getKey());
                var block = runtime.stackMemory().stream()
                        .filter(candidate -> candidate.address() == entry.getValue()).findFirst().orElse(null);
                String name = local == null ? entry.getKey() : local.sourceName();
                String type = local == null ? "?" : local.type().name();
                text.append("  ").append(name).append(" : ").append(type)
                        .append(" @0x").append(Long.toHexString(entry.getValue())).append(" = ")
                        .append(formatLocal(local, block)).append('\n');
            }
            if (!frame.temporaries().isEmpty()) text.append("  临时值: ").append(frame.temporaries()).append('\n');
        }
        if (runtime.stack().isEmpty()) text.append("  <empty>\n");

        text.append("\n堆内存\n");
        if (runtime.heap().isEmpty()) text.append("  <empty>\n");
        runtime.heap().forEach(block -> text.append("  0x").append(Long.toHexString(block.address()))
                .append(" size=").append(block.size()).append(" ").append(block.label())
                .append(" initialized=").append(block.initializedBytes()).append(" bytes=")
                .append(block.bytes()).append('\n'));
        if (runtime.returnValue() != null) text.append("\n返回值: ").append(runtime.returnValue()).append('\n');
        return text.toString();
    }

    private String formatLocal(IrLocal local, minic.debug.DebugRuntime.MemoryBlock block) {
        if (block == null || block.initializedBytes() == 0) return "<未初始化>";
        if (local == null || block.initializedBytes() < Math.min(local.sizeBytes(), local.type().sizeBytes())) {
            return "<部分初始化> bytes=" + block.bytes();
        }
        if (local.aggregate()) return "bytes=" + block.bytes();
        ByteBuffer bytes = ByteBuffer.wrap(HexFormat.of().parseHex(block.bytes())).order(ByteOrder.LITTLE_ENDIAN);
        return switch (local.type()) {
            case BOOL -> bytes.get(0) == 0 ? "false" : "true";
            case CHAR -> Byte.toString(bytes.get(0));
            case INT -> Integer.toString(bytes.getInt(0));
            case LONG -> Long.toString(bytes.getLong(0));
            case FLOAT -> Float.toString(bytes.getFloat(0));
            case DOUBLE -> Double.toString(bytes.getDouble(0));
            case POINTER -> "0x" + Long.toHexString(bytes.getLong(0));
            default -> "bytes=" + block.bytes();
        };
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
        asmVisualData.set(api.asmVisualData());
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
     * Asm 汇编图形化 DTO 属性。
     *
     * @return 汇编图形化 DTO 属性
     */
    public ReadOnlyObjectProperty<UiStageVisualDto> asmVisualDataProperty() {
        return asmVisualData.getReadOnlyProperty();
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

    public ReadOnlyObjectProperty<DebugState> debugStateProperty() {
        return debugState.getReadOnlyProperty();
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

    public record UiViewportState(double hvalue, double vvalue) {
        public static final UiViewportState DEFAULT = new UiViewportState(0.0, 0.0);
    }

    public record DebugState(
            int historyIndex,
            String status,
            String trap,
            String function,
            String block,
            int stackDepth,
            boolean canStep,
            boolean canStepBack,
            UiSourceSpanDto sourceRange,
            String ir,
            String runtime,
            String stdout,
            String error,
            String notice
    ) {}
}
