package minic.ui.frame;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import minic.ui.component.UiStyles;
import minic.ui.component.data.UiList;
import minic.ui.component.display.UiIcon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "minic.ui.test", matches = "true")
final class AppFrameTest {
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
    void rightActivityBarKeepsItsIconsWithoutWidthButtons() throws Exception {
        onFx(() -> {
            AppFrame frame = frame();
            assertNull(frame.lookup("#display-expand-button"));
            assertNull(frame.lookup("#display-collapse-button"));
            assertNull(frame.lookup("#display-restore-button"));
            assertTrue(frame.lookupAll(".display-width-button").isEmpty());
            assertTrue(frame.lookupAll(".display-width-actions").isEmpty());

            Parent bar = assertInstanceOf(Parent.class, frame.lookup(".display-activity-bar"));
            assertEquals(1, bar.getChildrenUnmodifiable().size(), "no empty action footer");
            UiList activities = assertInstanceOf(UiList.class, bar.getChildrenUnmodifiable().getFirst());
            assertEquals(4, activities.getItems().size());
            assertSame(frame.lookup("#app-pipeline-button"), activities.getItems().get(1));
            assertEquals("自动整理代码", activities.getItems().getLast().getAccessibleText());
            assertEquals(48, activities.getCrossExtent());
        });
    }

    @Test
    void workspaceTabsKeepExactlyOneSelectionAndReplaceTheWholeWorkspace() throws Exception {
        onFx(() -> {
            AppFrame frame = frame();
            StackPane host = workspaceHost(frame);
            Node editorWorkspace = host.getChildren().getFirst();
            assertEquals(4, frame.lookupAll(".activity-workspace-button").size());
            assertSame(editorWorkspace, frame.lookup(".app-content"));
            assertWorkspaceSelection(frame, AppFrame.WorkspaceTab.EDITOR);

            for (int round = 0; round < 3; round++) {
                for (AppFrame.WorkspaceTab tab : AppFrame.WorkspaceTab.values()) {
                    ToggleButton button = workspaceButton(frame, tab);
                    button.fire();
                    assertWorkspaceSelection(frame, tab);
                    assertEquals(1, host.getChildren().size(), "rapid tab changes must never accumulate pages");
                    Node current = host.getChildren().getFirst();
                    if (tab == AppFrame.WorkspaceTab.EDITOR) {
                        assertSame(editorWorkspace, current);
                    } else {
                        assertEquals("app-" + tab.name().toLowerCase(java.util.Locale.ROOT) + "-page", current.getId());
                        assertNull(frame.lookup(".app-content"),
                                "the editor, tabs, interaction area and display must all leave the workspace together");
                        assertNull(editorWorkspace.getParent());
                    }
                    button.fire();
                    assertWorkspaceSelection(frame, tab);
                    assertSame(current, host.getChildren().getFirst(),
                            "clicking the active tab must keep its page and selection");
                }
            }
            frame.showEditorWorkspace();
            assertWorkspaceSelection(frame, AppFrame.WorkspaceTab.EDITOR);
            assertSame(editorWorkspace, host.getChildren().getFirst());
        });
    }

