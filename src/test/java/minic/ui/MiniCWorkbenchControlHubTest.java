package minic.ui;

import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import minic.ui.control.MiniCControlTargetType;
import minic.ui.control.MiniCViewportAdapter;
import minic.ui.control.MiniCWorkbenchControlHub;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCWorkbenchControlHubTest {
    @Test
    void registersDebuggerCompilerAndSettingsCommandIds() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();

        hub.registerDebuggerCommands(debuggerCommands(new AtomicInteger()));
        hub.registerCompilerCommands(compilerCommands(new AtomicInteger(), () -> true));
        hub.registerSettingsCommands(settingsCommands(new AtomicReference<>(), new AtomicLong(100)));

        assertThat(hub.commandIds()).contains(
                "debug.start",
                "debug.runToEnd",
                "debug.runToBreakpoint",
                "debug.stepOver",
                "debug.stepInto",
                "debug.backToBreakpoint",
                "debug.stepBackOver",
                "debug.stepBack",
                "compiler.next",
                "compiler.nextStage",
                "compiler.runToExecution",
                "compiler.play",
                "compiler.playFast",
                "compiler.pause",
                "settings.theme.set",
                "settings.frameInterval.set",
                "settings.frameInterval.increase",
                "settings.frameInterval.decrease"
        );
    }

    @Test
    void successfulDebuggerCommandRunsActionAndActiveTracking() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        AtomicInteger startCalls = new AtomicInteger();
        AtomicInteger trackingCalls = new AtomicInteger();
        hub.setActiveTrackingAction(trackingCalls::incrementAndGet);
        hub.setActiveTrackingScheduler(Runnable::run);
        hub.registerDebuggerCommands(debuggerCommands(startCalls));

        assertThat(hub.execute("debug.start")).isTrue();

        assertThat(startCalls).hasValue(1);
        assertThat(trackingCalls).hasValue(1);
    }

    @Test
    void everyDebuggerCommandRunsItsMappedAction() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        AtomicInteger trackingCalls = new AtomicInteger();
        AtomicInteger startCalls = new AtomicInteger();
        AtomicInteger runToEndCalls = new AtomicInteger();
        AtomicInteger runToBreakpointCalls = new AtomicInteger();
        AtomicInteger stepOverCalls = new AtomicInteger();
        AtomicInteger stepIntoCalls = new AtomicInteger();
        AtomicInteger backToBreakpointCalls = new AtomicInteger();
        AtomicInteger stepBackOverCalls = new AtomicInteger();
        AtomicInteger stepBackCalls = new AtomicInteger();
        hub.setActiveTrackingAction(trackingCalls::incrementAndGet);
        hub.setActiveTrackingScheduler(Runnable::run);
        hub.registerDebuggerCommands(new MiniCWorkbenchControlHub.DebuggerCommands(
                () -> true,
                startCalls::incrementAndGet,
                () -> true,
                runToEndCalls::incrementAndGet,
                () -> true,
                runToBreakpointCalls::incrementAndGet,
                () -> true,
                stepOverCalls::incrementAndGet,
                () -> true,
                stepIntoCalls::incrementAndGet,
                () -> true,
                backToBreakpointCalls::incrementAndGet,
                () -> true,
                stepBackOverCalls::incrementAndGet,
                () -> true,
                stepBackCalls::incrementAndGet
        ));

        assertThat(hub.execute("debug.start")).isTrue();
        assertThat(hub.execute("debug.runToEnd")).isTrue();
        assertThat(hub.execute("debug.runToBreakpoint")).isTrue();
        assertThat(hub.execute("debug.stepOver")).isTrue();
        assertThat(hub.execute("debug.stepInto")).isTrue();
        assertThat(hub.execute("debug.backToBreakpoint")).isTrue();
        assertThat(hub.execute("debug.stepBackOver")).isTrue();
        assertThat(hub.execute("debug.stepBack")).isTrue();

        assertThat(startCalls).hasValue(1);
        assertThat(runToEndCalls).hasValue(1);
        assertThat(runToBreakpointCalls).hasValue(1);
        assertThat(stepOverCalls).hasValue(1);
        assertThat(stepIntoCalls).hasValue(1);
        assertThat(backToBreakpointCalls).hasValue(1);
        assertThat(stepBackOverCalls).hasValue(1);
        assertThat(stepBackCalls).hasValue(1);
        assertThat(trackingCalls).hasValue(8);
    }

    @Test
    void disabledDebuggerCommandDoesNotRunOrTrack() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        AtomicBoolean enabled = new AtomicBoolean(false);
        AtomicInteger runToEndCalls = new AtomicInteger();
        AtomicInteger trackingCalls = new AtomicInteger();
        hub.setActiveTrackingAction(trackingCalls::incrementAndGet);
        hub.setActiveTrackingScheduler(Runnable::run);
        hub.registerDebuggerCommands(new MiniCWorkbenchControlHub.DebuggerCommands(
                () -> true,
                () -> {
                },
                enabled::get,
                runToEndCalls::incrementAndGet,
                () -> true,
                () -> {
                },
                () -> true,
                () -> {
                },
                () -> true,
                () -> {
                },
                () -> true,
                () -> {
                },
                () -> true,
                () -> {
                },
                () -> true,
                () -> {
                }
        ));

        assertThat(hub.commandEnabled("debug.runToEnd")).isFalse();
        assertThat(hub.execute("debug.runToEnd")).isFalse();

        assertThat(runToEndCalls).hasValue(0);
        assertThat(trackingCalls).hasValue(0);

        enabled.set(true);
        assertThat(hub.commandEnabled("debug.runToEnd")).isTrue();
        assertThat(hub.execute("debug.runToEnd")).isTrue();
        assertThat(runToEndCalls).hasValue(1);
        assertThat(trackingCalls).hasValue(1);
    }

    @Test
    void everyDebuggerCommandUsesItsOwnEnablementSupplier() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        AtomicBoolean[] enabled = booleans(8);
        AtomicInteger[] calls = counters(8);
        hub.registerDebuggerCommands(new MiniCWorkbenchControlHub.DebuggerCommands(
                enabled[0]::get,
                calls[0]::incrementAndGet,
                enabled[1]::get,
                calls[1]::incrementAndGet,
                enabled[2]::get,
                calls[2]::incrementAndGet,
                enabled[3]::get,
                calls[3]::incrementAndGet,
                enabled[4]::get,
                calls[4]::incrementAndGet,
                enabled[5]::get,
                calls[5]::incrementAndGet,
                enabled[6]::get,
                calls[6]::incrementAndGet,
                enabled[7]::get,
                calls[7]::incrementAndGet
        ));
        String[] commandIds = {
                "debug.start",
                "debug.runToEnd",
                "debug.runToBreakpoint",
                "debug.stepOver",
                "debug.stepInto",
                "debug.backToBreakpoint",
                "debug.stepBackOver",
                "debug.stepBack"
        };

        for (int index = 0; index < commandIds.length; index++) {
            assertThat(hub.commandEnabled(commandIds[index])).isFalse();
            assertThat(hub.execute(commandIds[index])).isFalse();
            enabled[index].set(true);
            assertThat(hub.commandEnabled(commandIds[index])).isTrue();
            assertThat(hub.execute(commandIds[index])).isTrue();
            assertThat(calls[index]).hasValue(1);
            enabled[index].set(false);
        }
    }

    @Test
    void schedulesActiveTrackingAfterCommandAction() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        AtomicInteger startCalls = new AtomicInteger();
        AtomicInteger trackingCalls = new AtomicInteger();
        AtomicReference<Runnable> scheduledTracking = new AtomicReference<>();
        hub.setActiveTrackingAction(trackingCalls::incrementAndGet);
        hub.setActiveTrackingScheduler(scheduledTracking::set);
        hub.registerDebuggerCommands(debuggerCommands(startCalls));

        assertThat(hub.execute("debug.start")).isTrue();

        assertThat(startCalls).hasValue(1);
        assertThat(trackingCalls).hasValue(0);
        assertThat(scheduledTracking).hasValueSatisfying(Runnable::run);
        assertThat(trackingCalls).hasValue(1);
    }

    @Test
    void disabledCompilerCommandDoesNotRunOrTrack() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        AtomicInteger nextCalls = new AtomicInteger();
        AtomicInteger trackingCalls = new AtomicInteger();
        AtomicBoolean enabled = new AtomicBoolean(false);
        hub.setActiveTrackingAction(trackingCalls::incrementAndGet);
        hub.setActiveTrackingScheduler(Runnable::run);
        hub.registerCompilerCommands(compilerCommands(nextCalls, enabled::get));

        assertThat(hub.commandEnabled("compiler.next")).isFalse();
        assertThat(hub.execute("compiler.next")).isFalse();

        assertThat(nextCalls).hasValue(0);
        assertThat(trackingCalls).hasValue(0);

        enabled.set(true);
        assertThat(hub.commandEnabled("compiler.next")).isTrue();
        assertThat(hub.execute("compiler.next")).isTrue();
        assertThat(nextCalls).hasValue(1);
        assertThat(trackingCalls).hasValue(1);
    }

    @Test
    void everyCompilerCommandRunsItsMappedAction() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        AtomicInteger nextCalls = new AtomicInteger();
        AtomicInteger nextStageCalls = new AtomicInteger();
        AtomicInteger runToExecutionCalls = new AtomicInteger();
        AtomicInteger playCalls = new AtomicInteger();
        AtomicInteger playFastCalls = new AtomicInteger();
        AtomicInteger pauseCalls = new AtomicInteger();
        hub.registerCompilerCommands(new MiniCWorkbenchControlHub.CompilerCommands(
                () -> true,
                nextCalls::incrementAndGet,
                () -> true,
                nextStageCalls::incrementAndGet,
                () -> true,
                runToExecutionCalls::incrementAndGet,
                () -> true,
                playCalls::incrementAndGet,
                () -> true,
                playFastCalls::incrementAndGet,
                () -> true,
                pauseCalls::incrementAndGet
        ));

        assertThat(hub.execute("compiler.next")).isTrue();
        assertThat(hub.execute("compiler.nextStage")).isTrue();
        assertThat(hub.execute("compiler.runToExecution")).isTrue();
        assertThat(hub.execute("compiler.play")).isTrue();
        assertThat(hub.execute("compiler.playFast")).isTrue();
        assertThat(hub.execute("compiler.pause")).isTrue();

        assertThat(nextCalls).hasValue(1);
        assertThat(nextStageCalls).hasValue(1);
        assertThat(runToExecutionCalls).hasValue(1);
        assertThat(playCalls).hasValue(1);
        assertThat(playFastCalls).hasValue(1);
        assertThat(pauseCalls).hasValue(1);
    }

    @Test
    void everyCompilerCommandUsesItsOwnEnablementSupplier() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        AtomicBoolean[] enabled = booleans(6);
        AtomicInteger[] calls = counters(6);
        hub.registerCompilerCommands(new MiniCWorkbenchControlHub.CompilerCommands(
                enabled[0]::get,
                calls[0]::incrementAndGet,
                enabled[1]::get,
                calls[1]::incrementAndGet,
                enabled[2]::get,
                calls[2]::incrementAndGet,
                enabled[3]::get,
                calls[3]::incrementAndGet,
                enabled[4]::get,
                calls[4]::incrementAndGet,
                enabled[5]::get,
                calls[5]::incrementAndGet
        ));
        String[] commandIds = {
                "compiler.next",
                "compiler.nextStage",
                "compiler.runToExecution",
                "compiler.play",
                "compiler.playFast",
                "compiler.pause"
        };

        for (int index = 0; index < commandIds.length; index++) {
            assertThat(hub.commandEnabled(commandIds[index])).isFalse();
            assertThat(hub.execute(commandIds[index])).isFalse();
            enabled[index].set(true);
            assertThat(hub.commandEnabled(commandIds[index])).isTrue();
            assertThat(hub.execute(commandIds[index])).isTrue();
            assertThat(calls[index]).hasValue(1);
            enabled[index].set(false);
        }
    }

    @Test
    void settingsCommandsSetThemeAndClampFrameIntervalChanges() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        AtomicReference<String> selectedTheme = new AtomicReference<>();
        AtomicLong frameInterval = new AtomicLong(100);
        hub.registerSettingsCommands(settingsCommands(selectedTheme, frameInterval));

        assertThat(hub.setTheme("light")).isTrue();
        assertThat(selectedTheme).hasValue("light");

        assertThat(hub.setFrameIntervalMillis(1200)).isTrue();
        assertThat(frameInterval).hasValue(1000);

        assertThat(hub.decreaseFrameInterval()).isTrue();
        assertThat(frameInterval).hasValue(950);

        frameInterval.set(20);
        assertThat(hub.decreaseFrameInterval()).isTrue();
        assertThat(frameInterval).hasValue(1);

        frameInterval.set(980);
        assertThat(hub.increaseFrameInterval()).isTrue();
        assertThat(frameInterval).hasValue(1000);
    }

    @Test
    void viewportOperationsDelegateToCurrentTargetCapabilities() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        FakeViewportAdapter adapter = new FakeViewportAdapter();
        hub.viewportRegistry().pin(adapter);

        hub.handleZoom(new Point2D(10, 20), 0.25);
        hub.handleScrollVertical(12);
        hub.handleScrollHorizontal(-4);
        hub.handlePan(5, 6);

        assertThat(adapter.zoomCalls).isOne();
        assertThat(adapter.lastZoomPoint).isEqualTo(new Point2D(10, 20));
        assertThat(adapter.lastZoomDelta).isEqualTo(0.25);
        assertThat(adapter.verticalDelta).isEqualTo(12);
        assertThat(adapter.horizontalDelta).isEqualTo(-4);
        assertThat(adapter.panDeltaX).isEqualTo(5);
        assertThat(adapter.panDeltaY).isEqualTo(6);
    }

    @Test
    void installedViewportTargetUpdatesHoverAndPinnedTargetsFromMouseEvents() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        Pane node = new Pane();
        FakeViewportAdapter adapter = new FakeViewportAdapter();
        hub.installViewportTarget(node, adapter);

        node.fireEvent(new MouseEvent(
                MouseEvent.MOUSE_ENTERED,
                1, 1, 1, 1,
                MouseButton.NONE,
                0,
                false, false, false, false,
                false, false, false, false,
                false, false, null
        ));
        assertThat(hub.viewportRegistry().currentTarget()).containsSame(adapter);

        node.fireEvent(new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                1, 1, 1, 1,
                MouseButton.PRIMARY,
                1,
                false, false, false, false,
                true, false, false, true,
                false, false, null
        ));
        node.fireEvent(new MouseEvent(
                MouseEvent.MOUSE_EXITED,
                1, 1, 1, 1,
                MouseButton.NONE,
                0,
                false, false, false, false,
                false, false, false, false,
                false, false, null
        ));

        assertThat(hub.viewportRegistry().currentTarget()).containsSame(adapter);
    }

    private static MiniCWorkbenchControlHub.DebuggerCommands debuggerCommands(AtomicInteger firstCommandCalls) {
        return new MiniCWorkbenchControlHub.DebuggerCommands(
                firstCommandCalls::incrementAndGet,
                () -> {
                },
                () -> {
                },
                () -> {
                },
                () -> {
                },
                () -> {
                },
                () -> {
                },
                () -> {
                }
        );
    }

    private static MiniCWorkbenchControlHub.CompilerCommands compilerCommands(
            AtomicInteger firstCommandCalls,
            java.util.function.BooleanSupplier firstCommandEnabled
    ) {
        return new MiniCWorkbenchControlHub.CompilerCommands(
                firstCommandEnabled,
                firstCommandCalls::incrementAndGet,
                () -> true,
                () -> {
                },
                () -> true,
                () -> {
                },
                () -> true,
                () -> {
                },
                () -> true,
                () -> {
                },
                () -> true,
                () -> {
                }
        );
    }

    private static MiniCWorkbenchControlHub.SettingsCommands settingsCommands(
            AtomicReference<String> selectedTheme,
            AtomicLong frameInterval
    ) {
        return new MiniCWorkbenchControlHub.SettingsCommands(
                selectedTheme::set,
                frameInterval::set,
                frameInterval::get,
                () -> 1,
                () -> 1000,
                50
        );
    }

    private static AtomicBoolean[] booleans(int count) {
        AtomicBoolean[] result = new AtomicBoolean[count];
        for (int index = 0; index < count; index++) {
            result[index] = new AtomicBoolean(false);
        }
        return result;
    }

    private static AtomicInteger[] counters(int count) {
        AtomicInteger[] result = new AtomicInteger[count];
        for (int index = 0; index < count; index++) {
            result[index] = new AtomicInteger();
        }
        return result;
    }

    private static final class FakeViewportAdapter implements MiniCViewportAdapter {
        private int zoomCalls;
        private Point2D lastZoomPoint;
        private double lastZoomDelta;
        private double verticalDelta;
        private double horizontalDelta;
        private double panDeltaX;
        private double panDeltaY;

        @Override
        public MiniCControlTargetType type() {
            return MiniCControlTargetType.GRAPH;
        }

        @Override
        public boolean canZoom() {
            return true;
        }

        @Override
        public void zoomAt(Point2D localPoint, double delta) {
            zoomCalls++;
            lastZoomPoint = localPoint;
            lastZoomDelta = delta;
        }

        @Override
        public boolean canScrollVertical() {
            return true;
        }

        @Override
        public void scrollVertical(double delta) {
            verticalDelta = delta;
        }

        @Override
        public boolean canScrollHorizontal() {
            return true;
        }

        @Override
        public void scrollHorizontal(double delta) {
            horizontalDelta = delta;
        }

        @Override
        public boolean canPan() {
            return true;
        }

        @Override
        public void pan(double deltaX, double deltaY) {
            panDeltaX = deltaX;
            panDeltaY = deltaY;
        }
    }
}
