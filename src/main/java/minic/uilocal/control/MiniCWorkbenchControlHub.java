package minic.uilocal.control;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Workbench-level facade for command execution and viewport routing.
 */
public final class MiniCWorkbenchControlHub {
    private static final String VIEWPORT_TARGET_PROPERTY = "minic.uilocal.control.viewportTargetAdapter";
    public static final String DEBUG_START = "debug.start";
    public static final String DEBUG_RUN_TO_END = "debug.runToEnd";
    public static final String DEBUG_RUN_TO_BREAKPOINT = "debug.runToBreakpoint";
    public static final String DEBUG_STEP_OVER = "debug.stepOver";
    public static final String DEBUG_STEP_INTO = "debug.stepInto";
    public static final String DEBUG_BACK_TO_BREAKPOINT = "debug.backToBreakpoint";
    public static final String DEBUG_STEP_BACK_OVER = "debug.stepBackOver";
    public static final String DEBUG_STEP_BACK = "debug.stepBack";
    public static final String COMPILER_NEXT = "compiler.next";
    public static final String COMPILER_NEXT_STAGE = "compiler.nextStage";
    public static final String COMPILER_RUN_TO_EXECUTION = "compiler.runToExecution";
    public static final String COMPILER_PLAY = "compiler.play";
    public static final String COMPILER_PLAY_FAST = "compiler.playFast";
    public static final String COMPILER_PAUSE = "compiler.pause";
    public static final String SETTINGS_THEME_SET = "settings.theme.set";
    public static final String SETTINGS_THEME_NEXT = "settings.theme.next";
    public static final String SETTINGS_THEME_PREVIOUS = "settings.theme.previous";
    public static final String SETTINGS_FRAME_INTERVAL_SET = "settings.frameInterval.set";
    public static final String SETTINGS_FRAME_INTERVAL_INCREASE = "settings.frameInterval.increase";
    public static final String SETTINGS_FRAME_INTERVAL_DECREASE = "settings.frameInterval.decrease";
    public static final String SETTINGS_UI_SCALE_INCREASE = "settings.uiScale.increase";
    public static final String SETTINGS_UI_SCALE_DECREASE = "settings.uiScale.decrease";
    public static final String VIEWPORT_ZOOM_IN = "viewport.zoom.in";
    public static final String VIEWPORT_ZOOM_OUT = "viewport.zoom.out";
    public static final String VIEWPORT_SCROLL_UP = "viewport.scroll.up";
    public static final String VIEWPORT_SCROLL_DOWN = "viewport.scroll.down";
    public static final String VIEWPORT_SCROLL_LEFT = "viewport.scroll.left";
    public static final String VIEWPORT_SCROLL_RIGHT = "viewport.scroll.right";
    public static final String VIEWPORT_CENTER_ACTIVE = "viewport.centerActive";

    private final MiniCCommandRegistry commandRegistry;
    private final MiniCViewportRegistry viewportRegistry;
    private final Set<String> commandIds = new LinkedHashSet<>();
    private final List<Runnable> additionalActiveTrackingActions = new ArrayList<>();
    private Runnable activeTrackingAction = () -> {
    };
    private Consumer<Runnable> activeTrackingScheduler = MiniCWorkbenchControlHub::runLaterIfPossible;
    private String pendingThemeName;
    private Long pendingFrameIntervalMillis;

    /**
     * Creates a hub with fresh command and viewport registries.
     */
    public MiniCWorkbenchControlHub() {
        this(new MiniCCommandRegistry(), new MiniCViewportRegistry());
    }

    /**
     * Creates a hub around existing registries.
     *
     * @param commandRegistry command registry to wrap
     * @param viewportRegistry viewport registry to wrap
     */
    public MiniCWorkbenchControlHub(
            MiniCCommandRegistry commandRegistry,
            MiniCViewportRegistry viewportRegistry
    ) {
        this.commandRegistry = Objects.requireNonNull(commandRegistry, "commandRegistry");
        this.viewportRegistry = Objects.requireNonNull(viewportRegistry, "viewportRegistry");
    }

    public MiniCCommandRegistry commandRegistry() {
        return commandRegistry;
    }

    public MiniCViewportRegistry viewportRegistry() {
        return viewportRegistry;
    }

    public Set<String> commandIds() {
        return Collections.unmodifiableSet(commandIds);
    }

    public void setActiveTrackingAction(Runnable activeTrackingAction) {
        this.activeTrackingAction = Objects.requireNonNull(activeTrackingAction, "activeTrackingAction");
    }

