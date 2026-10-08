package craken.ui.debug;

import craken.compiler.SourceFile;
import craken.debug.visualization.DebugCaptureProjector;
import craken.ui.component.UiStyles;
import craken.ui.component.visualization.UiVisualizationContainer;
import craken.visualization.layout.LayoutRequest;
import craken.visualization.snapshot.VisualizationSnapshot.PageState;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户报告的场景：`Graph *g` 这种对象图必须在一页里渲染节点与指针边，
 * 而不是每个指针跳转一层页；环状结构交图布局完成且没有布局错误。
 */
@Tag("visualization-adapter")
final class DebugGraphRenderingTest {
    private static final String SOURCE = """
            #include <stdio.h>
            #include <stdlib.h>

            #define INF 999999

            typedef struct Vertex Vertex;
            typedef struct Edge Edge;

            struct Edge { Vertex *dest; int weight; Edge *next; };
            struct Vertex { int id; int dist; int visited; Vertex *prev; Edge *edges; };
            typedef struct { int n; Vertex **vertices; } Graph;

            Graph *createGraph(int n) {
                Graph *g = (Graph *)malloc(sizeof(Graph));
                g->n = n;
                g->vertices = (Vertex **)malloc(n * sizeof(Vertex *));
                for (int i = 0; i < n; i++) {
                    Vertex *v = (Vertex *)malloc(sizeof(Vertex));
                    v->id = i;
                    v->dist = INF;
                    v->visited = 0;
                    v->prev = 0;
                    v->edges = 0;
                    g->vertices[i] = v;
                }
                return g;
            }

            void addEdge(Vertex *src, Vertex *dest, int weight) {
                Edge *e = (Edge *)malloc(sizeof(Edge));
                e->dest = dest;
                e->weight = weight;
                e->next = src->edges;
                src->edges = e;
            }

            int main(void) {
                Graph *g = createGraph(4);
                Vertex *v0 = g->vertices[0];
                Vertex *v1 = g->vertices[1];
                addEdge(v0, v1, 4); addEdge(v1, v0, 4);
                addEdge(v0, g->vertices[2], 1);
                addEdge(v1, g->vertices[3], 2);
                printf("%d\\n", g->n);
                return 0;
            }
            """;

    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); } catch (IllegalStateException running) { Platform.runLater(start); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test void pointerGraphRendersAsOnePageWithNodesAndPointerEdges() throws Exception {
        var session = new DebugWorkbenchSession(new SourceFile("graph.mc", SOURCE), Set.of());
        try {
            var variable = session.search("g").stream()
                    .filter(candidate -> candidate.function().contains("main")).findFirst().orElseThrow();
            session.start(List.of(variable));
            PageState graph = null;
            for (int step = 0; step < 120; step++) {
                var current = session.snapshot();
                graph = current.visualization() == null ? graph
                        : current.visualization().pages().values().stream()
                                .filter(page -> page.type().key().equals(DebugCaptureProjector.GRAPH_PAGE_TYPE))
                                .findFirst().orElse(graph);
                if (graph != null && graph.nodes().size() >= 10 && graph.topology().size() >= 10) break;
                session.stepInto();
            }
            var visualization = session.snapshot().visualization();
            assertNotNull(visualization);
            graph = visualization.pages().values().stream()
                    .filter(page -> page.type().key().equals(DebugCaptureProjector.GRAPH_PAGE_TYPE))
                    .findFirst().orElseThrow();
            assertEquals(2, visualization.pages().size(), "变量卡片 + 一张对象图页");
            assertTrue(graph.nodes().size() >= 10, "对象图节点数 " + graph.nodes().size());
            assertTrue(graph.topology().size() >= 10, "对象图连线数 " + graph.topology().size());
            assertTrue(graph.nodes().values().stream()
                            .anyMatch(node -> node.content().label().contains("Vertex")),
                    "顶点是图上的节点");
            assertTrue(graph.nodes().values().stream()
                            .anyMatch(node -> node.content().label().contains("Edge")),
                    "边表也是图上的节点");

            var host = onFx(() -> {
                var view = new UiVisualizationContainer();
                var scene = new Scene(view, 1200, 800);
                UiStyles.install(scene);
                view.showSnapshot(visualization);
                view.resize(1200, 800);
                view.applyCss();
                view.layout();
                return view;
            });
            try {
                awaitLayout(host);
                onFx(() -> {
                    var card = host.displayModel().pages().get(host.displayModel().root().pageId())
                            .nodes().values().iterator().next();
                    var pane = host.visibleOccurrences().getFirst().nodeViews().get(card.location());
                    assertNotNull(pane, "g 卡片已渲染");
                    pane.getOnMouseClicked().handle(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                            MouseButton.PRIMARY, 1, false, false, false, false, true, false, false,
                            true, false, false, null));
                    return null;
                });
                awaitLayout(host);
                onFx(() -> {
                    assertFalse(host.isLayoutPending(), "对象图布局必须完成");
                    assertTrue(host.diagnostics().engineRuns()
                                    .getOrDefault(LayoutRequest.Kind.GRAPH, 0L) > 0,
                            "环状对象图必须走图布局");
                    var parts = host.visibleOccurrences().stream()
                            .flatMap(occurrence -> occurrence.parts().stream()).toList();
                    assertFalse(parts.isEmpty());
                    assertTrue(parts.stream().allMatch(part -> part.errorText().isEmpty()),
                            parts.stream().map(part -> part.errorText()).toList().toString());
                    return null;
                });
            } finally {
                onFx(() -> { host.close(); return null; });
            }
        } finally {
            session.close();
        }
    }

    private static void awaitLayout(UiVisualizationContainer host) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            boolean idle = onFx(() -> {
                host.applyCss();
                host.layout();
                return !host.isLayoutPending();
            });
            if (idle) {
                onFx(() -> null);
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("对象图布局未在预算内完成");
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(60, TimeUnit.SECONDS);
    }
}
