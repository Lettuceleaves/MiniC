package craken.ui.component.visualization;

import craken.visualization.api.ViewLocation;
import craken.visualization.layout.LayoutRequest;
import craken.visualization.layout.LayoutResult;
import craken.visualization.layout.LinearLayout;
import craken.visualization.layout.PortRouter;
import craken.visualization.model.*;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Text;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static craken.visualization.layout.LayoutRequest.*;
import static craken.visualization.layout.LayoutResult.*;

@Tag("visualization-ui")
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class VisualizationRendererFxTest {
    private static ViewLocation location(long id) { return new ViewLocation(1, 1, id); }
    private static final class NodeModel extends ViewNode {
        NodeModel(long id, Spec spec, List<ViewLocation> children) { super(VisualizationRendererFxTest.location(id), spec, Retention.ROOT, ParentSelection.ROOT, children); }
        @Override public ViewNode withState(Spec spec, ParentSelection parents) { return new NodeModel(location().nodeId(), spec, children()); }
        @Override public ViewNode withChildren(List<ViewLocation> children) { return new NodeModel(location().nodeId(), content(), children); }
    }
    private static Map<Long, ViewNode> matrix() {
        var map = new LinkedHashMap<Long, ViewNode>();
        map.put(1L, new NodeModel(1, new ViewNode.Spec(ViewNode.Kind.ARRAY, "二维数组"), List.of(location(2), location(3))));
        map.put(2L, new NodeModel(2, new ViewNode.Spec(ViewNode.Kind.ARRAY, "row[0]"), List.of(location(4), location(5))));
        map.put(3L, new NodeModel(3, new ViewNode.Spec(ViewNode.Kind.ARRAY, "row[1]"), List.of(location(6), location(7))));
        map.put(4L, new NodeModel(4, ViewNode.Spec.point("1"), List.of()));
        map.put(5L, new NodeModel(5, ViewNode.Spec.point("a longer value"), List.of()));
        map.put(6L, new NodeModel(6, ViewNode.Spec.point("333"), List.of()));
        map.put(7L, new NodeModel(7, new ViewNode.Spec(ViewNode.Kind.POINT, "4", Map.of("next", "address=0x20")), List.of()));
        return map;
    }
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable task = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(task); } catch (IllegalStateException started) { Platform.runLater(task); }
        ready.get(10, TimeUnit.SECONDS);
    }
    private static <T> T onFx(Callable<T> task) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(task.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        try { return result.get(10, TimeUnit.SECONDS); }
        catch (ExecutionException failure) { if (failure.getCause() instanceof Error error) throw error; throw (Exception)failure.getCause(); }
    }
    @Test void measuresEveryMatrixRowAndCellAndAlignsVariableColumns() throws Exception {
        onFx(() -> {
            var nodes = matrix(); var theme = VisualizationTheme.of(VisualizationTheme.Preset.BLUE);
            var result = new ViewNodeRenderer().render(nodes.get(1L), nodes, theme, Set.of(location(7)));
            assertEquals(7, result.unit().members().size()); assertEquals(7, result.members().size());
            var bounds = new HashMap<ViewLocation, Rect>(); result.unit().members().forEach(m -> bounds.put(m.node(), m.bounds()));
            assertTrue(bounds.get(location(2)).y() < bounds.get(location(3)).y());
            assertEquals(bounds.get(location(4)).x(), bounds.get(location(6)).x());
            assertEquals(bounds.get(location(5)).x(), bounds.get(location(7)).x());
            assertEquals(bounds.get(location(4)).width(), bounds.get(location(6)).width());
            assertTrue(result.unit().size().width() > 200);
            assertEquals(result.unit(), new FxNodeMeasurer().measure(nodes.get(1L), nodes, theme, Set.of(location(7))));
            return null;
        });
    }
    @Test void createsIndependentFxInstancesForRepeatedPageOccurrences() throws Exception {
        onFx(() -> {
            var nodes = matrix(); var renderer = new ViewNodeRenderer(); var theme = VisualizationTheme.of(VisualizationTheme.Preset.TEAL);
            var first = renderer.render(nodes.get(1L), nodes, theme, Set.of(location(4)));
            var second = renderer.render(nodes.get(1L), nodes, theme, Set.of(location(7)));
            assertNotSame(first.view(), second.view());
            for (var id : first.members().keySet()) assertNotSame(first.members().get(id), second.members().get(id));
            var parent = new HBox(first.view(), second.view()); new Scene(parent); parent.applyCss(); parent.layout();
            assertEquals(2, parent.getChildren().size()); assertEquals(first.unit(), second.unit());
            return null;
        });
    }
    @Test void fixedColorsAndInsideOutlinesNeverChangeMeasuredGeometry() throws Exception {
        onFx(() -> {
            var nodes = matrix(); var renderer = new ViewNodeRenderer();
            var normal = renderer.render(nodes.get(1L), nodes, VisualizationTheme.of(VisualizationTheme.Preset.BLUE), Set.of());
            var selected = renderer.render(nodes.get(1L), nodes, VisualizationTheme.of(VisualizationTheme.Preset.RED), Set.of(location(4)));
            assertEquals(normal.unit(), selected.unit());
            var leaves = descendants(selected.view());
            assertTrue(leaves.stream().anyMatch(n -> n instanceof Text));
            for (var node : leaves) {
                if (node instanceof Text text) assertEquals(Color.WHITE, text.getFill());
                if (node instanceof Rectangle rect && rect.getStroke() != null) {
                    assertEquals(StrokeType.INSIDE, rect.getStrokeType()); assertEquals(12, rect.getArcWidth());
                    assertEquals(1, ((Color)rect.getFill()).getOpacity());
                }
            }
            return null;
        });
    }
    @Test void neutralIdeCardsFollowTheApprovedOutlineAndLabelHierarchy() throws Exception {
        onFx(() -> {
            var nodes = matrix();
            var theme = VisualizationTheme.of(VisualizationTheme.Preset.NEUTRAL);
            var normal = new ViewNodeRenderer().render(nodes.get(7L), nodes, theme, Set.of());
            var body = (Rectangle) normal.view().getChildren().getFirst();
            var header = (Rectangle) normal.view().getChildren().get(1);
            assertEquals(Color.web("#161B22"), body.getFill());
            assertEquals(Color.web("#30363D"), body.getStroke());
            assertEquals(1.0, body.getStrokeWidth());
            assertEquals(Color.web("#21262D"), header.getFill());
            var selected = new ViewNodeRenderer().render(nodes.get(7L), nodes, theme, Set.of(location(7)));
            var outline = (Rectangle) selected.view().getChildren().getFirst();
            assertEquals(Color.web("#58A6FF"), outline.getStroke());
            assertEquals(2.0, outline.getStrokeWidth());
            assertEquals(normal.unit(), selected.unit(), "the highlight must not change measured geometry");
            var fills = descendants(selected.view()).stream().filter(Text.class::isInstance)
                    .map(node -> ((Text) node).getFill()).toList();
            assertTrue(fills.contains(Color.web("#8B949E")), "field names use the muted IDE label color");
            assertTrue(fills.contains(Color.WHITE), "field values stay white");
            return null;
        });
    }
    @Test void exposesCellAndFieldPortsAtActualMeasuredMemberBoundaries() throws Exception {
        onFx(() -> {
            var nodes = matrix(); var unit = new FxNodeMeasurer().measure(nodes.get(1L), nodes,
                    VisualizationTheme.of(VisualizationTheme.Preset.VIOLET), Set.of());
            var field = unit.ports().stream().filter(p -> p.ref().equals(new PortRef(location(7), "field:next"))).findFirst().orElseThrow();
            var cell = unit.members().stream().filter(m -> m.node().equals(location(7))).findFirst().orElseThrow().bounds();
            assertEquals(Side.EAST, field.side()); assertEquals(cell.right(), field.anchor().x());
            assertTrue(cell.contains(field.anchor())); assertEquals(7, unit.ports().stream().filter(p -> p.ref().key().equals("node")).count());
            assertFalse(unit.textObstacles().isEmpty());
            return null;
        });
    }
    @Test void rendersCubicArrowsFromTangentsForAllDirections() throws Exception {
        onFx(() -> {
            var edge = new EdgePath(new Point(0, 0), List.of(new Cubic(new Point(0, 100), new Point(100, 100), new Point(100, 0))));
            var renderer = new EdgeRenderer();
            for (var direction : Direction.values()) {
                var group = renderer.render(edge, direction);
                var arrows = group.getChildren().stream().filter(n -> n instanceof Polygon).map(n -> (Polygon)n).toList();
                assertEquals(direction == Direction.NONE ? 0 : direction == Direction.BOTH ? 2 : 1, arrows.size());
                assertTrue(group.getChildren().getFirst() instanceof javafx.scene.shape.Path);
                for (var arrow : arrows) {
                    var points = arrow.getPoints(); double tipY = points.get(1);
                    assertEquals(0, tipY); assertTrue(points.get(3) > 0); assertTrue(points.get(5) > 0);
                }
            }
            assertEquals(1, renderer.render(new EdgePath(new Point(2, 2), List.of(new Line(new Point(2, 2)))), Direction.BOTH).getChildren().size());
            return null;
        });
    }
    @Test void aRealMeasuredMatrixCellRoutesToAnExternalNodeWithoutCrossingSiblingCards() throws Exception {
        onFx(() -> {
            var nodes = matrix(); var unit = new FxNodeMeasurer().measure(nodes.get(1L), nodes,
                    VisualizationTheme.of(VisualizationTheme.Preset.BLUE), Set.of());
            var request = new LayoutRequest(new Stamp(location(1).page(), 1, 0, 0, 1, 800), Kind.LINEAR,
                    List.of(unit, Unit.simple(location(8), 64, 48)),
                    List.of(new Link(1, new PortRef(location(7), "field:next"), PortRef.node(location(8)), Direction.FORWARD)),
                    Hints.defaults(), Map.of());
            var result = new LinearLayout().layout(request); var route = result.edgePaths().get(1L); var previous = route.start();
            for (var segment : route.segments()) {
                for (long sibling : new long[]{4, 5, 6}) assertFalse(PortRouter.crosses(previous, segment.end(), result.nodeBounds().get(location(sibling))));
                previous = segment.end();
            }
            var port = unit.ports().stream().filter(p -> p.ref().equals(new PortRef(location(7), "field:next"))).findFirst().orElseThrow();
            assertEquals(new Point(port.anchor().x() + 8, port.anchor().y() + 8), route.start());
            assertEquals(1, new EdgeRenderer().render(route, Direction.NONE).getChildren().size());
            return null;
        });
    }
    @Test void largerActualFontsChangeGeometryAndKeepEveryMemberPortValid() throws Exception {
        onFx(() -> {
            var nodes = matrix(); var theme = VisualizationTheme.of(VisualizationTheme.Preset.BLUE);
            var normal = new FxNodeMeasurer(Font.font("System", 14)).measure(nodes.get(1L), nodes, theme, Set.of());
            var large = new FxNodeMeasurer(Font.font("System", 28)).measure(nodes.get(1L), nodes, theme, Set.of());
            assertTrue(large.size().width() > normal.size().width()); assertTrue(large.size().height() > normal.size().height());
            for (var port : large.ports()) {
                var member = large.members().stream().filter(m -> m.node().equals(port.ref().node())).findFirst().orElseThrow();
                assertTrue(member.bounds().contains(port.anchor()));
            }
            return null;
        });
    }
    @Test void aDeepZeroNestingCompositionDoesNotUseTheJavaOrFxParentStack() throws Exception {
        onFx(() -> {
            var nodes = new LinkedHashMap<Long, ViewNode>();
            for (int i = 1; i <= 1500; i++) nodes.put((long)i, new NodeModel(i, ViewNode.Spec.point("address"),
                    i == 1500 ? List.of() : List.of(location(i + 1))));
            var result = new ViewNodeRenderer().render(nodes.get(1L), nodes, VisualizationTheme.of(VisualizationTheme.Preset.BLUE), Set.of());
            assertEquals(1500, result.unit().members().size());
            for (var member : result.members().entrySet()) if (member.getKey().nodeId() != 1) assertSame(result.view(), member.getValue().getParent());
            result.view().applyCss(); result.view().layout();
            return null;
        });
    }
    @Test void refusesToTouchFxNodesOutsideTheFxThread() {
        var nodes = matrix(); var theme = VisualizationTheme.of(VisualizationTheme.Preset.BLUE);
        assertThrows(IllegalStateException.class, () -> new ViewNodeRenderer().render(nodes.get(1L), nodes, theme, Set.of()));
        assertThrows(IllegalStateException.class, () -> new FxNodeMeasurer().measure(nodes.get(1L), nodes, theme, Set.of()));
        assertThrows(IllegalStateException.class, () -> new EdgeRenderer().render(new EdgePath(new Point(0, 0), List.of()), Direction.NONE));
    }
    @Test void modelColorOverridesTheMemberDefaultAndInheritedColorUsesIt() throws Exception {
        onFx(() -> {
            var explicit = new NodeModel(1,new ViewNode.Spec(ViewNode.Kind.POINT,"red",Map.of(),
                    craken.visualization.style.ColorSpec.Preset.RED),List.of(location(2)));
            var inherited = new NodeModel(2,ViewNode.Spec.point("teal"),List.of());
            var nodes = Map.<Long,ViewNode>of(1L,explicit,2L,inherited);
            var result = new ViewNodeRenderer().render(explicit,nodes,Map.of(
                    location(1),VisualizationTheme.of(VisualizationTheme.Preset.BLUE),
                    location(2),VisualizationTheme.of(VisualizationTheme.Preset.TEAL)),Set.of());
            assertEquals(Color.web("#70434A"),((Rectangle)result.members().get(location(1)).getChildren().getFirst()).getFill());
            assertEquals(Color.web("#285C53"),((Rectangle)result.members().get(location(2)).getChildren().getFirst()).getFill());
            return null;
        });
    }
    @Test void rejectsIncompleteOrCyclicCompositionBeforeAttachingAnyView() throws Exception {
        onFx(() -> {
            var nodes = matrix(); var renderer = new ViewNodeRenderer(); var theme = VisualizationTheme.of(VisualizationTheme.Preset.BLUE);
            var missing = new HashMap<>(nodes); missing.remove(4L);
            assertThrows(IllegalArgumentException.class, () -> renderer.render(nodes.get(1L), missing, theme, Set.of()));
            var cycle = new HashMap<>(nodes); cycle.put(4L, nodes.get(4L).withChildren(List.of(location(1))));
            assertThrows(IllegalArgumentException.class, () -> renderer.render(cycle.get(1L), cycle, theme, Set.of()));
            return null;
        });
    }
    @Test void savesARealFxMatrixScreenshotWithVisibleCellLabels() throws Exception {
        onFx(() -> {
            var nodes = matrix(); var result = new ViewNodeRenderer().render(nodes.get(1L), nodes,
                    VisualizationTheme.of(VisualizationTheme.Preset.BLUE), Set.of(location(7)));
            var root = new Pane(result.view()); result.view().relocate(24, 24);
            root.setPrefSize(result.unit().size().width() + 48, result.unit().size().height() + 48);
            root.setBackground(new Background(new BackgroundFill(Color.rgb(13, 17, 23), null, null)));
            var stage = new Stage(); stage.setScene(new Scene(root));
            try {
                stage.show(); root.applyCss(); root.layout();
                var snapshot = root.snapshot(null, null);
                assertEquals(Color.rgb(13, 17, 23), snapshot.getPixelReader().getColor(2, 2));
                Path directory = Path.of(System.getProperty("craken.visualization.screenshots", "build/verification/screenshots"));
                Files.createDirectories(directory); ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", directory.resolve("renderer-2d.png").toFile());
                assertTrue(snapshot.getWidth() > 200); assertTrue(snapshot.getHeight() > 150);
            } finally { stage.close(); }
            return null;
        });
    }
    private static List<Node> descendants(Node root) {
        var result = new ArrayList<Node>(); var queue = new ArrayDeque<Node>(); queue.add(root);
        while (!queue.isEmpty()) { var node = queue.remove(); result.add(node); if (node instanceof Parent parent) queue.addAll(parent.getChildrenUnmodifiable()); }
        return result;
    }
}
