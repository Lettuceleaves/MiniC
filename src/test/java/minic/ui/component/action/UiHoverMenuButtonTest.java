package minic.ui.component.action;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import minic.ui.component.navigation.UiContextMenu;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 以 -Dminic.ui.test=true 显式运行，使用独立窗口验证弹层的实际生命周期。 */
@EnabledIfSystemProperty(named = "minic.ui.test", matches = "true")
final class UiHoverMenuButtonTest {
    @BeforeAll
    static void startToolkit() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        Runnable ready = () -> {
            Platform.setImplicitExit(false);
            started.complete(null);
        };
        try {
            Platform.startup(ready);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(ready);
        }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void hoverBridgeKeepsMenuOpenUntilPointerLeavesPopup() throws Exception {
        Fixture fixture = fixture();
        try {
            onFx(() -> mouse(fixture.first, MouseEvent.MOUSE_ENTERED));
            afterPulse(160);
            onFx(() -> {
                assertTrue(fixture.first.isShowing());
                assertTrue(fixture.first.getPopup().isShowing());
                mouse(fixture.first, MouseEvent.MOUSE_EXITED);
                mouse(fixture.first.getPopup().getScene().getRoot(), MouseEvent.MOUSE_ENTERED);
            });
            afterPulse(160);
            onFx(() -> {
                assertTrue(fixture.first.isShowing(), "moving into the popup must cancel delayed dismissal");
                mouse(fixture.first.getPopup().getScene().getRoot(), MouseEvent.MOUSE_EXITED);
            });
            afterPulse(160);
            onFx(() -> assertFalse(fixture.first.isShowing()));
        } finally {
            onFx(fixture.stage::close);
        }
    }

    @Test
    void passingQuicklyOverButtonDoesNotOpenMenu() throws Exception {
        Fixture fixture = fixture();
        try {
            onFx(() -> {
                mouse(fixture.first, MouseEvent.MOUSE_ENTERED);
                mouse(fixture.first, MouseEvent.MOUSE_EXITED);
            });
            afterPulse(160);
            onFx(() -> assertFalse(fixture.first.isShowing()));
        } finally {
            onFx(fixture.stage::close);
        }
    }

    @Test
    void menuBarSwitchesImmediatelyAndOnlyOnePopupRemainsOpen() throws Exception {
        Fixture fixture = fixture();
        try {
            onFx(() -> {
                fixture.first.setMenuStyle(UiContextMenu.Style.MENUBAR);
                fixture.second.setMenuStyle(UiContextMenu.Style.MENUBAR);
                fixture.first.show();
                assertTrue(fixture.first.getPopup().isShowing());
                mouse(fixture.second, MouseEvent.MOUSE_ENTERED);
                assertFalse(fixture.first.isShowing());
                assertTrue(fixture.second.isShowing());
                assertTrue(fixture.second.getPopup().getStyleClass().contains("ui-menubar-context-menu"));
            });
        } finally {
            onFx(fixture.stage::close);
        }
    }

    @Test
    void hidingDisablingOrDetachingAnchorDismissesPopup() throws Exception {
        Fixture fixture = fixture();
        try {
            onFx(() -> {
                fixture.first.show();
                fixture.first.setVisible(false);
                assertFalse(fixture.first.getPopup().isShowing());
                fixture.first.setVisible(true);
                fixture.first.show();
                fixture.first.setDisable(true);
                assertFalse(fixture.first.getPopup().isShowing());
                fixture.first.setDisable(false);
                fixture.first.show();
                fixture.root.getChildren().remove(fixture.first);
                assertFalse(fixture.first.isShowing());
                assertFalse(fixture.first.getPopup().isShowing());
            });
        } finally {
            onFx(fixture.stage::close);
        }
    }

    private static Fixture fixture() throws Exception {
        CompletableFuture<Fixture> result = new CompletableFuture<>();
        onFx(() -> {
            UiHoverMenuButton first = new UiHoverMenuButton("File", new MenuItem("Open"));
            UiHoverMenuButton second = new UiHoverMenuButton("Edit", new MenuItem("Copy"));
            first.setHoverOpenDelay(Duration.millis(30));
            first.setHoverCloseDelay(Duration.millis(40));
            HBox root = new HBox(first, second);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 260, 150));
            stage.show();
            root.applyCss();
            root.layout();
            result.complete(new Fixture(first, second, root, stage));
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private static void mouse(Node target, EventType<MouseEvent> type) {
        Event.fireEvent(target, new MouseEvent(type, 1, 1, 1, 1, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null));
    }

    private static void afterPulse(double millis) throws Exception {
        CompletableFuture<Void> finished = new CompletableFuture<>();
        onFx(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(millis));
            pause.setOnFinished(event -> finished.complete(null));
            pause.play();
        });
        finished.get(10, TimeUnit.SECONDS);
    }

    private static void onFx(Runnable action) throws Exception {
        CompletableFuture<Void> finished = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                finished.complete(null);
            } catch (Throwable failure) {
                finished.completeExceptionally(failure);
            }
        });
        finished.get(10, TimeUnit.SECONDS);
    }

    private record Fixture(UiHoverMenuButton first, UiHoverMenuButton second, HBox root, Stage stage) {
    }
}
