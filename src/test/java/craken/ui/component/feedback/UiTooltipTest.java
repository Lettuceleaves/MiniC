package craken.ui.component.feedback;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.robot.Robot;
import javafx.scene.layout.HBox;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import craken.ui.component.UiStyles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** 显式启用真实 JavaFX 浮窗测试，只创建和关闭测试自己的窗口。 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class UiTooltipTest {
    @BeforeAll
    static void toolkit() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        Runnable ready = () -> { Platform.setImplicitExit(false); started.complete(null); };
        try { Platform.startup(ready); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready); }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void realHoverDelaysThenShowsAndLeavingHides() throws Exception {
        Fixture ui = onFx(Fixture::new);
        try {
            onFx(() -> {
                ui.tooltip.setShowDelay(Duration.millis(120));
                ui.tooltip.setHideDelay(Duration.millis(20));
                assertFalse(ui.tooltip.isAutoHide(), "tooltips must not grab native mouse input");
                ui.button.setTooltip(ui.tooltip);
                ui.mouse(MouseEvent.MOUSE_MOVED);
                assertFalse(ui.tooltip.isShowing(), "hover must respect the appearance delay");
                return null;
            });
            awaitShowing(ui.tooltip, true);
            onFx(() -> { ui.mouse(MouseEvent.MOUSE_EXITED); return null; });
            awaitShowing(ui.tooltip, false);
        } finally { onFx(() -> { ui.close(); return null; }); }
    }

    @Test
    void nativeHoverAlsoTracksOwnerVisibility() throws Exception {
        Fixture ui = onFx(Fixture::new);
        try {
            onFx(() -> {
                ui.tooltip.setShowDelay(Duration.millis(30));
                ui.button.setTooltip(ui.tooltip);
                ui.mouse(MouseEvent.MOUSE_MOVED);
                return null;
            });
            awaitShowing(ui.tooltip, true);
            onFx(() -> {
                ui.button.setVisible(false);
                assertFalse(ui.tooltip.isShowing());
                return null;
            });
        } finally { onFx(() -> { ui.close(); return null; }); }
    }

    @Test
    void openingMenuBeforeNativeHoverDelaySuppressesTooltip() throws Exception {
        Fixture ui = onFx(Fixture::new);
        MenuButton menu = onFx(() -> {
            MenuButton button = new MenuButton("Menu", null, new MenuItem("Action"));
            button.setTooltip(ui.tooltip);
            ui.tooltip.setShowDelay(Duration.millis(100));
            ui.root.getChildren().setAll(button);
            ui.root.applyCss();
            ui.root.layout();
            Point2D point = button.localToScreen(4, 4);
            Event.fireEvent(button, new MouseEvent(MouseEvent.MOUSE_MOVED, 4, 4,
                    point.getX(), point.getY(), MouseButton.NONE, 0,
                    false, false, false, false, false, false, false, false, false, true, null));
            button.show();
            return button;
        });
        try {
            Thread.sleep(350);
            onFx(() -> {
                assertTrue(menu.isShowing());
                assertFalse(ui.tooltip.isShowing());
                return null;
            });
        } finally {
            onFx(() -> { menu.hide(); menu.setTooltip(null); ui.close(); return null; });
        }
    }

    @Test
    void escapeAndOwnerClickDismissWithoutSwallowingClick() throws Exception {
        onFx(() -> {
            try (Fixture ui = new Fixture()) {
                ui.showTooltip();
                assertTrue(ui.tooltip.isShowing());
                Event.fireEvent(ui.button, new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                        KeyCode.ESCAPE, false, false, false, false));
                assertFalse(ui.tooltip.isShowing());
                ui.showTooltip();
                AtomicBoolean receivedClick = new AtomicBoolean();
                ui.button.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> receivedClick.set(true));
                ui.mouse(MouseEvent.MOUSE_PRESSED);
                assertFalse(ui.tooltip.isShowing());
                assertTrue(receivedClick.get(), "dismissal must not consume the user's click");
            }
            return null;
        });
    }

    @Test
    void openingMenuDismissesTooltipAndSuppressesANewOne() throws Exception {
        onFx(() -> {
            try (Fixture ui = new Fixture()) {
                MenuButton menu = new MenuButton("Menu", null, new MenuItem("Action"));
                ui.root.getChildren().setAll(menu);
                ui.root.applyCss();
                ui.root.layout();
                Point2D anchor = menu.localToScreen(0, menu.getHeight());
                ui.tooltip.show(menu, anchor.getX(), anchor.getY() + 4);
                assertTrue(ui.tooltip.isShowing());
                menu.show();
                assertTrue(menu.isShowing());
                assertFalse(ui.tooltip.isShowing());
                ui.tooltip.show(menu, anchor.getX(), anchor.getY() + 4);
                assertFalse(ui.tooltip.isShowing(), "an open menu and its tooltip must not overlap");
                menu.hide();
            }
            return null;
        });
    }

    @Test
    void richContentUpdatesAndDetachingOwnerClosesPopup() throws Exception {
        onFx(() -> {
            try (Fixture ui = new Fixture()) {
                ui.tooltip.setTitle("保存文件");
                ui.tooltip.setText("保存当前文件到磁盘。");
                ui.tooltip.setShortcut("Ctrl+S");
                ui.showTooltip();
                assertEquals("保存文件", label(ui.tooltip, ".ui-tooltip-title").getText());
                assertEquals("保存当前文件到磁盘。", label(ui.tooltip, ".ui-tooltip-description").getText());
                assertEquals("Ctrl+S", label(ui.tooltip, ".ui-tooltip-shortcut").getText());
                ui.tooltip.setShortcut("");
                assertFalse(label(ui.tooltip, ".ui-tooltip-shortcut").isManaged());
                ui.root.getChildren().clear();
                assertFalse(ui.tooltip.isShowing(), "detaching the owner must not leave a stale popup");
            }
            return null;
        });
    }

    @Test
    void hidingOrDisablingOwnerClosesPopup() throws Exception {
        onFx(() -> {
            try (Fixture ui = new Fixture()) {
                ui.showTooltip();
                ui.button.setVisible(false);
                assertFalse(ui.tooltip.isShowing());
                ui.button.setVisible(true);
                ui.showTooltip();
                ui.button.setDisable(true);
                assertFalse(ui.tooltip.isShowing());
            }
            return null;
        });
    }

    @Test
    void stationaryNativePointerKeepsTooltipVisibleForSeveralSeconds() throws Exception {
        Fixture ui = onFx(Fixture::new);
        Point2D savedPointer = onFx(() -> new Robot().getMousePosition());
        onFx(() -> { new Robot().mouseMove(ui.root.localToScreen(4, 4)); return null; });
        Thread.sleep(120);
        List<String> transitions = new ArrayList<>();
        try {
            onFx(() -> {
                ui.tooltip.setShowDelay(Duration.millis(120));
                ui.button.setTooltip(ui.tooltip);
                ui.tooltip.showingProperty().addListener((observable, oldValue, showing) ->
                        transitions.add(showing + " focus=" + ui.stage.isFocused()
                                + " pointer=" + new Robot().getMousePosition()
                                + " popup=" + ui.tooltip.getX() + "," + ui.tooltip.getY()
                                + "," + ui.tooltip.getWidth() + "," + ui.tooltip.getHeight()));
                moveTo(ui.button);
                return null;
            });
            awaitShowing(ui.tooltip, true);
            Thread.sleep(2200);
            onFx(() -> {
                assertTrue(ui.tooltip.isShowing(), "stationary hover must remain visible: " + transitions);
                assertEquals(1, transitions.size(), "hover must not repeatedly close/reopen: " + transitions);
                return null;
            });
        } finally {
            onFx(() -> { ui.close(); new Robot().mouseMove(savedPointer); return null; });
        }
    }

    @Test
    void nestedCloseTooltipRemainsStableAndTakesPriorityOverTabTooltip() throws Exception {
        Fixture ui = onFx(Fixture::new);
        Point2D savedPointer = onFx(() -> new Robot().getMousePosition());
        onFx(() -> { new Robot().mouseMove(ui.root.localToScreen(4, 4)); return null; });
        Thread.sleep(120);
        Button close = new Button("×");
        HBox tab = new HBox(20, new Label("main.c"), close);
        List<String> transitions = new ArrayList<>();
        UiTooltip tabTooltip = onFx(() -> {
            UiTooltip tooltip = UiTooltip.attach(tab, "main.c", "E:/projects/Craken/main.c", "");
            tooltip.setShowDelay(Duration.millis(120));
            ui.tooltip.setShowDelay(Duration.millis(120));
            ui.tooltip.setText("关闭 main.c");
            close.setTooltip(ui.tooltip);
            ui.root.getChildren().setAll(tab);
            tab.setMaxSize(200, 36);
            ui.root.applyCss(); ui.root.layout();
            tooltip.showingProperty().addListener((observable, oldValue, showing) -> transitions.add("tab=" + showing));
            ui.tooltip.showingProperty().addListener((observable, oldValue, showing) -> transitions.add("close=" + showing));
            moveTo(close);
            return tooltip;
        });
        try {
            Thread.sleep(450);
            onFx(() -> {
                assertTrue(ui.tooltip.isShowing(), "nested close must own the hover: " + transitions);
                assertFalse(tabTooltip.isShowing(), "tab hint must not compete with close hint: " + transitions);
                return null;
            });
            for (int i = 0; i < 15; i++) {
                int offset = i % 2;
                onFx(() -> {
                    Point2D point = close.localToScreen(close.getWidth() / 2 + offset, close.getHeight() / 2);
                    new Robot().mouseMove(point);
                    return null;
                });
                Thread.sleep(100);
            }
            onFx(() -> {
                assertTrue(ui.tooltip.isShowing(), "moving within close must retain its tooltip: " + transitions);
                assertFalse(tabTooltip.isShowing());
                assertEquals(List.of("close=true"), transitions, "nested hints must not alternate or flicker");
                return null;
            });
        } finally {
            onFx(() -> {
                tabTooltip.hide(); Tooltip.uninstall(tab, tabTooltip); close.setTooltip(null);
                ui.close(); new Robot().mouseMove(savedPointer); return null;
            });
        }
    }

    private static void moveTo(Node node) {
        Point2D point = node.localToScreen(node.getLayoutBounds().getWidth() / 2,
                node.getLayoutBounds().getHeight() / 2);
        new Robot().mouseMove(point);
    }
    private static Label label(UiTooltip tooltip, String selector) {
        return (Label) tooltip.getGraphic().lookup(selector);
    }

    private static void awaitShowing(UiTooltip tooltip, boolean showing) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (onFx(tooltip::isShowing) == showing) return;
            Thread.sleep(25);
        }
        assertEquals(showing, onFx(tooltip::isShowing));
    }

    private static <T> T onFx(Callable<T> task) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(task.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private static final class Fixture implements AutoCloseable {
        private final Button button = new Button("Hover target");
        private final StackPane root = new StackPane(button);
        private final UiTooltip tooltip = new UiTooltip("A useful hint");
        private final Stage stage = new Stage();

        Fixture() {
            Scene scene = new Scene(root, 340, 200);
            UiStyles.install(scene);
            stage.setScene(scene);
            stage.setTitle("Craken tooltip interaction test");
            stage.setAlwaysOnTop(true);
            stage.show();
            stage.requestFocus();
            root.applyCss();
            root.layout();
        }

        void showTooltip() {
            Point2D anchor = button.localToScreen(0, button.getHeight());
            tooltip.show(button, anchor.getX(), anchor.getY() + 4);
        }

        void mouse(javafx.event.EventType<MouseEvent> type) {
            Point2D point = button.localToScreen(4, 4);
            Event.fireEvent(button, new MouseEvent(type, 4, 4, point.getX(), point.getY(),
                    type == MouseEvent.MOUSE_PRESSED ? MouseButton.PRIMARY : MouseButton.NONE,
                    1, false, false, false, false, false, false, false,
                    false, false, true, null));
        }

        @Override public void close() {
            tooltip.hide();
            button.setTooltip(null);
            stage.close();
        }
    }
}