    @Test
    void switchingWorkspaceTabsPreservesEditorContentAndBothDividerPositions() throws Exception {
        onFx(() -> {
            Region editor = new Region();
            Region display = new Region();
            Region interaction = new Region();
            Region tabs = new Region();
            editor.setId("test-editor-content");
            display.setId("test-display-content");
            interaction.setId("test-interaction-content");
            tabs.setId("test-document-tabs");
            editor.setUserData("unsaved editor state");
            AppFrame frame = new AppFrame(editor, display, interaction, tabs);
            Scene scene = new Scene(frame, 1280, 800);
            UiStyles.install(scene);
            frame.resize(1280, 800);
            frame.applyCss();
            frame.layout();
            SplitPane content = assertInstanceOf(SplitPane.class, frame.lookup(".app-content"));
            SplitPane center = assertInstanceOf(SplitPane.class, frame.lookup(".app-center"));
            Node divider = content.lookupAll(".split-pane-divider").stream()
                    .filter(node -> node.getParent() == content).findFirst().orElseThrow();
            // A real press hands control to the user rather than retaining the default-width preset.
            divider.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0,
                    MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, false, false, true, new PickResult(divider, 0, 0)));
            content.setDividerPositions(0.61);
            center.setDividerPositions(0.64);
            divider.fireEvent(new MouseEvent(MouseEvent.MOUSE_RELEASED, 0, 0, 0, 0,
                    MouseButton.PRIMARY, 1, false, false, false, false,
                    false, false, false, false, false, true, new PickResult(divider, 0, 0)));
            frame.layout();
            double displayDivider = content.getDividerPositions()[0];
            double interactionDivider = center.getDividerPositions()[0];

            frame.selectWorkspaceTab(AppFrame.WorkspaceTab.SETTINGS);
            frame.applyCss();
            frame.layout();
            assertWorkspaceSelection(frame, AppFrame.WorkspaceTab.SETTINGS);
            assertEquals(1, workspaceHost(frame).getChildren().size());
            assertEquals("app-settings-page", workspaceHost(frame).getChildren().getFirst().getId());
            assertNull(content.getParent());
            assertNull(content.getScene(), "the cached editor page must be detached while a different tab is shown");
            assertNull(frame.lookup("#test-document-tabs"));
            assertNull(frame.lookup("#test-editor-content"));
            assertNull(frame.lookup("#test-interaction-content"));
            assertNull(frame.lookup("#test-display-content"));

            frame.selectWorkspaceTab(AppFrame.WorkspaceTab.PROFILE);
            frame.showEditorWorkspace();
            frame.applyCss();
            frame.layout();
            assertWorkspaceSelection(frame, AppFrame.WorkspaceTab.EDITOR);
            assertSame(content, workspaceHost(frame).getChildren().getFirst());
            assertSame(scene, content.getScene());
            assertSame(center, frame.lookup(".app-center"));
            assertSame(editor, frame.lookup("#test-editor-content"));
            assertSame(display, frame.lookup("#test-display-content"));
            assertSame(interaction, frame.lookup("#test-interaction-content"));
            assertSame(tabs, frame.lookup("#test-document-tabs"));
            assertEquals("unsaved editor state", editor.getUserData());
            assertEquals(displayDivider, content.getDividerPositions()[0], 0.002);
            assertEquals(interactionDivider, center.getDividerPositions()[0], 0.002);
        });
    }

    @Test
    void pipelineSelectionFollowsTheVisibleWorkspaceWithoutLosingItsOpenState() throws Exception {
        onFx(() -> {
            AppFrame frame = frame();
            ToggleButton pipeline = assertInstanceOf(ToggleButton.class, frame.lookup("#app-pipeline-button"));
            AtomicInteger opens = new AtomicInteger();
            frame.setOnPipeline(() -> {
                opens.incrementAndGet();
                frame.showPipelineDisplayArea();
            });
            pipeline.fire();
            assertTrue(pipeline.isSelected());
            for (AppFrame.WorkspaceTab tab : new AppFrame.WorkspaceTab[]{
                    AppFrame.WorkspaceTab.EXTENSIONS, AppFrame.WorkspaceTab.SETTINGS, AppFrame.WorkspaceTab.PROFILE}) {
                frame.selectWorkspaceTab(tab);
                assertFalse(pipeline.isSelected(), "a pipeline in the detached editor workspace is not visible");
                frame.showEditorWorkspace();
                assertTrue(pipeline.isSelected(), "returning to the editor must restore its open pipeline selection");
            }
            assertEquals(1, opens.get(), "switching workspaces must not restart the pipeline session");
            pipeline.fire();
            assertFalse(pipeline.isSelected());
            frame.selectWorkspaceTab(AppFrame.WorkspaceTab.SETTINGS);
            frame.showEditorWorkspace();
            assertFalse(pipeline.isSelected(), "a collapsed pipeline must remain collapsed after changing tabs");
            assertEquals(1, opens.get());
        });
    }

    @Test
    void runAndPipelineCommandsReturnToTheEditorBeforeForwarding() throws Exception {
        onFx(() -> {
            AppFrame frame = frame();
            Node editorWorkspace = workspaceHost(frame).getChildren().getFirst();
            Button run = assertInstanceOf(Button.class, frame.lookup("#app-run-button"));
            ToggleButton pipeline = assertInstanceOf(ToggleButton.class, frame.lookup("#app-pipeline-button"));
            AtomicInteger runs = new AtomicInteger();
            AtomicInteger opens = new AtomicInteger();
            frame.setOnRun(() -> {
                assertWorkspaceSelection(frame, AppFrame.WorkspaceTab.EDITOR);
                assertSame(editorWorkspace, workspaceHost(frame).getChildren().getFirst());
                runs.incrementAndGet();
            });
            frame.setOnPipeline(() -> {
                assertWorkspaceSelection(frame, AppFrame.WorkspaceTab.EDITOR);
                assertSame(editorWorkspace, workspaceHost(frame).getChildren().getFirst());
                opens.incrementAndGet();
                frame.showPipelineDisplayArea();
            });
            for (AppFrame.WorkspaceTab tab : new AppFrame.WorkspaceTab[]{
                    AppFrame.WorkspaceTab.EXTENSIONS, AppFrame.WorkspaceTab.SETTINGS, AppFrame.WorkspaceTab.PROFILE}) {
                frame.selectWorkspaceTab(tab);
                run.fire();
                assertWorkspaceSelection(frame, AppFrame.WorkspaceTab.EDITOR);
                frame.selectWorkspaceTab(tab);
                pipeline.fire();
                assertWorkspaceSelection(frame, AppFrame.WorkspaceTab.EDITOR);
                assertTrue(pipeline.isSelected());
            }
            assertEquals(3, runs.get());
            assertEquals(3, opens.get());

            frame.setRunDisabled(true);
            frame.setPipelineDisabled(true);
            frame.selectWorkspaceTab(AppFrame.WorkspaceTab.SETTINGS);
            run.fire();
            pipeline.fire();
            assertWorkspaceSelection(frame, AppFrame.WorkspaceTab.SETTINGS);
            assertEquals(3, runs.get());
            assertEquals(3, opens.get());
        });
    }

    @Test
    void displayWidthCommandsRemainPublicAndCallable() throws Exception {
        for (String name : new String[]{"expandDisplayArea", "collapseDisplayArea", "restoreDefaultDisplayAreaWidth"}) {
            var method = AppFrame.class.getMethod(name);
            assertTrue(Modifier.isPublic(method.getModifiers()));
            assertEquals(void.class, method.getReturnType());
        }
        onFx(() -> {
            AppFrame frame = frame();
            frame.expandDisplayArea();
            frame.collapseDisplayArea();
            frame.restoreDefaultDisplayAreaWidth();
            assertEquals(280, AppFrame.DEFAULT_DISPLAY_WIDTH);
            assertEquals(240, DisplayPaneWidthController.ANIMATION_DURATION.toMillis());
        });
    }

    @Test
    void runIconForwardsOnlyTheCurrentEnabledCommand() throws Exception {
        onFx(() -> {
            AppFrame frame = frame();
            Button run = assertInstanceOf(Button.class, frame.lookup("#app-run-button"));
            assertTrue(run.isDisabled(), "no handler means the command is unavailable");
            assertTrue(run.isFocusTraversable());
            assertInstanceOf(UiIcon.class, run.getGraphic());
            assertEquals("编译并运行当前标签", run.getAccessibleText());
            var first = new AtomicInteger();
            var second = new AtomicInteger();
            frame.setOnRun(first::incrementAndGet);
            assertFalse(run.isDisabled());
            run.fire();
            assertEquals(1, first.get());
            frame.setRunDisabled(true);
            run.fire();
            assertEquals(1, first.get());
            frame.setOnRun(second::incrementAndGet);
            assertTrue(run.isDisabled());
            frame.setRunDisabled(false);
            run.fire();
            assertEquals(1, first.get());
            assertEquals(1, second.get());
            frame.setOnRun(null);
            run.fire();
            assertTrue(run.isDisabled());
            assertEquals(1, second.get());
        });
    }

    @Test
    void pipelineIconForwardsOnlyTheCurrentEnabledCommand() throws Exception {
        onFx(() -> {
            AppFrame frame = frame();
            ToggleButton pipeline = assertInstanceOf(ToggleButton.class, frame.lookup("#app-pipeline-button"));
            assertTrue(pipeline.isDisabled(), "no handler means the command is unavailable");
            assertTrue(pipeline.isFocusTraversable());
            assertInstanceOf(UiIcon.class, pipeline.getGraphic());
            assertEquals("打开编译流水线", pipeline.getAccessibleText());
            assertNotNull(pipeline.getTooltip());
            var first = new AtomicInteger();
            var second = new AtomicInteger();
            frame.setOnPipeline(first::incrementAndGet);
            assertFalse(pipeline.isDisabled());
            pipeline.fire();
            assertEquals(1, first.get());
            assertTrue(pipeline.isSelected());
            pipeline.fire();
            assertFalse(pipeline.isSelected());
            assertEquals(1, first.get(), "closing must not invoke the compilation/open handler");
            frame.setPipelineDisabled(true);
            pipeline.fire();
            assertEquals(1, first.get());
            frame.setOnPipeline(second::incrementAndGet);
            assertTrue(pipeline.isDisabled());
            frame.setPipelineDisabled(false);
            pipeline.fire();
            assertEquals(1, first.get());
            assertEquals(1, second.get());
            assertTrue(pipeline.isSelected());
            frame.setOnPipeline(null);
            pipeline.fire();
            assertTrue(pipeline.isDisabled());
            assertFalse(pipeline.isSelected());
            assertEquals(1, second.get());
        });
    }

    @Test
    void displayCommandsKeepThePipelineSelectionInSync() throws Exception {
        onFx(() -> {
            AppFrame frame = frame();
            ToggleButton pipeline = (ToggleButton) frame.lookup("#app-pipeline-button");
            var opens = new AtomicInteger();
            frame.setOnPipeline(opens::incrementAndGet);
            frame.showPipelineDisplayArea();
            assertTrue(pipeline.isSelected());
            assertEquals(0, opens.get(), "explicit opening should not call the handler recursively");
            pipeline.fire();
            assertFalse(pipeline.isSelected());
            assertEquals(0, opens.get());
            frame.expandDisplayArea();
            assertTrue(pipeline.isSelected());
            frame.collapseDisplayArea();
            assertFalse(pipeline.isSelected());
            frame.restoreDefaultDisplayAreaWidth();
            assertTrue(pipeline.isSelected());
            frame.setOnRun(frame::collapseDisplayArea);
            ((Button) frame.lookup("#app-run-button")).fire();
            assertFalse(pipeline.isSelected(), "running code must clear the hidden display selection");
        });
    }

    private static AppFrame frame() {
        return new AppFrame(new Region(), new Region(), new Region(), new Region());
    }

    private static StackPane workspaceHost(AppFrame frame) {
        return assertInstanceOf(StackPane.class, frame.lookup("#app-workspace-host"));
    }

    private static ToggleButton workspaceButton(AppFrame frame, AppFrame.WorkspaceTab tab) {
        return assertInstanceOf(ToggleButton.class,
                frame.lookup("#app-" + tab.name().toLowerCase(java.util.Locale.ROOT) + "-button"));
    }

    private static void assertWorkspaceSelection(AppFrame frame, AppFrame.WorkspaceTab expected) {
        assertEquals(expected, frame.selectedWorkspaceTab());
        for (AppFrame.WorkspaceTab tab : AppFrame.WorkspaceTab.values()) {
            assertEquals(tab == expected, workspaceButton(frame, tab).isSelected(),
                    "exactly the visible workspace tab must be selected: " + tab);
        }
    }

    private static void onFx(Runnable action) throws Exception {
        var result = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                action.run();
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        result.get(10, TimeUnit.SECONDS);
    }
}
