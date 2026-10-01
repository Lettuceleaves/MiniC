package minic.ui.component.swing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.JWindow;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 使用未显示的 Swing 窗口验证局部状态；不启动 JavaFX，也不创建可见窗口。 */
@EnabledIfSystemProperty(named = "minic.ui.test", matches = "true")
final class UiSwingNodeSurfaceTest {
    private static GraphicsConfiguration configuration;

    @BeforeAll
    static void requireWindowSupport() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a window-capable environment");
        configuration = Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
                .flatMap(device -> Arrays.stream(device.getConfigurations()))
                .filter(GraphicsConfiguration::isTranslucencyCapable)
                .findFirst().orElse(null);
        assumeTrue(configuration != null, "requires per-pixel translucent window support");
    }

    @Test
    void prepareAndReleaseRequireTheSwingThread() throws Exception {
        var content = new AtomicReference<JPanel>();
        onSwing(() -> content.set(new JPanel()));
        assertFalse(SwingUtilities.isEventDispatchThread());
        assertThrows(IllegalStateException.class, () -> UiSwingNodeSurface.prepare(content.get()));
        assertThrows(IllegalStateException.class, () -> UiSwingNodeSurface.release(content.get()));
    }

    @Test
    void unattachedAndInitiallyTransparentContentRemainUntouched() throws Exception {
        onSwing(() -> {
            JPanel unattached = new JPanel();
            unattached.setBackground(Color.BLACK);
            unattached.setDoubleBuffered(true);
            UiSwingNodeSurface.prepare(unattached);
            UiSwingNodeSurface.release(unattached);
            assertTrue(unattached.isDoubleBuffered());
            assertEquals(Color.BLACK, unattached.getBackground());

            try (Fixture fixture = new Fixture()) {
                fixture.content.setOpaque(false);
                WindowState transparent = WindowState.capture(fixture.window);
                UiSwingNodeSurface.prepare(fixture.content);
                transparent.assertRestored();

                fixture.content.setOpaque(true);
                fixture.content.setBackground(new Color(20, 30, 40, 90));
                WindowState alpha = WindowState.capture(fixture.window);
                UiSwingNodeSurface.prepare(fixture.content);
                alpha.assertRestored();
            }
        });
    }

    @Test
    void repeatedPrepareIsLocalAndReleaseRestoresEveryOriginalSetting() throws Exception {
        onSwing(() -> {
            try (Fixture fixture = new Fixture(); Fixture unrelated = new Fixture()) {
                RepaintManager manager = RepaintManager.currentManager(fixture.content);
                boolean managerBuffering = manager.isDoubleBufferingEnabled();
                UiSwingNodeSurface.prepare(fixture.content);
                assertPrepared(fixture.window, fixture.content.getBackground());

                UiSwingNodeSurface.prepare(fixture.content);
                fixture.content.setBackground(new Color(40, 50, 60));
                UiSwingNodeSurface.prepare(fixture.content);
                assertPrepared(fixture.window, fixture.content.getBackground());
                assertSame(manager, RepaintManager.currentManager(fixture.content));
                assertEquals(managerBuffering, manager.isDoubleBufferingEnabled());
                unrelated.original.assertRestored();

                UiSwingNodeSurface.release(fixture.content);
                fixture.original.assertRestored();
                UiSwingNodeSurface.release(fixture.content);
                fixture.original.assertRestored();
                assertFalse(fixture.window.isVisible());
                assertFalse(unrelated.window.isVisible());
            }
        });
    }

    @Test
    void changingToTransparentContentRestoresAndClearsTheOptimization() throws Exception {
        onSwing(() -> {
            try (Fixture fixture = new Fixture()) {
                UiSwingNodeSurface.prepare(fixture.content);
                Color transparent = new Color(20, 30, 40, 100);
                fixture.content.setBackground(transparent);
                UiSwingNodeSurface.prepare(fixture.content);
                fixture.original.assertRestored();
                assertEquals(transparent, fixture.content.getBackground());

                fixture.content.setBackground(Color.BLACK);
                UiSwingNodeSurface.prepare(fixture.content);
                fixture.content.setOpaque(false);
                UiSwingNodeSurface.prepare(fixture.content);
                assertFalse(fixture.content.isOpaque(), "must retain the caller's transparent-content setting");
                // 恢复测试自己改变的属性，再逐项核验优化器的状态恢复。
                fixture.content.setOpaque(true);
                fixture.original.assertRestored();
            }
        });
    }

    @Test
    void removedLightweightPopupIsRestoredAndNoLongerRetainedForRelease() throws Exception {
        onSwing(() -> {
            try (Fixture fixture = new Fixture()) {
                JPanel popup = new JPanel(new BorderLayout());
                JViewport popupViewport = new JViewport();
                popupViewport.setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
                popup.add(popupViewport);
                fixture.window.getLayeredPane().add(popup, JLayeredPane.POPUP_LAYER);

                UiSwingNodeSurface.prepare(fixture.content);
                assertFalse(popup.isDoubleBuffered());
                assertEquals(JViewport.SIMPLE_SCROLL_MODE, popupViewport.getScrollMode());

                fixture.window.getLayeredPane().remove(popup);
                UiSwingNodeSurface.prepare(fixture.content);
                assertTrue(popup.isDoubleBuffered());
                assertEquals(JViewport.BACKINGSTORE_SCROLL_MODE, popupViewport.getScrollMode());

                popup.setDoubleBuffered(false);
                popupViewport.setScrollMode(JViewport.BLIT_SCROLL_MODE);
                UiSwingNodeSurface.release(fixture.content);
                assertFalse(popup.isDoubleBuffered(), "release must not revisit a removed popup");
                assertEquals(JViewport.BLIT_SCROLL_MODE, popupViewport.getScrollMode());
                fixture.original.assertRestored();
            }
        });
    }

    @Test
    void changingTheOwningWindowRestoresTheOldWindowBeforePreparingTheNewOne() throws Exception {
        onSwing(() -> {
            try (Fixture first = new Fixture(); Fixture second = new Fixture()) {
                UiSwingNodeSurface.prepare(first.content);
                first.host.remove(first.content);
                second.host.remove(second.content);
                second.host.add(first.content, BorderLayout.CENTER);

                UiSwingNodeSurface.prepare(first.content);
                first.original.assertRestoredExceptSubtree(first.content);
                assertPrepared(second.window, first.content.getBackground());

                UiSwingNodeSurface.release(first.content);
                first.original.assertRestored();
                second.original.assertRestored();
            }
        });
    }

    @Test
    void preparingAfterDetachRestoresThePreviousWindow() throws Exception {
        onSwing(() -> {
            try (Fixture fixture = new Fixture()) {
                UiSwingNodeSurface.prepare(fixture.content);
                fixture.host.remove(fixture.content);
                UiSwingNodeSurface.prepare(fixture.content);
                fixture.original.assertRestored();
                assertNull(SwingUtilities.getWindowAncestor(fixture.content));
            }
        });
    }

    private static void onSwing(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static void assertPrepared(Window window, Color background) {
        assertEquals(background, window.getBackground());
        assertTrue(window.isOpaque());
        assertFalse(window.isVisible());
        visit(window, component -> {
            assertFalse(component.isDoubleBuffered(), component.getClass().getSimpleName());
            if (component instanceof JViewport viewport) {
                assertEquals(JViewport.SIMPLE_SCROLL_MODE, viewport.getScrollMode());
            }
        });
    }

    private static void visit(Component component, java.util.function.Consumer<JComponent> action) {
        if (component instanceof JComponent swing) {
            action.accept(swing);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                visit(child, action);
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final JWindow window = new JWindow((Window) null, configuration);
        private final JPanel host = new JPanel(new BorderLayout());
        private final JPanel content = new JPanel(new BorderLayout());
        private final WindowState original;

        private Fixture() {
            window.setContentPane(host);
            window.setBackground(new Color(3, 5, 7, 0));
            // 各层原始 opacity 故意不同，防止仅恢复 Window 背景造成错误。
            window.getRootPane().setOpaque(false);
            window.getLayeredPane().setOpaque(true);
            host.setOpaque(true);
            host.add(content, BorderLayout.CENTER);
            content.setBackground(new Color(13, 17, 23));
            content.setOpaque(true);

            JViewport viewport = new JViewport();
            viewport.setDoubleBuffered(true);
            viewport.setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
            viewport.setView(new JPanel());
            JViewport rowHeader = new JViewport();
            rowHeader.setDoubleBuffered(false);
            rowHeader.setScrollMode(JViewport.BLIT_SCROLL_MODE);
            rowHeader.setView(new JPanel());
            content.add(viewport, BorderLayout.CENTER);
            content.add(rowHeader, BorderLayout.WEST);
            original = WindowState.capture(window);
        }

        @Override
        public void close() {
            try {
                UiSwingNodeSurface.release(content);
            } finally {
                window.dispose();
            }
        }
    }

    private record ComponentState(boolean opaque, boolean doubleBuffered, int scrollMode) {
        private static ComponentState capture(JComponent component) {
            return new ComponentState(component.isOpaque(), component.isDoubleBuffered(),
                    component instanceof JViewport viewport ? viewport.getScrollMode() : -1);
        }
    }

    private record WindowState(Window window, Color background, Map<JComponent, ComponentState> components) {
        private static WindowState capture(Window window) {
            Map<JComponent, ComponentState> components = new IdentityHashMap<>();
            visit(window, component -> components.put(component, ComponentState.capture(component)));
            return new WindowState(window, window.getBackground(), components);
        }

        private void assertRestored() {
            assertRestoredExceptSubtree(null);
        }

        private void assertRestoredExceptSubtree(Component excluded) {
            assertEquals(background, window.getBackground());
            components.forEach((component, state) -> {
                if (excluded == null || !SwingUtilities.isDescendingFrom(component, excluded)) {
                    assertEquals(state, ComponentState.capture(component), component.getClass().getSimpleName());
                }
            });
        }
    }
}