    public void setActiveTrackingScheduler(Consumer<Runnable> activeTrackingScheduler) {
        this.activeTrackingScheduler = Objects.requireNonNull(activeTrackingScheduler, "activeTrackingScheduler");
    }

    public void addActiveTrackingAction(Runnable activeTrackingAction) {
        additionalActiveTrackingActions.add(Objects.requireNonNull(activeTrackingAction, "activeTrackingAction"));
    }

    public void registerDebuggerCommands(DebuggerCommands commands) {
        Objects.requireNonNull(commands, "commands");
        register(DEBUG_START, "从头开始", commands.canStart(), commands.start());
        register(DEBUG_RUN_TO_END, "运行到结束", commands.canRunToEnd(), commands.runToEnd());
        register(DEBUG_RUN_TO_BREAKPOINT, "下个断点", commands.canRunToBreakpoint(), commands.runToBreakpoint());
        register(DEBUG_STEP_OVER, "本层下一句", commands.canStepOver(), commands.stepOver());
        register(DEBUG_STEP_INTO, "下一句", commands.canStepInto(), commands.stepInto());
        register(DEBUG_BACK_TO_BREAKPOINT, "上个断点", commands.canBackToBreakpoint(), commands.backToBreakpoint());
        register(DEBUG_STEP_BACK_OVER, "本层上一句", commands.canStepBackOver(), commands.stepBackOver());
        register(DEBUG_STEP_BACK, "上一句", commands.canStepBack(), commands.stepBack());
    }

    public void registerCompilerCommands(CompilerCommands commands) {
        Objects.requireNonNull(commands, "commands");
        register(COMPILER_NEXT, "下一步", commands.canNext(), commands.next());
        register(COMPILER_NEXT_STAGE, "下一阶段", commands.canNextStage(), commands.nextStage());
        register(COMPILER_RUN_TO_EXECUTION, "到执行", commands.canRunToExecution(), commands.runToExecution());
        register(COMPILER_PLAY, "播放", commands.canPlay(), commands.play());
        register(COMPILER_PLAY_FAST, "2x", commands.canPlayFast(), commands.playFast());
        register(COMPILER_PAUSE, "暂停", commands.canPause(), commands.pause());
    }

    public void registerSettingsCommands(SettingsCommands commands) {
        Objects.requireNonNull(commands, "commands");
        register(SETTINGS_THEME_SET, "设置主题", () -> pendingThemeName != null, () ->
                commands.themeSetter().accept(pendingThemeName));
        register(SETTINGS_THEME_NEXT, "下一个主题", () -> true, commands.themeNext());
        register(SETTINGS_THEME_PREVIOUS, "上一个主题", () -> true, commands.themePrevious());
        register(SETTINGS_FRAME_INTERVAL_SET, "设置帧间隔", () -> pendingFrameIntervalMillis != null, () ->
                commands.frameIntervalSetter().accept(clamp(
                        pendingFrameIntervalMillis,
                        commands.minFrameInterval(),
                        commands.maxFrameInterval()
                )));
        register(SETTINGS_FRAME_INTERVAL_INCREASE, "增加帧间隔", () -> true, () ->
                commands.frameIntervalSetter().accept(clamp(
                        commands.currentFrameInterval().getAsLong() + commands.frameIntervalStep(),
                        commands.minFrameInterval(),
                        commands.maxFrameInterval()
                )));
        register(SETTINGS_FRAME_INTERVAL_DECREASE, "减少帧间隔", () -> true, () ->
                commands.frameIntervalSetter().accept(clamp(
                        commands.currentFrameInterval().getAsLong() - commands.frameIntervalStep(),
                        commands.minFrameInterval(),
                        commands.maxFrameInterval()
                )));
        register(SETTINGS_UI_SCALE_INCREASE, "增加全局缩放", () -> true, () ->
                commands.uiScaleSetter().accept(clamp(
                        commands.currentUiScale().getAsDouble() + commands.uiScaleStep(),
                        commands.minUiScale(),
                        commands.maxUiScale()
                )));
        register(SETTINGS_UI_SCALE_DECREASE, "减少全局缩放", () -> true, () ->
                commands.uiScaleSetter().accept(clamp(
                        commands.currentUiScale().getAsDouble() - commands.uiScaleStep(),
                        commands.minUiScale(),
                        commands.maxUiScale()
                )));
    }

    public boolean commandEnabled(String commandId) {
        return commandRegistry.enabled(commandId);
    }

