package minic.uilocal;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import minic.uilocal.control.MiniCWorkbenchControlHub;

import java.util.List;

/** 基于 DebugApi 的本地调试面板；高级控制全部由 UI 层组合 next/previous 实现。 */
public final class MiniCDebugPane extends VBox {
    private static final List<String> SHORTCUT_ACTIONS = List.of(
            MiniCWorkbenchControlHub.DEBUG_START,
            MiniCWorkbenchControlHub.DEBUG_RUN_TO_END,
            MiniCWorkbenchControlHub.DEBUG_RUN_TO_BREAKPOINT,
            MiniCWorkbenchControlHub.DEBUG_STEP_OVER,
            MiniCWorkbenchControlHub.DEBUG_STEP,
            MiniCWorkbenchControlHub.DEBUG_STEP_OUT,
            MiniCWorkbenchControlHub.DEBUG_BACK_TO_BREAKPOINT,
            MiniCWorkbenchControlHub.DEBUG_BACK_TO_CALL_SITE,
            MiniCWorkbenchControlHub.DEBUG_STEP_BACK_OVER,
            MiniCWorkbenchControlHub.DEBUG_STEP_BACK,
            MiniCWorkbenchControlHub.DEBUG_PAUSE
    );

    private final MiniCWorkbenchViewModel model;
    private final MiniCSourceLoaderView source;
    private final MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
    private final Label status = new Label();
    private final TextArea ir = area(), runtime = area(), output = area();
    private final Timeline runner;
    private final Button runToEnd;
    private final Button runToBreakpoint;
    private final Button stepOver;
    private final Button step;
    private final Button stepOut;
    private final Button backToBreakpoint;
    private final Button backToCallSite;
    private final Button stepBackOver;
    private final Button stepBack;
    private final Button pause;
    private RunMode runMode;
    private int runStartDepth;
    private boolean sourceDirty;

