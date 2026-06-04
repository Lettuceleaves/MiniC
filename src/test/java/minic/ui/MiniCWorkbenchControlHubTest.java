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
        hub.registerDebuggerCommands(debuggerCommands(startCalls));

        assertThat(hub.execute("debug.start")).isTrue();

        assertThat(startCalls).hasValue(1);
        assertThat(trackingCalls).hasValue(1);
    }

    @Test
    void disabledCompilerCommandDoesNotRunOrTrack() {
        MiniCWorkbenchControlHub hub = new MiniCWorkbenchControlHub();
        AtomicInteger nextCalls = new AtomicInteger();
        AtomicInteger trackingCalls = new AtomicInteger();
        AtomicBoolean enabled = new AtomicBoolean(false);
        hub.setActiveTrackingAction(trackingCalls::incrementAndGet);
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
