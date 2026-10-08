package craken.debug.visualization;

import craken.SourceRange;
import craken.compiler.ir.IrResult;
import craken.compiler.type.CrakenType;
import craken.debug.DebugCapture;
import craken.debug.DebugVariable;
import craken.debug.DebugRuntime;
import craken.visualization.api.ViewLocation;
import craken.visualization.mutation.DefaultVisualizationSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 捕获投影：选中的变量在根页上得到稳定身份的卡片，值随停止点刷新，
 * 创建/读取/写入映射为访问高亮，离开作用域删除节点；结构展开在停止点进行，
 * 指针目标是结构体时渲染为单页对象图（对象是节点、指针字段是有向边）。
 */
@Tag("visualization-adapter")
final class DebugCaptureProjectorTest {
    private static final DebugVariable COUNTER = variable("counter", DebugVariable.Kind.GLOBAL);

    @Test void capturedVariablesBecomeStableCardsWithValuesAndAccessHighlights() {
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session, new IrResult(List.of()));
        try {
            var reader = new DebugMemoryReader(List.of(block(11, 0x1000, 42)));
            var created = projector.project(List.of(capture(COUNTER, DebugCapture.Kind.CREATE, 0x1000)), reader);
            var page = created.pages().get(created.root().pageId());
            assertEquals(DebugCaptureProjector.PAGE_TYPE, page.type().key());
            var location = page.nodes().values().iterator().next().location();
            assertEquals("counter", page.nodes().get(location.nodeId()).content().label());
            assertEquals("42", page.nodes().get(location.nodeId()).content().fields().get("value"));
            assertEquals("int", page.nodes().get(location.nodeId()).content().fields().get("type"));
            assertEquals(craken.visualization.api.AccessKind.ALLOCATE, session.model().interaction().accessKind());

            var written = projector.project(List.of(capture(COUNTER, DebugCapture.Kind.WRITE, 0x1000)),
                    new DebugMemoryReader(List.of(block(11, 0x1000, 99))));
            var updated = written.pages().get(written.root().pageId()).nodes().get(location.nodeId());
            assertEquals("99", updated.content().fields().get("value"), "content refreshes from the stop snapshot");
            assertEquals(location, session.model().interaction().accessed());
            assertEquals(craken.visualization.api.AccessKind.WRITE, session.model().interaction().accessKind());

            projector.project(List.of(capture(COUNTER, DebugCapture.Kind.READ, 0x1000)), reader);
            assertEquals(craken.visualization.api.AccessKind.READ, session.model().interaction().accessKind());
            assertEquals(1, session.model().pages().get(location.pageId()).nodes().size(), "one variable keeps one card");
        } finally {
            projector.close();
            session.close();
        }
    }

    @Test void threeDimensionalArraysExpandIntoThreeLayersOfPagesAtTheStop() {
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session, new IrResult(List.of()));
        try {
            var type = new CrakenType.ArrayType(new CrakenType.ArrayType(new CrakenType.ArrayType(CrakenType.INT, 3), 3), 3);
            var variable = new DebugVariable("a", type, DebugVariable.Kind.LOCAL, "main",
                    new SourceRange(3, 4, 3, 20), false);
            int[] values = new int[27];
            values[0] = 100;
            values[1] = 7;
            var reader = new DebugMemoryReader(List.of(arrayBlock(21, 0x2000, values)));

            // 文档的分层分页：声明只建第一层，停止点按声明类型一次展开到最深一层，点击只做导航。
            var declared = projector.declare(List.of(variable));
            assertEquals(1, declared.pages().size());
            var first = declared.pages().get(declared.root().pageId());
            assertEquals(List.of("a", "a[0]", "a[1]", "a[2]"), labels(first), "第一层是数组节点 + 三个入口槽");
            var firstArray = first.nodes().values().iterator().next();
            assertEquals(craken.visualization.model.ViewNode.Kind.ARRAY, firstArray.content().kind());
            assertEquals("int[3][3][3]", firstArray.content().fields().get("type"));

            var created = projector.project(List.of(capture(variable, DebugCapture.Kind.CREATE, 0x2000)), reader);
            assertEquals(13, created.pages().size(), "停止点按声明类型展开 1 + 3 + 9 页");
            var firstEntry = created.pages().get(created.root().pageId()).nodes().values().stream()
                    .filter(node -> "a[0]".equals(node.content().label())).findFirst().orElseThrow();
            var second = childPage(created, firstEntry.location());
            assertEquals(List.of("a[0]", "a[0][0]", "a[0][1]", "a[0][2]"), labels(second),
                    "第二层也是数组节点 + 三个入口槽");
            assertEquals("int[3][3]", second.nodes().values().iterator().next().content().fields().get("type"));
            var secondEntry = second.nodes().values().stream()
                    .filter(node -> "a[0][0]".equals(node.content().label())).findFirst().orElseThrow();
            var third = childPage(created, secondEntry.location());
            assertEquals(4, third.nodes().size(), "第三层页面 = 一个 int[3] 数组节点 + 三个连续槽");
            var arrayNode = third.nodes().values().stream()
                    .filter(node -> node.content().kind() == craken.visualization.model.ViewNode.Kind.ARRAY)
                    .findFirst().orElseThrow();
            assertEquals("a[0][0]", arrayNode.content().label());
            assertEquals(craken.visualization.model.ViewNode.Kind.ARRAY, arrayNode.content().kind());
            assertEquals(3, third.composition().values().stream()
                    .filter(link -> link.parent().equals(arrayNode.location())).count(), "数组节点内三个连续槽");

            var updated = projector.project(List.of(capture(variable, DebugCapture.Kind.READ, 0x2000)), reader);
            var updatedFirst = updated.pages().get(updated.root().pageId());
            var updatedFirstEntry = updatedFirst.nodes().values().stream()
                    .filter(node -> "a[0]".equals(node.content().label())).findFirst().orElseThrow();
            var updatedSecond = childPage(updated, updatedFirstEntry.location());
            var updatedSecondEntry = updatedSecond.nodes().values().stream()
                    .filter(node -> "a[0][0]".equals(node.content().label())).findFirst().orElseThrow();
            var updatedThird = childPage(updated, updatedSecondEntry.location());
            var updatedArray = updatedThird.nodes().values().iterator().next();
            var cells = updatedThird.composition().values().stream()
                    .filter(link -> link.parent().equals(updatedArray.location()))
                    .sorted(java.util.Comparator.comparingInt(craken.visualization.model.relation.CompositionLink::slot))
                    .map(link -> updatedThird.nodes().get(link.child().nodeId()).content().label()).toList();
            assertEquals("100", cells.get(0), "a[0][0][0] 显示 100");
            assertEquals("7", cells.get(1), "a[0][0][1] 显示 7");
            assertEquals(craken.visualization.api.AccessKind.READ, session.model().interaction().accessKind(),
                    "最后一次捕获是读取，高亮类型跟随捕获种类（与标量卡片用例一致）");
        } finally {
            projector.close();
            session.close();
        }
    }

    /** 用户最初报告的场景：a[0][1][0] 写入后焦点必须落在第三层元素槽，而不是最高层。 */
    @Test void elementWritesFocusTheDeepestLayerPageOfAThreeDimensionalArray() {
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session, new IrResult(List.of()));
        try {
            var type = new CrakenType.ArrayType(new CrakenType.ArrayType(new CrakenType.ArrayType(CrakenType.INT, 3), 3), 3);
            var variable = new DebugVariable("a", type, DebugVariable.Kind.LOCAL, "main",
                    new SourceRange(3, 4, 3, 20), false);
            int[] values = new int[27];
            values[3] = 100; // 字节偏移 12 = 元素下标 3 = a[0][1][0]
            var reader = new DebugMemoryReader(List.of(arrayBlock(41, 0x4000, values)));
            var root = projector.declare(List.of(variable)).root();
            var written = projector.project(List.of(capture(variable, DebugCapture.Kind.WRITE, 0x4000, 12)), reader);
            var accessed = session.model().interaction().accessed();
            assertNotNull(accessed, "写元素后有访问焦点");
            assertNotEquals(root.pageId(), accessed.pageId(), "焦点页不是最高层");
            assertEquals("a[0][1]", written.pages().get(accessed.pageId()).nodes().values().stream()
                            .filter(node -> node.content().kind() == craken.visualization.model.ViewNode.Kind.ARRAY)
                            .findFirst().orElseThrow().content().label(),
                    "焦点落在 a[0][1] 那一层的槽位");
            assertEquals("100", written.pages().get(accessed.pageId()).nodes().get(accessed.nodeId())
                    .content().label(), "焦点落在被写入的第三层元素槽");
            assertEquals(craken.visualization.api.AccessKind.WRITE, session.model().interaction().accessKind());
        } finally {
            projector.close();
            session.close();
        }
    }

    @Test void pointerMembersExpandToTheTrackedAllocationExtent() {
        var layout = new craken.compiler.semantic.model.StructLayout("S", 16, 8, List.of(
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("data",
                        new CrakenType.PointerType(CrakenType.INT), 0, 8, 8),
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("size",
                        CrakenType.INT, 8, 4, 4)));
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session,
                new IrResult(List.of(), List.of(), java.util.Set.of(), java.util.Map.of("S", layout)));
        try {
            var variable = new DebugVariable("p", new CrakenType.StructType("S"), DebugVariable.Kind.LOCAL,
                    "main", new SourceRange(6, 4, 6, 10), false);
            var reader = new DebugMemoryReader(List.of(
                    structPointerBlock(51, 0x3000, 0x4000, 3),
                    arrayBlock(52, 0x4000, new int[]{5, 6, 7})));
            List<craken.debug.visualization.RuntimeEvent> events = List.of(
                    allocated(1, 51, 0x3000, 16, "stack", "p"),
                    allocated(2, 52, 0x4000, 12, "heap", "buffer"));
            var declared = projector.declare(List.of(variable));
            var page = declared.pages().get(declared.root().pageId());
            var member = page.nodes().values().stream()
                    .filter(node -> "data".equals(node.content().label())).findFirst().orElseThrow();
            assertEquals("int*", member.content().fields().get("type"));
            var projected = projector.project(List.of(capture(variable, DebugCapture.Kind.CREATE, 0x3000)), reader, events);
            var refreshed = projected.pages().get(declared.root().pageId())
                    .nodes().get(member.location().nodeId());
            assertEquals("0x4000", refreshed.content().fields().get("value"), "指针成员先显示指针值本身");

            // 展开发生在停止点投影里；宿主点击只做页面导航，不再读内存。
            var second = childPage(projected, member.location());
            var container = second.nodes().values().iterator().next();
            assertEquals("p.data", container.content().label());
            assertEquals("int[3]", container.content().fields().get("type"),
                    "格数 = 分配块剩余字节 / 单格大小，不需要类型名特判");
            var cells = second.composition().values().stream()
                    .filter(link -> link.parent().equals(container.location()))
                    .sorted(java.util.Comparator.comparingInt(
                            craken.visualization.model.relation.CompositionLink::slot))
                    .map(link -> second.nodes().get(link.child().nodeId()).content().label()).toList();
            assertEquals(List.of("5", "6", "7"), cells);

            // 之后的停止点没有新的捕获事件：指针页的值必须仍然读自目标缓冲区，而不是变量自身。
            var later = projector.project(List.of(), reader);
            var laterPage = childPage(later, member.location());
            var laterCells = laterPage.composition().values().stream()
                    .filter(link -> link.parent().equals(
                            laterPage.nodes().values().stream()
                                    .filter(node -> node.content().kind()
                                            == craken.visualization.model.ViewNode.Kind.ARRAY)
                                    .findFirst().orElseThrow().location()))
                    .sorted(java.util.Comparator.comparingInt(
                            craken.visualization.model.relation.CompositionLink::slot))
                    .map(link -> laterPage.nodes().get(link.child().nodeId()).content().label()).toList();
            assertEquals(List.of("5", "6", "7"), laterCells, "指针页的每个槽都绑定目标缓冲区基址");
        } finally {
            projector.close();
            session.close();
        }
    }

    /** 用户报告的场景：捕获 `Graph *g` 时卡片必须带入口，第二层是单页对象图而不是逐层页链。 */
    @Test void capturedPointerVariablesRenderTheirTargetAsASingleGraphPage() {
        var layout = new craken.compiler.semantic.model.StructLayout("Graph", 16, 8, List.of(
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("n", CrakenType.INT, 0, 4, 4),
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("vertices",
                        new CrakenType.PointerType(new CrakenType.PointerType(new CrakenType.StructType("Vertex"))),
                        8, 8, 8)));
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session,
                new IrResult(List.of(), List.of(), java.util.Set.of(), java.util.Map.of("Graph", layout)));
        try {
            var variable = new DebugVariable("g", new CrakenType.PointerType(new CrakenType.StructType("Graph")),
                    DebugVariable.Kind.LOCAL, "main", new SourceRange(10, 4, 10, 12), false);
            var reader = new DebugMemoryReader(List.of(
                    pointerBlock(91, 0x6000, 0x7000),
                    graphBlock(92, 0x7000, 5, 0x7100)));
            List<craken.debug.visualization.RuntimeEvent> events = List.of(
                    allocated(1, 91, 0x6000, 8, "stack", "g"),
                    allocated(2, 92, 0x7000, 16, "heap", "graph"));

            var declared = projector.declare(List.of(variable));
            var declaredPage = declared.pages().get(declared.root().pageId());
            var declaredCard = declaredPage.nodes().values().iterator().next();
            assertEquals("g", declaredCard.content().label());
            assertEquals("struct Graph*", declaredCard.content().fields().get("type"));
            assertEquals("未解析", declaredCard.content().fields().get("value"));

            var projected = projector.project(List.of(capture(variable, DebugCapture.Kind.CREATE, 0x6000)),
                    reader, events);
            var card = projected.pages().get(projected.root().pageId()).nodes().get(declaredCard.location().nodeId());
            assertEquals("0x6000", card.content().fields().get("address"), "卡片显示变量自身地址");
            assertEquals("0x7000", card.content().fields().get("value"), "指针值单独显示");
            assertTrue(projected.ownership().values().stream().anyMatch(binding ->
                            binding.key().pre().equals(card.location())
                                    && binding.sources().stream().anyMatch(source -> source.startsWith("debug:slot:"))),
                    "指针卡片必须带下一层入口，点击才能展开");

            var second = childPage(projected, card.location());
            assertEquals(DebugCaptureProjector.GRAPH_PAGE_TYPE, second.type().key(),
                    "第二层是对象图页，不再逐个指针建页");
            var graphNode = second.nodes().values().iterator().next();
            assertEquals("struct Graph", graphNode.content().label());
            assertEquals("5", graphNode.content().fields().get("n"), "字段按对象地址读取");
            assertEquals("0x7100", graphNode.content().fields().get("vertices"));
            assertEquals("0x7000", graphNode.content().fields().get("address"));

            var again = projector.project(List.of(), reader, List.of());
            assertEquals(projected.pages().size(), again.pages().size(), "重复投影不再新建页");
        } finally {
            projector.close();
            session.close();
        }
    }

    /** 用户报告的场景：Node *head 链表必须在一页里显示全部节点，next 指针画成有向边。 */
    @Test void capturedPointerChainStaysOnOnePageWithPointerEdges() {
        var layout = new craken.compiler.semantic.model.StructLayout("Node", 16, 8, List.of(
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("data", CrakenType.INT, 0, 4, 4),
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("next",
                        new CrakenType.PointerType(new CrakenType.StructType("Node")), 8, 8, 8)));
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session,
                new IrResult(List.of(), List.of(), java.util.Set.of(), java.util.Map.of("Node", layout)));
        try {
            var variable = new DebugVariable("head", new CrakenType.PointerType(new CrakenType.StructType("Node")),
                    DebugVariable.Kind.LOCAL, "main", new SourceRange(8, 4, 8, 16), false);
            var reader = new DebugMemoryReader(List.of(
                    pointerBlock(101, 0x6000, 0x7000),
                    nodeBlock(102, 0x7000, 5, 0x7010),
                    nodeBlock(103, 0x7010, 10, 0)));
            List<craken.debug.visualization.RuntimeEvent> events = List.of(
                    allocated(1, 101, 0x6000, 8, "stack", "head"),
                    allocated(2, 102, 0x7000, 16, "heap", "node"),
                    allocated(3, 103, 0x7010, 16, "heap", "node"));

            var projected = projector.project(List.of(capture(variable, DebugCapture.Kind.CREATE, 0x6000)),
                    reader, events);
            assertEquals(2, projected.pages().size(), "变量卡片 + 一张链表对象图页");
            var root = projected.pages().get(projected.root().pageId());
            var card = root.nodes().values().iterator().next();
            var graph = childPage(projected, card.location());
            assertEquals(DebugCaptureProjector.GRAPH_PAGE_TYPE, graph.type().key());
            assertEquals(List.of("struct Node", "struct Node"), graph.nodes().values().stream()
                    .map(node -> node.content().label()).sorted().toList());
            assertEquals(List.of("10", "5"), graph.nodes().values().stream()
                    .map(node -> node.content().fields().get("data")).sorted().toList());
            assertEquals(1, graph.topology().size(), "next 指针一条边");
            var edge = graph.topology().values().iterator().next();
            assertEquals("field:next", edge.aPort().equals("field:next") ? edge.aPort() : edge.bPort());
            assertEquals("field-west:next", edge.aPort().equals("field:next") ? edge.bPort() : edge.aPort(),
                    "next 边从目标卡片西侧同名字段进入，保证箭头水平");

            var again = projector.project(List.of(), reader, List.of());
            assertEquals(2, again.pages().size(), "重复投影不再新建页");
            assertEquals(2, again.pages().get(graph.ref().pageId()).nodes().size());
            assertEquals(1, again.pages().get(graph.ref().pageId()).topology().size());

            var checkpoint = projector.checkpoint();
            // 同一批节点上新增一条回边：只更新连线，不重建节点。
            var nodeLocations = again.pages().get(graph.ref().pageId()).nodes().values().stream()
                    .map(node -> node.location()).sorted(java.util.Comparator.comparingLong(ViewLocation::nodeId))
                    .toList();
            var cyclicReader = new DebugMemoryReader(List.of(
                    pointerBlock(101, 0x6000, 0x7000),
                    nodeBlock(102, 0x7000, 5, 0x7010),
                    nodeBlock(103, 0x7010, 10, 0x7000)));
            var cyclic = projector.project(List.of(), cyclicReader, List.of());
            var cyclicGraph = cyclic.pages().get(graph.ref().pageId());
            assertEquals(2, cyclicGraph.nodes().size());
            assertEquals(2, cyclicGraph.topology().size(), "回边只增连线");
            assertEquals(nodeLocations, cyclicGraph.nodes().values().stream()
                    .map(node -> node.location()).sorted(java.util.Comparator.comparingLong(ViewLocation::nodeId))
                    .toList(), "结构不变时节点身份必须稳定");

            // 历史恢复后重建索引：图层根入口不得再走一次旧的分页展开。
            projector.restore(checkpoint);
            var restored = projector.project(List.of(), cyclicReader, List.of());
            assertEquals(2, restored.pages().size(), "历史恢复后不得重复建页");
            assertEquals(2, restored.pages().get(graph.ref().pageId()).topology().size(), "恢复后回边仍然生效");

            // 尾节点被摘掉后，结构变化才重建：第三页里只剩 5 -> NULL。
            var shortened = new DebugMemoryReader(List.of(
                    pointerBlock(101, 0x6000, 0x7000),
                    nodeBlock(102, 0x7000, 5, 0)));
            var rewired = projector.project(List.of(), shortened, List.of());
            var shortenedGraph = rewired.pages().get(graph.ref().pageId());
            assertEquals(1, shortenedGraph.nodes().size(), "不再可达的节点必须移除");
            assertTrue(shortenedGraph.topology().isEmpty(), "摘掉的 next 边必须断开");
        } finally {
            projector.close();
            session.close();
        }
    }

    @Test void veryLargeArraysStayASingleCardInsteadOfThousandsOfCells() {
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session, new IrResult(List.of()));
        try {
            var type = new CrakenType.ArrayType(new CrakenType.ArrayType(CrakenType.INT, 64), 64);
            var variable = new DebugVariable("grid", type, DebugVariable.Kind.LOCAL, "main",
                    new SourceRange(4, 4, 4, 24), false);
            var declared = projector.declare(List.of(variable));
            var node = declared.pages().get(declared.root().pageId()).nodes().values().iterator().next();
            assertEquals(craken.visualization.model.ViewNode.Kind.POINT, node.content().kind());
            assertTrue(declared.ownership().values().stream()
                    .noneMatch(binding -> binding.key().pre().equals(node.location())), "超大数组不建立下游页");
            assertEquals("int[64][64]", node.content().fields().get("type"));
        } finally {
            projector.close();
            session.close();
        }
    }

    @Test void structCardsRenderTheirDeclaredMemberRows() {
        var layout = new craken.compiler.semantic.model.StructLayout("S", 8, 4, List.of(
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("x", CrakenType.INT, 0, 4, 4),
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("y", CrakenType.INT, 4, 4, 4)));
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session,
                new IrResult(List.of(), List.of(), java.util.Set.of(), java.util.Map.of("S", layout)));
        try {
            var variable = new DebugVariable("p", new CrakenType.StructType("S"), DebugVariable.Kind.LOCAL,
                    "main", new SourceRange(5, 4, 5, 10), false);
            var reader = new DebugMemoryReader(List.of(pairBlock(31, 0x3000, 3, 4)));
            var declared = projector.declare(List.of(variable));
            var declaredPage = declared.pages().get(declared.root().pageId());
            var declaredMembers = declaredPage.nodes().values().stream()
                    .filter(node -> node.content().fields().containsKey("value")).toList();
            assertEquals(2, declaredMembers.size());
            assertTrue(declaredMembers.stream().allMatch(node -> "未解析".equals(node.content().fields().get("value"))),
                    "地址未知时成员槽显示占位符而不是空白：" + declaredMembers.stream()
                            .map(node -> node.content().fields()).toList());
            var created = projector.project(List.of(capture(variable, DebugCapture.Kind.CREATE, 0x3000)), reader);
            var page = created.pages().get(created.root().pageId());
            var container = page.nodes().values().stream()
                    .filter(node -> node.content().kind() == craken.visualization.model.ViewNode.Kind.ARRAY)
                    .findFirst().orElseThrow();
            assertEquals("p", container.content().label());
            var members = page.composition().values().stream()
                    .filter(link -> link.parent().equals(container.location()))
                    .sorted(java.util.Comparator.comparingInt(
                            craken.visualization.model.relation.CompositionLink::slot))
                    .map(link -> page.nodes().get(link.child().nodeId())).toList();
            assertEquals(List.of("x", "y"), members.stream().map(node -> node.content().label()).toList(),
                    "成员名字保留在标题");
            assertEquals("3", members.get(0).content().fields().get("value"), "struct 成员值逐行渲染");
            assertEquals("4", members.get(1).content().fields().get("value"));
        } finally {
            projector.close();
            session.close();
        }
    }

    @Test void selfReferentialStructuresExpandWithinBudgetAndStayStable() {
        var layout = new craken.compiler.semantic.model.StructLayout("Node", 16, 8, List.of(
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("value", CrakenType.INT, 0, 4, 4),
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("next",
                        new CrakenType.PointerType(new CrakenType.StructType("Node")), 8, 8, 8)));
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session,
                new IrResult(List.of(), List.of(), java.util.Set.of(), java.util.Map.of("Node", layout)));
        try {
            var variable = new DebugVariable("n", new CrakenType.StructType("Node"), DebugVariable.Kind.LOCAL,
                    "main", new SourceRange(9, 4, 9, 10), false);
            var reader = new DebugMemoryReader(List.of(selfReferentialBlock(81, 0x5000, 7)));
            List<craken.debug.visualization.RuntimeEvent> events =
                    List.of(allocated(1, 81, 0x5000, 16, "heap", "n"));
            var projected = projector.project(List.of(capture(variable, DebugCapture.Kind.CREATE, 0x5000)), reader, events);
            assertTrue(projected.pages().size() <= 4,
                    "自引用结构必须终止并受预算约束，实际页数 " + projected.pages().size());
            var again = projector.project(List.of(), reader, List.of());
            assertEquals(projected.pages().size(), again.pages().size(), "重复投影不再新建页");
        } finally {
            projector.close();
            session.close();
        }
    }

    @Test void pointerTargetWithoutRecordedAllocationStaysUnexpanded() {
        var layout = new craken.compiler.semantic.model.StructLayout("S", 16, 8, List.of(
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("data",
                        new CrakenType.PointerType(CrakenType.INT), 0, 8, 8)));
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session,
                new IrResult(List.of(), List.of(), java.util.Set.of(), java.util.Map.of("S", layout)));
        try {
            var variable = new DebugVariable("p", new CrakenType.StructType("S"), DebugVariable.Kind.LOCAL,
                    "main", new SourceRange(8, 4, 8, 10), false);
            var reader = new DebugMemoryReader(List.of(
                    structPointerBlock(71, 0x3000, 0x4000, 3),
                    arrayBlock(72, 0x4000, new int[]{5, 6, 7})));
            // 只有变量的栈分配事件，没有缓冲区的分配事件：目标范围未知，不能靠内存探测补出来。
            List<craken.debug.visualization.RuntimeEvent> events =
                    List.of(allocated(1, 71, 0x3000, 16, "stack", "p"));
            var projected = projector.project(List.of(capture(variable, DebugCapture.Kind.CREATE, 0x3000)), reader, events);
            var page = projected.pages().get(projected.root().pageId());
            var member = page.nodes().values().stream()
                    .filter(node -> "data".equals(node.content().label())).findFirst().orElseThrow();
            assertEquals("0x4000", member.content().fields().get("value"), "仍显示指针值本身");
            assertEquals(1, projected.pages().size(), "没有分配事件就不展开，不靠点击时探测内存");
            assertEquals(2, page.nodes().size());
        } finally {
            projector.close();
            session.close();
        }
    }

    @Test void everyStopRefreshesDeclaredValuesEvenWithoutNewCaptureEvents() {
        var layout = new craken.compiler.semantic.model.StructLayout("S", 8, 4, List.of(
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("x", CrakenType.INT, 0, 4, 4),
                new craken.compiler.semantic.model.StructLayout.StructFieldLayout("y", CrakenType.INT, 4, 4, 4)));
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session,
                new IrResult(List.of(), List.of(), java.util.Set.of(), java.util.Map.of("S", layout)));
        try {
            var variable = new DebugVariable("p", new CrakenType.StructType("S"), DebugVariable.Kind.LOCAL,
                    "main", new SourceRange(7, 4, 7, 10), false);
            var uninitialized = new DebugMemoryReader(List.of(uninitializedPairBlock(61, 0x3000)));
            var declared = projector.declare(List.of(variable));
            projector.project(List.of(capture(variable, DebugCapture.Kind.CREATE, 0x3000)), uninitialized);
            var first = projector.publishedSnapshot().pages().get(declared.root().pageId());
            var initial = first.nodes().values().stream()
                    .filter(node -> node.content().fields().containsKey("value")).toList();
            assertEquals(2, initial.size());
            assertTrue(initial.stream()
                            .allMatch(node -> "<未初始化>".equals(node.content().fields().get("value"))),
                    "构造前成员显示未初始化：" + initial.stream()
                            .map(node -> node.content().fields()).toList());

            // 之后没有新的捕获事件，只是停在另一个停止点：值仍必须按该停止点的快照刷新。
            var ready = new DebugMemoryReader(List.of(pairBlock(62, 0x3000, 3, 4)));
            var refreshed = projector.project(List.of(), ready);
            var members = refreshed.pages().get(declared.root().pageId()).nodes().values().stream()
                    .filter(node -> node.content().fields().containsKey("value")).toList();
            assertEquals(List.of("3", "4"),
                    members.stream().map(node -> node.content().fields().get("value")).toList(),
                    "没有捕获事件的停止点也要刷新已登记变量的值");
        } finally {
            projector.close();
            session.close();
        }
    }

    @Test void leavingScopeKeepsTheCardMarkedAndCheckpointsRestoreIt() {
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session, new IrResult(List.of()));
        try {
            var reader = new DebugMemoryReader(List.of(block(11, 0x1000, 7)));
            projector.project(List.of(capture(COUNTER, DebugCapture.Kind.CREATE, 0x1000)), reader);
            var checkpoint = projector.checkpoint();
            var removed = projector.project(List.of(capture(COUNTER, DebugCapture.Kind.REMOVE, 0x1000)), reader);
            var nodes = removed.pages().get(removed.root().pageId()).nodes();
            assertEquals(1, nodes.size(), "leaving scope keeps the captured card visible");
            assertTrue(nodes.values().iterator().next().content().fields().get("value").startsWith("已离开作用域"),
                    nodes.values().iterator().next().content().fields().toString());
            assertEquals(craken.visualization.api.AccessKind.DELETE, session.model().interaction().accessKind());
            projector.restore(checkpoint);
            var published = projector.publishedSnapshot();
            var restored = published.pages().get(published.root().pageId()).nodes().values().iterator().next();
            assertEquals("7", restored.content().fields().get("value"), "history restores the live value");
        } finally {
            projector.close();
            session.close();
        }
    }

    @Test void unreadableAndUnresolvedValuesShowExplicitMarkers() {
        var session = new DefaultVisualizationSession();
        var projector = new DebugCaptureProjector(session, new IrResult(List.of()));
        try {
            var reader = new DebugMemoryReader(List.of(block(11, 0x1000, 1)));
            var unresolved = projector.project(List.of(capture(COUNTER, DebugCapture.Kind.READ, 0)), reader);
            var page = unresolved.pages().get(unresolved.root().pageId());
            assertEquals("未解析", page.nodes().values().iterator().next().content().fields().get("value"));
            var uninitialized = projector.project(List.of(capture(COUNTER, DebugCapture.Kind.CREATE, 0x2000)),
                    new DebugMemoryReader(List.of(uninitializedBlock(12, 0x2000))));
            assertTrue(uninitialized.pages().get(uninitialized.root().pageId()).nodes().values().stream()
                    .anyMatch(node -> "<未初始化>".equals(node.content().fields().get("value"))));
        } finally {
            projector.close();
            session.close();
        }
    }

    private static DebugVariable variable(String name, DebugVariable.Kind kind) {
        return new DebugVariable(name, CrakenType.INT, kind, "",
                new SourceRange(1, 0, 1, 8), false);
    }

    private static DebugCapture capture(DebugVariable variable, DebugCapture.Kind kind, long address) {
        return new DebugCapture(variable, kind, "main", new SourceRange(2, 0, 2, 3), address);
    }

    private static DebugCapture capture(DebugVariable variable, DebugCapture.Kind kind, long address, int offset) {
        return new DebugCapture(variable, kind, "main", new SourceRange(2, 0, 2, 3), address, offset);
    }

    private static DebugRuntime.MemoryBlock block(long id, long address, int value) {
        byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
        return new DebugRuntime.MemoryBlock(address, 4, "value", HexFormat.of().formatHex(bytes), 4, id, "0f");
    }

    private static DebugRuntime.MemoryBlock uninitializedBlock(long id, long address) {
        byte[] bytes = new byte[4];
        return new DebugRuntime.MemoryBlock(address, 4, "value", HexFormat.of().formatHex(bytes), 0, id, "00");
    }

    private static DebugRuntime.MemoryBlock arrayBlock(long id, long address, int[] values) {
        byte[] bytes = new byte[values.length * Integer.BYTES];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : values) buffer.putInt(value);
        return new DebugRuntime.MemoryBlock(address, bytes.length, "array",
                HexFormat.of().formatHex(bytes), bytes.length, id, mask(bytes.length));
    }

    private static DebugRuntime.MemoryBlock pairBlock(long id, long address, int first, int second) {
        byte[] bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(first).putInt(second).array();
        return new DebugRuntime.MemoryBlock(address, bytes.length, "pair",
                HexFormat.of().formatHex(bytes), bytes.length, id, "ff");
    }

    private static DebugRuntime.MemoryBlock structPointerBlock(long id, long address, long pointer, int size) {
        byte[] bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putLong(pointer).putInt(size).array();
        return new DebugRuntime.MemoryBlock(address, bytes.length, "struct",
                HexFormat.of().formatHex(bytes), bytes.length, id, mask(bytes.length));
    }

    private static DebugRuntime.MemoryBlock pointerBlock(long id, long address, long pointer) {
        byte[] bytes = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(pointer).array();
        return new DebugRuntime.MemoryBlock(address, bytes.length, "pointer",
                HexFormat.of().formatHex(bytes), bytes.length, id, mask(bytes.length));
    }

    private static DebugRuntime.MemoryBlock graphBlock(long id, long address, int n, long vertices) {
        byte[] bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(n).putInt(0).putLong(vertices).array();
        return new DebugRuntime.MemoryBlock(address, bytes.length, "graph",
                HexFormat.of().formatHex(bytes), bytes.length, id, mask(bytes.length));
    }

    private static DebugRuntime.MemoryBlock nodeBlock(long id, long address, int data, long next) {
        byte[] bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(data).putInt(0).putLong(next).array();
        return new DebugRuntime.MemoryBlock(address, bytes.length, "node",
                HexFormat.of().formatHex(bytes), bytes.length, id, mask(bytes.length));
    }

    private static DebugRuntime.MemoryBlock uninitializedPairBlock(long id, long address) {
        byte[] bytes = new byte[8];
        return new DebugRuntime.MemoryBlock(address, bytes.length, "pair",
                HexFormat.of().formatHex(bytes), 0, id, "00");
    }

    private static DebugRuntime.MemoryBlock selfReferentialBlock(long id, long address, int value) {
        byte[] bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value).putInt(0).putLong(address).array();
        return new DebugRuntime.MemoryBlock(address, bytes.length, "node",
                HexFormat.of().formatHex(bytes), bytes.length, id, mask(bytes.length));
    }

    private static craken.debug.visualization.RuntimeEvent.Allocated allocated(
            long sequence, long allocationId, long address, int size, String storage, String label) {
        return new craken.debug.visualization.RuntimeEvent.Allocated(sequence,
                new craken.debug.visualization.RuntimeEvent.MemoryRange(allocationId, address, size), storage, label);
    }

    /** 前 bits 位全部置位的初始化掩码（十六进制按字节对齐）。 */
    private static String mask(int bits) {
        int full = bits / 8, remainder = bits % 8;
        StringBuilder value = new StringBuilder("ff".repeat(full));
        if (remainder > 0) value.append(String.format("%02x", (1 << remainder) - 1));
        return value.toString();
    }

    private static List<String> labels(craken.visualization.snapshot.VisualizationSnapshot.PageState page) {
        return page.nodes().values().stream()
                .sorted(java.util.Comparator.comparingLong(node -> node.location().nodeId()))
                .map(node -> node.content().label())
                .toList();
    }

    private static craken.visualization.snapshot.VisualizationSnapshot.PageState childPage(
            craken.visualization.snapshot.VisualizationSnapshot model, ViewLocation parent) {
        var target = model.ownership().values().stream()
                .filter(binding -> binding.key().pre().equals(parent) && !binding.key().nxt().page().equals(parent.page()))
                .map(binding -> binding.key().nxt()).findFirst().orElseThrow();
        return model.pages().get(target.pageId());
    }
}