    public MiniCDebugPane(MiniCWorkbenchViewModel model) {
        this.model = model;
        source = new MiniCSourceLoaderView(model, false);
        runner = new Timeline(new KeyFrame(Duration.millis(4), event -> runTick()));
        runner.setCycleCount(Timeline.INDEFINITE);
        var keys = MiniCKeyBindingConfig.loadDefault();
        hub.registerDebuggerCommands(new MiniCWorkbenchControlHub.DebuggerCommands(
                () -> true, () -> invoke(this::startDebug),
                this::canStep, () -> invoke(model::debugStep),
                this::canStepBack, () -> invoke(model::debugStepBack),
                () -> startRunner(RunMode.END),
                () -> startRunner(RunMode.BREAKPOINT),
                () -> startRunner(RunMode.OVER),
                () -> startRunner(RunMode.OUT),
                () -> startRunner(RunMode.BACK_OVER),
                () -> startRunner(RunMode.BACK_BREAKPOINT),
                () -> startRunner(RunMode.BACK_CALL),
                this::running, this::pauseRunner
        ));

        Button start = button("重新开始", MiniCWorkbenchControlHub.DEBUG_START);
        Button close = new Button("关闭");
        close.setOnAction(event -> {
            pauseRunner();
            model.debugClose();
        });
        runToEnd = button("运行到结束", MiniCWorkbenchControlHub.DEBUG_RUN_TO_END);
        runToBreakpoint = button("运行到断点", MiniCWorkbenchControlHub.DEBUG_RUN_TO_BREAKPOINT);
        stepOver = button("步过", MiniCWorkbenchControlHub.DEBUG_STEP_OVER);
        step = button("步入", MiniCWorkbenchControlHub.DEBUG_STEP);
        stepOut = button("步出", MiniCWorkbenchControlHub.DEBUG_STEP_OUT);
        backToBreakpoint = button("返回断点", MiniCWorkbenchControlHub.DEBUG_BACK_TO_BREAKPOINT);
        backToCallSite = button("返回调用处", MiniCWorkbenchControlHub.DEBUG_BACK_TO_CALL_SITE);
        stepBackOver = button("反向步过", MiniCWorkbenchControlHub.DEBUG_STEP_BACK_OVER);
        stepBack = button("反向步入", MiniCWorkbenchControlHub.DEBUG_STEP_BACK);
        pause = button("暂停", MiniCWorkbenchControlHub.DEBUG_PAUSE);

        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            for (String action : SHORTCUT_ACTIONS) {
                if (keys.matches(action, event)) {
                    hub.execute(action);
                    event.consume();
                    break;
                }
            }
        });

        FlowPane controls = new FlowPane(8, 6,
                start, close, runToEnd, runToBreakpoint, pause,
                backToBreakpoint, backToCallSite, stepBackOver, stepBack,
                stepOver, step, stepOut);
        TabPane views = new TabPane(tab("IR / 代码区", ir), tab("调用栈 / 内存", runtime), tab("输出", output));
        SplitPane body = new SplitPane(source, views);
        body.setDividerPositions(0.45);
        getChildren().addAll(controls, status, body);
        VBox.setVgrow(body, Priority.ALWAYS);

        model.debugStateProperty().addListener((observable, before, after) -> refresh());
        model.debugBreakpointLinesProperty().addListener((observable, before, after) -> refresh());
        source.setSourceChangeAction(() -> {
            sourceDirty = true;
            refresh();
        });
        refresh();
    }

    private void startDebug() {
        pauseRunner();
        source.loadCurrentSource();
        model.startDebug();
        sourceDirty = false;
        refresh();
    }

    private boolean canStep() {
        var state = model.debugStateProperty().get();
        return !running() && !sourceDirty && state != null && state.canStep();
    }

    private boolean canStepBack() {
        var state = model.debugStateProperty().get();
        return !running() && !sourceDirty && state != null && state.canStepBack();
    }

    private boolean running() {
        return runner.getStatus() == Animation.Status.RUNNING;
    }

    private void startRunner(RunMode mode) {
        if (mode.backward ? !canStepBack() : !canStep()) return;
        runMode = mode;
        runStartDepth = model.debugStateProperty().get().stackDepth();
        runner.playFromStart();
        refresh();
    }

    private void runTick() {
        var before = model.debugStateProperty().get();
        if (sourceDirty || before == null
                || (runMode.backward ? !before.canStepBack() : !before.canStep())) {
            pauseRunner();
            return;
        }
        invoke(runMode.backward ? model::debugStepBack : model::debugStep);
        var state = model.debugStateProperty().get();
        boolean breakpoint = state != null && state.sourceRange() != null && "LINE".equals(state.trap())
                && model.debugBreakpointLinesProperty().get().contains(state.sourceRange().startLine());
        boolean stop = state == null
                || (runMode.backward ? !state.canStepBack() : !state.canStep());
        stop |= switch (runMode) {
            case END -> false;
            case BREAKPOINT, BACK_BREAKPOINT -> breakpoint;
            case OVER, BACK_OVER -> state != null && state.stackDepth() <= runStartDepth;
            case OUT -> state != null && state.stackDepth() < runStartDepth;
            case BACK_CALL -> state != null && "CALL".equals(state.trap())
                    && state.stackDepth() <= runStartDepth;
        };
        if (stop) {
            pauseRunner();
        }
    }

    private void pauseRunner() {
        runner.stop();
        refresh();
    }

    private Button button(String label, String action) {
        Button button = new Button(label);
        button.setOnAction(event -> hub.execute(action));
        return button;
    }

    private void invoke(Runnable action) {
        try { action.run(); }
        catch (RuntimeException error) { status.setText(error.getMessage()); }
    }

    private void refresh() {
        var state = model.debugStateProperty().get();
        boolean forward = !running() && !sourceDirty && state != null && state.canStep();
        boolean backward = !running() && !sourceDirty && state != null && state.canStepBack();
        runToEnd.setDisable(!forward);
        runToBreakpoint.setDisable(!forward || model.debugBreakpointLinesProperty().get().isEmpty());
        stepOver.setDisable(!forward);
        step.setDisable(!forward);
        stepOut.setDisable(!forward || state == null || state.stackDepth() < 2);
        backToBreakpoint.setDisable(!backward || model.debugBreakpointLinesProperty().get().isEmpty());
        backToCallSite.setDisable(!backward);
        stepBackOver.setDisable(!backward);
        stepBack.setDisable(!backward);
        pause.setDisable(!running());

        source.setCurrentExecutionRange(state == null ? null : state.sourceRange());
        if (state == null) {
            status.setText(sourceDirty ? "源码已修改，请重新开始" : "点击重新开始，编译到 IR");
            ir.clear(); runtime.clear(); output.clear();
            return;
        }
        String location = state.sourceRange() == null ? "" : " 行 " + state.sourceRange().startLine();
        String notice = state.notice().isBlank() ? "" : " · " + state.notice();
        status.setText((running() ? "运行中 · " : "") + "历史 #" + state.historyIndex() + " · " + state.status() + " " + state.trap()
                + location + " · " + state.function() + "/" + state.block()
                + " · 栈深 " + state.stackDepth() + " · 断点 "
                + model.debugBreakpointLinesProperty().get().size() + notice);
        ir.setText(state.ir());
        runtime.setText(state.runtime());
        output.setText(state.stdout());
    }

    private static TextArea area() {
        TextArea text = new TextArea();
        text.setEditable(false);
        text.setStyle("-fx-font-family: monospace;");
        return text;
    }

    private static Tab tab(String title, TextArea text) {
        Tab tab = new Tab(title, text);
        tab.setClosable(false);
        return tab;
    }

    private enum RunMode {
        END(false), BREAKPOINT(false), OVER(false), OUT(false),
        BACK_BREAKPOINT(true), BACK_OVER(true), BACK_CALL(true);

        private final boolean backward;

        RunMode(boolean backward) { this.backward = backward; }
    }
}
