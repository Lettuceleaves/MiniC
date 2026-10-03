package minic.ui.component.layout;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 以 -Dminic.ui.test=true 显式运行，不操作用户已打开的窗口。 */
@EnabledIfSystemProperty(named = "minic.ui.test", matches = "true")
final class UiWorkspaceCloseTest {
    @BeforeAll
    static void startToolkit() throws Exception {
        var started = new CompletableFuture<Void>();
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
    void onlyDisplayedSplitPanesHaveCloseButtons() throws Exception {
        onFx(() -> {
            UiWorkspace workspace = new UiWorkspace(new Region());
            UiWorkspacePane first = workspace.primaryPane();
            UiWorkspacePane hidden = workspace.createPane(new Region());
            assertFalse(closeButton(first).isVisible());
            assertFalse(closeButton(hidden).isVisible());
            UiWorkspacePane second = first.splitRight(new Region());
            assertTrue(closeButton(first).isVisible());
            assertTrue(closeButton(second).isVisible());
            assertFalse(closeButton(hidden).isVisible());
            workspace.show(hidden, first);
            assertFalse(closeButton(first).isVisible());
            assertTrue(closeButton(hidden).isVisible());
            closeButton(second).fire();
            assertSame(hidden, workspace.primaryPane());
            assertFalse(closeButton(hidden).isVisible(), "hidden retained panes do not count as splits");
            closeButton(hidden).fire();
            assertFalse(hidden.isClosed(), "a queued button action cannot close the last visible pane");
        });
    }

    @Test
    void closingNestedPanePreservesOtherContentsAndDividerPositions() throws Exception {
        onFx(() -> {
            TextArea text = new TextArea("keep this text");
            text.selectRange(2, 8);
            UiWorkspace workspace = new UiWorkspace(text);
            UiWorkspacePane left = workspace.primaryPane();
            UiWorkspacePane right = left.splitRight(new Region());
            UiWorkspacePane lower = right.splitBottom(new Region());
            UiWorkspacePane last = lower.splitRight(new Region());
            SplitPane root = (SplitPane) workspace.getChildren().getFirst();
            SplitPane vertical = (SplitPane) root.getItems().getLast();
            root.setDividerPositions(0.31);
            vertical.setDividerPositions(0.63);
            closeButton(lower).fire();
            assertTrue(lower.isClosed());
            assertSame(root, workspace.getChildren().getFirst());
            assertSame(last, vertical.getItems().getLast());
            assertEquals(0.31, root.getDividerPositions()[0], 0.0001);
            assertEquals(0.63, vertical.getDividerPositions()[0], 0.0001);
            assertSame(text, left.content());
            assertEquals("keep this text", text.getText());
            assertEquals(2, text.getAnchor());
            assertEquals(8, text.getCaretPosition());
            closeButton(right).fire();
            assertSame(last, root.getItems().getLast());
            closeButton(last).fire();
            assertSame(left, workspace.getChildren().getFirst());
            assertFalse(closeButton(left).isVisible());
        });
    }

    @Test
    void closingRootLeafPromotesTheWholeSiblingSubtreeAfterCssLayout() throws Exception {
        onFx(() -> {
            UiWorkspace workspace = new UiWorkspace(new Region());
            UiWorkspacePane left = workspace.primaryPane();
            UiWorkspacePane top = left.splitRight(new Region());
            UiWorkspacePane bottom = top.splitBottom(new Region());
            new Scene(workspace, 800, 600);
            workspace.applyCss();
            workspace.resize(800, 600);
            workspace.layout();
            SplitPane root = (SplitPane) workspace.getChildren().getFirst();
            SplitPane sibling = (SplitPane) root.getItems().getLast();
            closeButton(left).fire();
            workspace.applyCss();
            workspace.layout();
            assertSame(sibling, workspace.getChildren().getFirst());
            assertSame(top, sibling.getItems().getFirst());
            assertSame(bottom, sibling.getItems().getLast());
            assertTrue(closeButton(top).isVisible());
            assertTrue(closeButton(bottom).isVisible());
        });
    }

    @Test
    void closeButtonHonorsExistingVetoAndNotifiesExactlyOnce() throws Exception {
        onFx(() -> {
            UiWorkspace workspace = new UiWorkspace(new Region());
            UiWorkspacePane first = workspace.primaryPane();
            UiWorkspacePane second = first.splitRight(new Region());
            AtomicInteger closed = new AtomicInteger();
            second.setOnCloseRequest(() -> false);
            second.setOnClosed(closed::incrementAndGet);
            closeButton(second).fire();
            assertTrue(workspace.isDisplayed(second));
            assertEquals(0, closed.get());
            second.setOnCloseRequest(() -> true);
            closeButton(second).fire();
            assertTrue(second.isClosed());
            assertFalse(second.close());
            assertEquals(1, closed.get());
            assertFalse(closeButton(first).isVisible());
        });
    }

    @Test
    void replacingContentKeepsCloseControlAndDoesNotCoverContent() throws Exception {
        onFx(() -> {
            UiWorkspace workspace = new UiWorkspace(new Region());
            UiWorkspacePane first = workspace.primaryPane();
            UiWorkspacePane second = first.splitRight(null);
            Button close = closeButton(second);
            Region content = new Region();
            second.setContent(content);
            assertSame(content, second.content());
            assertSame(close, closeButton(second));
            new Scene(workspace, 800, 600);
            workspace.applyCss();
            workspace.resize(800, 600);
            workspace.layout();
            var buttonBounds = close.localToScene(close.getBoundsInLocal());
            var contentBounds = content.localToScene(content.getBoundsInLocal());
            var paneBounds = second.localToScene(second.getBoundsInLocal());
            assertTrue(buttonBounds.getMaxY() <= contentBounds.getMinY());
            assertTrue(Math.abs(paneBounds.getMaxX() - buttonBounds.getMaxX() - 2) < 1);
            second.setContent(null);
            assertNull(content.getParent());
            assertNull(second.content());
            assertSame(close, closeButton(second));
            close.fire();
            assertSame(first, workspace.primaryPane());
        });
    }

    @Test
    void explicitCloseStillSupportsEmptyWorkspaceForDocumentLifecycle() throws Exception {
        onFx(() -> {
            UiWorkspace workspace = new UiWorkspace(new Region());
            UiWorkspacePane last = workspace.primaryPane();
            assertFalse(closeButton(last).isVisible());
            assertTrue(last.close(), "only the split-close button is suppressed, existing close API is retained");
            assertNull(workspace.primaryPane());
        });
    }

    private static Button closeButton(UiWorkspacePane pane) {
        Button button = (Button) pane.lookup(".ui-workspace-pane-close");
        assertNotNull(button, "each pane owns its close control");
        return button;
    }

    private static void onFx(Runnable action) throws Exception {
        var finished = new CompletableFuture<Void>();
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
}