    public boolean execute(String commandId) {
        boolean executed = commandRegistry.execute(commandId);
        if (executed) {
            activeTrackingScheduler.accept(this::trackActiveViews);
        }
        return executed;
    }

    public boolean setTheme(String themeName) {
        pendingThemeName = Objects.requireNonNull(themeName, "themeName");
        try {
            return execute(SETTINGS_THEME_SET);
        } finally {
            pendingThemeName = null;
        }
    }

    public boolean setFrameIntervalMillis(long millis) {
        pendingFrameIntervalMillis = millis;
        try {
            return execute(SETTINGS_FRAME_INTERVAL_SET);
        } finally {
            pendingFrameIntervalMillis = null;
        }
    }

    public boolean increaseFrameInterval() {
        return execute(SETTINGS_FRAME_INTERVAL_INCREASE);
    }

    public boolean decreaseFrameInterval() {
        return execute(SETTINGS_FRAME_INTERVAL_DECREASE);
    }

    public boolean increaseUiScale() {
        return execute(SETTINGS_UI_SCALE_INCREASE);
    }

    public boolean decreaseUiScale() {
        return execute(SETTINGS_UI_SCALE_DECREASE);
    }

    public void handleZoom(Point2D localPoint, double delta) {
        Objects.requireNonNull(localPoint, "localPoint");
        viewportRegistry.currentTarget()
                .filter(MiniCViewportAdapter::canZoom)
                .ifPresent(adapter -> adapter.zoomAt(localPoint, delta));
    }

    public void handleScrollVertical(double delta) {
        viewportRegistry.currentTarget()
                .filter(MiniCViewportAdapter::canScrollVertical)
                .ifPresent(adapter -> adapter.scrollVertical(delta));
    }

    public void handleScrollHorizontal(double delta) {
        viewportRegistry.currentTarget()
                .filter(MiniCViewportAdapter::canScrollHorizontal)
                .ifPresent(adapter -> adapter.scrollHorizontal(delta));
    }

    public void handlePan(double deltaX, double deltaY) {
        viewportRegistry.currentTarget()
                .filter(MiniCViewportAdapter::canPan)
                .ifPresent(adapter -> adapter.pan(deltaX, deltaY));
    }

    public void handleCenterActive() {
        viewportRegistry.currentTarget().ifPresent(MiniCViewportAdapter::centerActive);
    }

