package craken.ui.pipeline;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import craken.ui.component.UiStyles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class PipelinePanelTest {
    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> {
            Platform.setImplicitExit(false);
            started.complete(null);
        };
        try { Platform.startup(ready); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready); }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void expansionStartsWithEqualPanesAndCommandsPreserveTheAdjustedDivider() throws Exception {
        onFx(() -> {
            PipelinePanel panel = new PipelinePanel();
            Scene scene = new Scene(panel, 280, 640);
            UiStyles.install(scene);
            layout(panel, 280, 640);
            layout(panel, 1180, 640);

            SplitPane split = assertInstanceOf(SplitPane.class, panel.getCenter());
            Region input = assertInstanceOf(Region.class, panel.lookup("#pipeline-input"));
            Region output = assertInstanceOf(Region.class, panel.lookup("#pipeline-output"));
            Region sidebar = assertInstanceOf(Region.class, panel.getRight());
            assertEquals(Orientation.HORIZONTAL, split.getOrientation());
            assertEquals(356, sidebar.getWidth(), 1);
            assertEquals((panel.getWidth() - sidebar.getWidth()) / 2, input.getWidth(), 4);
            assertEquals(input.getWidth(), output.getWidth(), 2,
                    "the initial narrow sidebar layout must not displace the 50/50 split after expansion");
            assertTrue(input.localToScene(input.getBoundsInLocal()).getMaxX()
                    <= output.localToScene(output.getBoundsInLocal()).getMinX());
            assertTrue(output.localToScene(output.getBoundsInLocal()).getMaxX()
                    <= sidebar.localToScene(sidebar.getBoundsInLocal()).getMinX() + 1);

            AtomicInteger steps = new AtomicInteger();
            AtomicInteger stages = new AtomicInteger();
            panel.setOnNextStep(steps::incrementAndGet);
            panel.setOnNextStage(stages::incrementAndGet);
            panel.show(snapshot(1, 7));
            split.setDividerPositions(0.32);
            panel.layout();
            double adjusted = split.getDividerPositions()[0];
            assertEquals(0.32, adjusted, 0.005);
            button(panel, "#pipeline-next-step").fire();
            button(panel, "#pipeline-next-stage").fire();
            panel.setBusy(true);
            button(panel, "#pipeline-next-step").fire();
            button(panel, "#pipeline-next-stage").fire();
            assertEquals(1, steps.get());
            assertEquals(1, stages.get());
            panel.show(snapshot(2, 12));
            panel.layout();
            assertEquals(adjusted, split.getDividerPositions()[0], 0.005);
            assertTrue(input.getWidth() < output.getWidth());
            return null;
        });
    }

    @Test
    void stageRowsFillTheAvailableHeightAndRemainNavigableAfterShrinkingAndGrowing() throws Exception {
        onFx(() -> {
            PipelinePanel panel = new PipelinePanel();
            Scene scene = new Scene(panel, 1180, 800);
            UiStyles.install(scene);
            panel.show(snapshot(6, 42));
            ListView<PipelineSession.StageView> stages = stages(panel);
            // Typical app content heights include fractional pixels at 150% display scaling.
            for (double height : new double[]{800, 742, 742.6666667}) {
                layout(panel, 1180, height);
                assertRowsFillList(stages);
            }
            double normalRowHeight = renderedCells(stages).getFirst().getHeight();

            layout(panel, 1180, 360);
            ScrollBar scrollbar = verticalScrollBar(stages);
            assertTrue(scrollbar.isVisible(), "short windows must scroll instead of compressing stage content");
            assertTrue(scrollbar.getVisibleAmount() < scrollbar.getMax() - scrollbar.getMin());
            stages.scrollTo(0);
            layout(panel, 1180, 360);
            ListCell<?> completed = renderedCells(stages).stream()
                    .filter(cell -> cell.getIndex() == 0).findFirst().orElseThrow();
            assertTrue(completed.getHeight() >= 55,
                    "stage title, description and status must retain a readable minimum height");
            assertFalse(completed.isDisabled());
            assertEquals(PipelineSession.Status.COMPLETED,
                    ((PipelineSession.StageView) completed.getItem()).status());
            stages.getSelectionModel().select(completed.getIndex());
            assertEquals(0, stages.getSelectionModel().getSelectedIndex());
            assertEquals("查看已完成阶段 · 预处理", status(panel).getText());

            layout(panel, 1180, 960);
            stages.scrollTo(0);
            layout(panel, 1180, 960);
            assertRowsFillList(stages);
            assertTrue(renderedCells(stages).getFirst().getHeight() > normalRowHeight,
                    "stage rows must grow again after the window is enlarged");
            assertEquals(0, stages.getSelectionModel().getSelectedIndex());
            assertEquals("查看已完成阶段 · 预处理", status(panel).getText());
            return null;
        });
    }

    @Test
    void onlyCompletedAndCurrentStagesAreSelectableAndFailureDisablesAdvance() throws Exception {
        onFx(() -> {
            PipelinePanel panel = new PipelinePanel();
            ListView<PipelineSession.StageView> stages = stages(panel);
            assertEquals(PipelineSession.stageLabels(), stages.getItems().stream()
                    .map(PipelineSession.StageView::label).toList());
            assertTrue(button(panel, "#pipeline-next-step").isDisabled());
            assertTrue(button(panel, "#pipeline-next-stage").isDisabled());
            assertEquals(-1, stages.getSelectionModel().getSelectedIndex());
            stages.getSelectionModel().select(3);
            assertEquals(-1, stages.getSelectionModel().getSelectedIndex());
            assertTrue(stages.getStyleClass().contains("interaction-list"));

            panel.preparing("source.mc");
            panel.show(snapshot(2, 12));
            assertEquals(2, stages.getSelectionModel().getSelectedIndex());
            stages.getSelectionModel().select(0);
            assertEquals(0, stages.getSelectionModel().getSelectedIndex());
            assertEquals("查看已完成阶段 · 预处理", status(panel).getText());
            stages.getSelectionModel().select(7);
            assertEquals(0, stages.getSelectionModel().getSelectedIndex(),
                    "a future stage must not replace the historical selection");
            stages.getSelectionModel().select(2);
            assertEquals("已执行 12 步", status(panel).getText());
            assertFalse(button(panel, "#pipeline-next-step").isDisabled());

            List<PipelineSession.StageView> failed = List.of(
                    new PipelineSession.StageView(0, "预处理", PipelineSession.Status.COMPLETED, true, ""),
                    new PipelineSession.StageView(1, "词法分析", PipelineSession.Status.FAILED, true, "invalid token"),
                    new PipelineSession.StageView(2, "语法分析", PipelineSession.Status.PENDING, false, ""));
            panel.show(new PipelineSession.Snapshot(failed, 1, 1, 14, false, false, true));
            assertTrue(status(panel).getText().contains("invalid token"));
            assertTrue(button(panel, "#pipeline-next-step").isDisabled());
            assertTrue(button(panel, "#pipeline-next-stage").isDisabled());
            stages.getSelectionModel().select(0);
            assertEquals(0, stages.getSelectionModel().getSelectedIndex());
            stages.getSelectionModel().select(2);
            assertEquals(0, stages.getSelectionModel().getSelectedIndex());
            return null;
        });
    }

    private static PipelineSession.Snapshot snapshot(int currentStage, long steps) {
        List<PipelineSession.StageView> stages = IntStream.range(0, PipelineSession.stageLabels().size())
                .mapToObj(index -> new PipelineSession.StageView(index, PipelineSession.stageLabels().get(index),
                        index < currentStage ? PipelineSession.Status.COMPLETED
                                : index == currentStage ? PipelineSession.Status.CURRENT : PipelineSession.Status.PENDING,
                        index <= currentStage, "")).toList();
        return new PipelineSession.Snapshot(stages, currentStage, currentStage, steps, true, false, false);
    }

    private static void layout(PipelinePanel panel, double width, double height) {
        panel.resize(width, height);
        panel.applyCss();
        panel.layout();
    }

    private static void assertRowsFillList(ListView<PipelineSession.StageView> stages) {
        List<ListCell<?>> cells = renderedCells(stages);
        assertEquals(IntStream.range(0, 8).boxed().toList(), cells.stream().map(ListCell::getIndex).toList(),
                "all eight stages must be visible at a normal window height");
        double top = stages.localToScene(stages.getBoundsInLocal()).getMinY() + stages.getInsets().getTop();
        double bottom = stages.localToScene(stages.getBoundsInLocal()).getMaxY() - stages.getInsets().getBottom();
        double firstTop = cells.getFirst().localToScene(cells.getFirst().getBoundsInLocal()).getMinY();
        double lastBottom = cells.getLast().localToScene(cells.getLast().getBoundsInLocal()).getMaxY();
        assertEquals(top, firstTop, 2);
        assertEquals(bottom, lastBottom, 3,
                "the final stage must reach the bottom of the available list area");
        for (int index = 0; index < cells.size(); index++) {
            assertEquals(cells.getFirst().getHeight(), cells.get(index).getHeight(), 1);
            assertTrue(cells.get(index).getHeight() >= 55);
            if (index > 0) {
                ListCell<?> previous = cells.get(index - 1);
                assertEquals(previous.localToScene(previous.getBoundsInLocal()).getMaxY(),
                        cells.get(index).localToScene(cells.get(index).getBoundsInLocal()).getMinY(), 1,
                        "the stage flow must remain continuous between neighboring rows");
            }
        }
        assertFalse(verticalScrollBar(stages).isVisible(), "the evenly distributed rows must fit without scrolling");
    }

    private static List<ListCell<?>> renderedCells(ListView<PipelineSession.StageView> stages) {
        List<ListCell<?>> cells = new ArrayList<>();
        for (var node : stages.lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell && cell.isVisible() && !cell.isEmpty()
                    && cell.getIndex() >= 0 && cell.getIndex() < stages.getItems().size()) cells.add(cell);
        }
        cells.sort(Comparator.comparingInt(ListCell::getIndex));
        return cells;
    }

    private static ScrollBar verticalScrollBar(ListView<?> stages) {
        return stages.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                .filter(bar -> bar.getOrientation() == Orientation.VERTICAL)
                .findFirst().orElseThrow();
    }

    private static Button button(PipelinePanel panel, String selector) {
        return assertInstanceOf(Button.class, panel.lookup(selector));
    }

    private static Label status(PipelinePanel panel) {
        return assertInstanceOf(Label.class, panel.lookup("#pipeline-status"));
    }

    @SuppressWarnings("unchecked")
    private static ListView<PipelineSession.StageView> stages(PipelinePanel panel) {
        return (ListView<PipelineSession.StageView>) assertInstanceOf(ListView.class,
                panel.lookup("#pipeline-stage-list"));
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }
}
