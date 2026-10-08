package craken.ui.interaction;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.geometry.Point3D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import craken.ui.component.UiStyles;
import craken.ui.component.data.UiCollection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 只使用测试容器，不启动 PowerShell，不操作用户窗口。 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class InteractionAreaTest {
    @TempDir Path directory;

    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> { Platform.setImplicitExit(false); started.complete(null); };
        try { Platform.startup(ready); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready); }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void differentTypedContainersRetainTheirOwnStateWhenSwitching() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(directory)) {
                TextArea first = new TextArea("first session output");
                TextField second = new TextField("pending command");
                InteractionItem<TextArea> firstItem = ui.area.addItem(new InteractionItem<>("Output", first));
                InteractionItem<TextField> secondItem = ui.area.addItem(new InteractionItem<>("Input", second));
                ui.attach();
                assertEquals(directory.toAbsolutePath().normalize(), ui.area.projectRoot());
                assertSame(secondItem, ui.area.activeItem());
                assertSame(second, ui.content());
                second.selectRange(2, 9);

                ui.area.select(firstItem);
                first.appendText("\nmore output");
                first.selectRange(3, 12);
                assertSame(first, ui.content());
                assertNull(second.getParent());
                assertSame(first, firstItem.content(), "generic access preserves the concrete container type");

                ui.area.select(secondItem);
                assertEquals("pending command", secondItem.content().getText());
                assertEquals(2, second.getAnchor());
                assertEquals(9, second.getCaretPosition());
                assertNull(first.getParent());
                ui.area.select(firstItem);
                assertEquals("first session output\nmore output", first.getText());
                assertEquals(3, first.getAnchor());
                assertEquals(12, first.getCaretPosition());
                assertFalse(firstItem.isClosed());
                assertFalse(secondItem.isClosed());
            }
        });
    }

    @Test
    void diagnosticsTabsNumberIndependentlyAndIncrement() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(directory)) {
                var first = ui.area.newDiagnostics();
                var second = ui.area.newDiagnostics();
                assertEquals("ERR 1", first.title());
                assertEquals("ERR 2", second.title());
                assertNotSame(first, second);
                assertNotSame(first.content(), second.content());
            }
        });
    }

    @Test
    void clickingAListCellSwitchesTheVisibleContainerAndSelectionProperty() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(directory)) {
                var first = ui.area.addItem(new InteractionItem<>("First", new StackPane()));
                var second = ui.area.addItem(new InteractionItem<>("Second", new TextField("retain")));
                ui.attach();
                AtomicInteger changes = new AtomicInteger();
                ui.area.activeItemProperty().addListener((observable, old, selected) -> changes.incrementAndGet());

                click(ui.cell(first));
                assertSame(first, ui.area.activeItem());
                assertSame(first.content(), ui.content());
                assertEquals(1, changes.get());
                click(ui.cell(second));
                assertSame(second, ui.area.activeItemProperty().get());
                assertSame(second.content(), ui.content());
                assertEquals("retain", second.content().getText());
                assertEquals(2, changes.get());
            }
        });
    }

    @Test
    void overflowingListShowsAnOperableVerticalScrollbar() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(directory)) {
                for (int index = 0; index < 30; index++) {
                    ui.area.addItem(new InteractionItem<>("Panel " + index, new StackPane()));
                }
                ui.area.select(ui.area.items().getFirst());
                ui.attach();
                ScrollBar scroll = ui.list().lookupAll(".scroll-bar").stream()
                        .map(ScrollBar.class::cast)
                        .filter(bar -> bar.getOrientation() == Orientation.VERTICAL)
                        .findFirst().orElseThrow();
                assertTrue(scroll.isVisible());
                assertTrue(scroll.getHeight() > 0);
                scroll.setValue(scroll.getMin());
                ui.layout();
                double before = scroll.getValue();
                Node flow = ui.list().lookup(".virtual-flow");
                Event.fireEvent(flow, new ScrollEvent(ScrollEvent.SCROLL,
                        10, 10, 10, 10, false, false, false, false, false, false,
                        0, -100, 0, -100, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                        ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0,
                        new PickResult(flow, new Point3D(10, 10, 0), 1)));
                ui.layout();
                assertTrue(scroll.getValue() > before, "mouse wheel must scroll the panel list");

                scroll.setValue(scroll.getMax());
                ui.layout();
                var last = ui.area.items().getLast();
                ListCell<?> lastCell = ui.cell(last);
                assertTrue(lastCell.isVisible());
                click(lastCell);
                assertSame(last, ui.area.activeItem());
                assertSame(last.content(), ui.content());
            }
        });
    }

    @Test
    void closingSelectedItemsChoosesAnAdjacentItemAndClosesEachExactlyOnce() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(directory)) {
                AtomicInteger firstClosed = new AtomicInteger();
                AtomicInteger middleClosed = new AtomicInteger();
                AtomicInteger lastClosed = new AtomicInteger();
                AtomicInteger firstSelected = new AtomicInteger();
                AtomicInteger lastSelected = new AtomicInteger();
                var first = ui.area.addItem(new InteractionItem<>("First", new StackPane(), firstSelected::incrementAndGet, firstClosed::incrementAndGet));
                var middle = ui.area.addItem(new InteractionItem<>("Middle", new StackPane(), () -> {}, middleClosed::incrementAndGet));
                var last = ui.area.addItem(new InteractionItem<>("Last", new StackPane(), lastSelected::incrementAndGet, lastClosed::incrementAndGet));
                ui.attach();
                ui.area.select(middle);
                int lastActivations = lastSelected.get();
                ((Button) ui.area.lookup("#interaction-close-panel")).fire();
                assertSame(last, ui.area.activeItem());
                assertSame(last.content(), ui.content());
                assertEquals(0, firstSelected.get(), "closing a middle item must not briefly activate its previous neighbor");
                assertEquals(lastActivations + 1, lastSelected.get(), "activate only the final selected neighbor once");
                assertTrue(middle.isClosed());
                assertEquals(1, middleClosed.get());
                assertFalse(ui.area.closeItem(middle));
                middle.close();
                assertEquals(1, middleClosed.get());

                assertTrue(ui.area.closeItem(last));
                assertSame(first, ui.area.activeItem());
                assertEquals(1, lastClosed.get());
                assertTrue(ui.area.closeItem(first));
                assertNull(ui.area.activeItem());
                assertTrue(ui.area.items().isEmpty());
                assertEquals(1, firstClosed.get());
                assertNotNull(ui.area.lookup(".placeholder-title"));
                assertTrue(((Button) ui.area.lookup("#interaction-close-panel")).isDisabled());
            }
        });
    }

    @Test
    void closingTheWholeAreaDoesNotActivateAnUnusedContainer() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(directory)) {
                AtomicInteger firstSelected = new AtomicInteger();
                AtomicInteger secondSelected = new AtomicInteger();
                AtomicInteger closed = new AtomicInteger();
                var first = ui.area.addItem(new InteractionItem<>("Unused", new StackPane(), firstSelected::incrementAndGet, closed::incrementAndGet));
                var second = ui.area.addItem(new InteractionItem<>("Visible", new StackPane(), secondSelected::incrementAndGet, closed::incrementAndGet));
                assertEquals(0, firstSelected.get());
                assertEquals(0, secondSelected.get());
                ui.attach();
                assertEquals(0, firstSelected.get());
                assertEquals(1, secondSelected.get());

                ui.area.close();
                ui.area.close();
                assertEquals(0, firstSelected.get(), "closing must not activate an unused terminal");
                assertEquals(1, secondSelected.get());
                assertEquals(2, closed.get());
                assertTrue(first.isClosed());
                assertTrue(second.isClosed());
                assertNull(ui.area.activeItem());
                assertTrue(ui.area.items().isEmpty());
                assertTrue(ui.area.isDisabled());
                assertThrows(IllegalStateException.class, () -> ui.area.addItem(new InteractionItem<>("Late", new StackPane())));
                assertThrows(IllegalStateException.class, ui.area::newTerminal);
            }
        });
    }

    @Test
    void duplicateContainersForeignSelectionsAndClosedItemsAreRejected() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(directory)) {
                var first = ui.area.addItem(new InteractionItem<>("First", new StackPane()));
                ui.area.addItem(new InteractionItem<>("Second", new StackPane()));
                assertSame(first, ui.area.addItem(first));
                assertEquals(2, ui.area.items().size());
                assertThrows(IllegalArgumentException.class,
                        () -> ui.area.addItem(new InteractionItem<>("Duplicate", first.content())));
                StackPane adopted = new StackPane();
                new StackPane(adopted);
                assertThrows(IllegalArgumentException.class,
                        () -> ui.area.addItem(new InteractionItem<>("Owned", adopted)));
                var foreign = new InteractionItem<>("Foreign", new StackPane());
                assertThrows(IllegalArgumentException.class, () -> ui.area.select(foreign));
                assertFalse(ui.area.closeItem(foreign));
                foreign.close();
                assertThrows(IllegalArgumentException.class, () -> ui.area.addItem(foreign));
                assertThrows(UnsupportedOperationException.class, () -> ui.area.items().clear());
                assertEquals(2, ui.area.items().size());
            }
        });
    }

    @Test
    void resultMarkersColorTheListItemAndClearOnChange() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture(directory)) {
                var passed = ui.area.addItem(new InteractionItem<>("通过用例", new StackPane()));
                var failed = ui.area.addItem(new InteractionItem<>("失败用例", new StackPane()));
                ui.attach();
                assertEquals(InteractionItem.Result.NONE, passed.result());
                assertFalse(ui.cell(passed).getStyleClass().contains("interaction-passed"));

                passed.setResult(InteractionItem.Result.PASSED);
                failed.setResult(InteractionItem.Result.FAILED);
                assertTrue(ui.cell(passed).getStyleClass().contains("interaction-passed"));
                assertTrue(ui.cell(failed).getStyleClass().contains("interaction-failed"));

                passed.setResult(InteractionItem.Result.FAILED);
                assertFalse(ui.cell(passed).getStyleClass().contains("interaction-passed"));
                assertTrue(ui.cell(passed).getStyleClass().contains("interaction-failed"));

                passed.setResult(null);
                assertEquals(InteractionItem.Result.NONE, passed.result());
                assertFalse(ui.cell(passed).getStyleClass().contains("interaction-failed"));
            }
        });
    }

    private static void click(Node node) {
        Event.fireEvent(node, mouse(node, MouseEvent.MOUSE_PRESSED, true));
        Event.fireEvent(node, mouse(node, MouseEvent.MOUSE_RELEASED, false));
    }

    private static MouseEvent mouse(Node node, javafx.event.EventType<MouseEvent> type, boolean pressed) {
        var point = node.localToScene(8, 8);
        return new MouseEvent(type, point.getX(), point.getY(), point.getX(), point.getY(), MouseButton.PRIMARY, 1,
                false, false, false, false, pressed, false, false, false, false, true,
                new PickResult(node, new Point3D(8, 8, 0), 1));
    }

    private static void onFx(Runnable action) throws Exception {
        var result = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try { action.run(); result.complete(null); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        result.get(20, TimeUnit.SECONDS);
    }

    private static final class Fixture implements AutoCloseable {
        final InteractionArea area;

        Fixture(Path directory) {
            area = new InteractionArea(directory);
            assertEquals(1, area.items().size());
            area.closeItem(area.activeItem());
        }

        void attach() {
            Scene scene = new Scene(area, 800, 210);
            UiStyles.install(scene);
            layout();
        }

        void layout() { area.applyCss(); area.resize(800, 210); area.layout(); }

        Node content() { return ((StackPane) area.getCenter()).getChildren().getFirst(); }

        UiCollection<?> list() { return (UiCollection<?>) area.lookup("#interaction-list"); }

        ListCell<?> cell(InteractionItem<?> item) {
            return list().lookupAll(".list-cell").stream()
                    .filter(node -> node instanceof ListCell<?> cell && cell.getItem() == item)
                    .map(node -> (ListCell<?>) node).findFirst().orElseThrow();
        }

        @Override public void close() { area.close(); }
    }
}