    public void installViewportTarget(Node node, MiniCViewportAdapter adapter) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(adapter, "adapter");
        if (node.getProperties().get(VIEWPORT_TARGET_PROPERTY) == adapter) {
            return;
        }
        node.getProperties().put(VIEWPORT_TARGET_PROPERTY, adapter);
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> viewportRegistry.hover(adapter));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> viewportRegistry.clearHover(adapter));
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                viewportRegistry.pin(adapter);
            }
        });
    }

    private void register(String id, String label, BooleanSupplier enabled, Runnable action) {
        commandRegistry.register(new MiniCControlCommand(id, label, enabled, action));
        commandIds.add(id);
    }

    private void trackActiveViews() {
        activeTrackingAction.run();
        additionalActiveTrackingActions.forEach(Runnable::run);
    }

    private static void runLaterIfPossible(Runnable action) {
        try {
            Platform.runLater(action);
        } catch (IllegalStateException ignored) {
            action.run();
        }
    }

    private static long clamp(long value, LongSupplier minSupplier, LongSupplier maxSupplier) {
        long min = minSupplier.getAsLong();
        long max = maxSupplier.getAsLong();
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, DoubleSupplier minSupplier, DoubleSupplier maxSupplier) {
        double min = minSupplier.getAsDouble();
        double max = maxSupplier.getAsDouble();
        return Math.max(min, Math.min(max, value));
    }

    public record DebuggerCommands(
            BooleanSupplier canStart,
            Runnable start,
            BooleanSupplier canRunToEnd,
            Runnable runToEnd,
            BooleanSupplier canRunToBreakpoint,
            Runnable runToBreakpoint,
            BooleanSupplier canStepOver,
            Runnable stepOver,
            BooleanSupplier canStepInto,
            Runnable stepInto,
            BooleanSupplier canBackToBreakpoint,
            Runnable backToBreakpoint,
            BooleanSupplier canStepBackOver,
            Runnable stepBackOver,
            BooleanSupplier canStepBack,
            Runnable stepBack
    ) {
        public DebuggerCommands(
                Runnable start,
                Runnable runToEnd,
                Runnable runToBreakpoint,
                Runnable stepOver,
                Runnable stepInto,
                Runnable backToBreakpoint,
                Runnable stepBackOver,
                Runnable stepBack
        ) {
            this(
                    () -> true,
                    start,
                    () -> true,
                    runToEnd,
                    () -> true,
                    runToBreakpoint,
                    () -> true,
                    stepOver,
                    () -> true,
                    stepInto,
                    () -> true,
                    backToBreakpoint,
                    () -> true,
                    stepBackOver,
                    () -> true,
                    stepBack
            );
        }

        public DebuggerCommands {
            Objects.requireNonNull(canStart, "canStart");
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(canRunToEnd, "canRunToEnd");
            Objects.requireNonNull(runToEnd, "runToEnd");
            Objects.requireNonNull(canRunToBreakpoint, "canRunToBreakpoint");
            Objects.requireNonNull(runToBreakpoint, "runToBreakpoint");
            Objects.requireNonNull(canStepOver, "canStepOver");
            Objects.requireNonNull(stepOver, "stepOver");
            Objects.requireNonNull(canStepInto, "canStepInto");
            Objects.requireNonNull(stepInto, "stepInto");
            Objects.requireNonNull(canBackToBreakpoint, "canBackToBreakpoint");
            Objects.requireNonNull(backToBreakpoint, "backToBreakpoint");
            Objects.requireNonNull(canStepBackOver, "canStepBackOver");
            Objects.requireNonNull(stepBackOver, "stepBackOver");
            Objects.requireNonNull(canStepBack, "canStepBack");
            Objects.requireNonNull(stepBack, "stepBack");
        }
    }

    public record CompilerCommands(
            BooleanSupplier canNext,
            Runnable next,
            BooleanSupplier canNextStage,
            Runnable nextStage,
            BooleanSupplier canRunToExecution,
            Runnable runToExecution,
            BooleanSupplier canPlay,
            Runnable play,
            BooleanSupplier canPlayFast,
            Runnable playFast,
            BooleanSupplier canPause,
            Runnable pause
    ) {
        public CompilerCommands {
            Objects.requireNonNull(canNext, "canNext");
            Objects.requireNonNull(next, "next");
            Objects.requireNonNull(canNextStage, "canNextStage");
            Objects.requireNonNull(nextStage, "nextStage");
            Objects.requireNonNull(canRunToExecution, "canRunToExecution");
            Objects.requireNonNull(runToExecution, "runToExecution");
            Objects.requireNonNull(canPlay, "canPlay");
            Objects.requireNonNull(play, "play");
            Objects.requireNonNull(canPlayFast, "canPlayFast");
            Objects.requireNonNull(playFast, "playFast");
            Objects.requireNonNull(canPause, "canPause");
            Objects.requireNonNull(pause, "pause");
        }
    }

    public record SettingsCommands(
            Consumer<String> themeSetter,
            Runnable themeNext,
            Runnable themePrevious,
            LongConsumer frameIntervalSetter,
            LongSupplier currentFrameInterval,
            LongSupplier minFrameInterval,
            LongSupplier maxFrameInterval,
            long frameIntervalStep,
            DoubleConsumer uiScaleSetter,
            DoubleSupplier currentUiScale,
            DoubleSupplier minUiScale,
            DoubleSupplier maxUiScale,
            double uiScaleStep
    ) {
        public SettingsCommands {
            Objects.requireNonNull(themeSetter, "themeSetter");
            Objects.requireNonNull(themeNext, "themeNext");
            Objects.requireNonNull(themePrevious, "themePrevious");
            Objects.requireNonNull(frameIntervalSetter, "frameIntervalSetter");
            Objects.requireNonNull(currentFrameInterval, "currentFrameInterval");
            Objects.requireNonNull(minFrameInterval, "minFrameInterval");
            Objects.requireNonNull(maxFrameInterval, "maxFrameInterval");
            Objects.requireNonNull(uiScaleSetter, "uiScaleSetter");
            Objects.requireNonNull(currentUiScale, "currentUiScale");
            Objects.requireNonNull(minUiScale, "minUiScale");
            Objects.requireNonNull(maxUiScale, "maxUiScale");
            if (frameIntervalStep < 1) {
                throw new IllegalArgumentException("frameIntervalStep must be positive");
            }
            if (uiScaleStep <= 0) {
                throw new IllegalArgumentException("uiScaleStep must be positive");
            }
        }
    }
}
