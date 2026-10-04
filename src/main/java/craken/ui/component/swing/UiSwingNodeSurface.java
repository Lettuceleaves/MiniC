package craken.ui.component.swing;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.IdentityHashMap;
import java.util.Map;

/** 仅调整指定内容的 SwingNode 承载树，不影响 FX 窗口或全局 RepaintManager。 */
public final class UiSwingNodeSurface {
    private static final Object STATE_KEY = new Object();

    private UiSwingNodeSurface() {
    }

    /** 在 Swing EDT 调用；内容尚未挂载时不做修改。 */
    public static void prepare(JComponent content) {
        requireSwingThread();
        Window window = SwingUtilities.getWindowAncestor(content);
        State state = (State) content.getClientProperty(STATE_KEY);
        if (state != null && state.window != window) {
            release(content);
            state = null;
        }
        Color background = content.getBackground();
        if (!content.isOpaque() || background == null || background.getAlpha() != 255) {
            release(content);
            return;
        }
        if (window == null) {
            return;
        }
        if (state == null) {
            state = new State(window);
            content.putClientProperty(STATE_KEY, state);
        }
        state.restoreDetachedComponents();
        // 内容已不透明，隐藏的 AWT 承载窗无需另做透明窗口的 GPU 提交。
        if (!background.equals(window.getBackground())) {
            window.setBackground(background);
        }
        state.prepareTree(window);
    }

    /** 脱离 SwingNode 或切换到透明内容时，恢复这次局部优化之前的状态。 */
    public static void release(JComponent content) {
        requireSwingThread();
        State state = (State) content.getClientProperty(STATE_KEY);
        if (state != null) {
            content.putClientProperty(STATE_KEY, null);
            state.restore();
        }
    }

    private static void requireSwingThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("SwingNode surface must be prepared on the Swing EDT");
        }
    }

    private static final class State {
        private final Window window;
        private final Color originalBackground;
        private final Map<JComponent, ComponentState> components = new IdentityHashMap<>();
        private final Map<JComponent, Boolean> layerOpacity = new IdentityHashMap<>();

        private State(Window window) {
            this.window = window;
            originalBackground = window.getBackground();
            if (originalBackground != null && originalBackground.getAlpha() < 255) {
                rememberLayers(window);
            }
        }

        private void prepareTree(Component component) {
            if (component instanceof JComponent swing) {
                boolean buffered = swing.isDoubleBuffered();
                int scrollMode = swing instanceof JViewport viewport ? viewport.getScrollMode() : -1;
                boolean changeScrollMode = scrollMode != -1 && scrollMode != JViewport.SIMPLE_SCROLL_MODE;
                if (buffered || changeScrollMode) {
                    components.putIfAbsent(swing, new ComponentState(buffered, scrollMode));
                }
                // JLightweightFrame 自有 ARGB 缓冲和像素通知仍在，不再叠加 Swing 缓冲。
                if (buffered) {
                    swing.setDoubleBuffered(false);
                }
                // BLIT 会强制双缓冲；SIMPLE 重画相同内容，滚动模型和输入保持不变。
                if (changeScrollMode) {
                    ((JViewport) swing).setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
                }
            }
            if (component instanceof Container container) {
                for (Component child : container.getComponents()) {
                    prepareTree(child);
                }
            }
        }

        private void restoreDetachedComponents() {
            // 不把曾短暂加入承载树的轻量 popup 保留到内容销毁。
            components.entrySet().removeIf(entry -> {
                if (SwingUtilities.isDescendingFrom(entry.getKey(), window)) {
                    return false;
                }
                entry.getValue().restore(entry.getKey());
                return true;
            });
        }

        private void rememberLayers(Component component) {
            if (component instanceof RootPaneContainer rootContainer) {
                var root = rootContainer.getRootPane();
                layerOpacity.put(root, root.isOpaque());
                layerOpacity.put(root.getLayeredPane(), root.getLayeredPane().isOpaque());
                if (root.getContentPane() instanceof JComponent pane) {
                    layerOpacity.put(pane, pane.isOpaque());
                    if (pane.getComponentCount() > 0) {
                        rememberLayers(pane.getComponent(0));
                    }
                }
            }
        }

        private void restore() {
            if (!java.util.Objects.equals(originalBackground, window.getBackground())) {
                window.setBackground(originalBackground);
            }
            // Window.setBackground 连带修改 Swing 根层 opacity，须恢复各层原值。
            layerOpacity.forEach((component, opaque) -> {
                if (component.isOpaque() != opaque) {
                    component.setOpaque(opaque);
                }
            });
            components.forEach((component, original) -> original.restore(component));
            components.clear();
            layerOpacity.clear();
        }
    }

    private record ComponentState(boolean doubleBuffered, int scrollMode) {
        private void restore(JComponent component) {
            if (component.isDoubleBuffered() != doubleBuffered) {
                component.setDoubleBuffered(doubleBuffered);
            }
            if (component instanceof JViewport viewport && viewport.getScrollMode() != scrollMode) {
                viewport.setScrollMode(scrollMode);
            }
        }
    }
}
