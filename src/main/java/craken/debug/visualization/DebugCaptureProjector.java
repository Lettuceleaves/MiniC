package craken.debug.visualization;

import craken.SourceRange;
import craken.compiler.ir.IrResult;
import craken.compiler.semantic.model.StructLayout;
import craken.compiler.type.CrakenType;
import craken.debug.DebugCapture;
import craken.debug.DebugVariable;
import craken.visualization.api.*;
import craken.visualization.model.PageModel;
import craken.visualization.model.ParentSelection;
import craken.visualization.model.ViewNode;
import craken.visualization.model.node.ArrayViewNode;
import craken.visualization.model.node.PointViewNode;
import craken.visualization.model.relation.TopologyEdge;
import craken.visualization.snapshot.VisualizationSnapshot;
import craken.visualization.style.ColorSpec;
import craken.visualization.style.EdgeStyle;

import java.util.*;

/**
 * 调试侧的捕获投影。全部规则只看 IR 的声明类型与结构体布局，没有任何具体类型（STL 或自定义）的名字特判：
 *
 * <ul>
 *   <li>{@code ArrayType}：每一层数组一个页面，层内槽位是下一层入口或元素值；</li>
 *   <li>{@code StructType}：结构体是一个容器节点，成员按 {@link StructLayout} 的声明顺序与偏移成为槽位，
 *       标量成员显示值，成员本身是数组/结构体/指向结构体或数组的指针时成为可点击入口；</li>
 *   <li>{@code PointerType}：指向结构体时不再逐层建页，目标对象与它引用的对象画成同一张对象图页
 *       （对象是节点、指针字段是有向边，链式结构自动排直，环状结构交图布局）；指向数组时按
 *       运行期分配记录展开；指向标量时显示指针值；空指针显示 NULL；</li>
 *   <li>循环由"沿展开路径的地址集合"阻断，规模由单变量节点上限阻断，两者都与类型无关。</li>
 * </ul>
 */
public final class DebugCaptureProjector implements AutoCloseable {
    /** 值槽绑定自己的读取基址：变量相对层基址为 0，指针展开层基址是指针目标，合并后各层互不串用。 */
    public record ValueSlot(ViewLocation node, CrakenType type, int offset, long base) {}
    public record Entry(ViewLocation node, DebugVariable variable, String path, CrakenType type, int offset,
                        long base, boolean pointer, int depth, Set<Long> ancestors) {
        public Entry { ancestors = Set.copyOf(ancestors); }
    }
    public record Expansion(ViewLocation anchor, List<ValueSlot> values, List<ViewLocation> nodes, List<Entry> entries,
                            long base) {
        public Expansion {
            values = List.copyOf(values);
            nodes = List.copyOf(nodes);
            entries = List.copyOf(entries);
        }
    }
    /** 对象图节点身份：对象地址 + 槽位下标（-1 表示对象/数组容器本身）。 */
    public record GraphKey(long address, int slot) {}
    /** 对象图页的增量状态：根入口、页面与节点位置，历史检查点按快照恢复。 */
    public record GraphState(Entry root, PageRef page, Map<GraphKey, ViewLocation> nodes) {
        public GraphState {
            nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        }
    }
    public record Checkpoint(VisualizationSnapshot snapshot, PageRef page, Map<SourceRange, Expansion> expansions,
                             Map<Long, RuntimeEvent.MemoryRange> allocations,
                             Map<SourceRange, Set<Long>> expandedTargets, Map<SourceRange, Integer> pageBudget,
                             Map<ViewLocation, GraphState> graphs) {
        public Checkpoint {
            expansions = Map.copyOf(expansions);
            allocations = Map.copyOf(allocations);
            expandedTargets = Map.copyOf(expandedTargets);
            pageBudget = Map.copyOf(pageBudget);
            graphs = Collections.unmodifiableMap(new LinkedHashMap<>(graphs));
        }
    }
    static final String PAGE_TYPE = "debug-capture";
    public static final String GRAPH_PAGE_TYPE = "debug-graph";
    private static final int MAX_CELLS = 512;
    private static final int MAX_NODES = 2048;
    /** 单个变量的展开预算：map/树这类互相引用的结构必须有界，超出就不再建页（入口保留）。 */
    private static final int MAX_NODES_PER_VARIABLE = 1024;
    private static final int MAX_PAGES_PER_VARIABLE = 48;
    private static final int MAX_POINTER_DEPTH = 6;
    /** 对象图预算：节点、数组槽与连线都必须有界，避免连成一团的数据结构把页面炸开。 */
    private static final int MAX_GRAPH_NODES = 192;
    private static final int MAX_GRAPH_ARRAY_SLOTS = 128;
    private static final int MAX_GRAPH_EDGES = 1024;

    /** 捕获页：数组与结构体成员的包含层级都不设默认上限（只要求非负）。 */
    private record CapturePage(String key) implements PageType {
        @Override public boolean readyEnabled() { return false; }
        @Override public int maximumNesting() { return Integer.MAX_VALUE; }
        @Override public Layout layout() { return Layout.ARRAY; }
        @Override public Set<ViewNode.Kind> nodeKinds() { return Set.of(ViewNode.Kind.POINT, ViewNode.Kind.ARRAY); }
        @Override public ViewNode create(ViewLocation location, ViewNode.Spec spec, ViewNode.Retention retention,
                                         ParentSelection parents) {
            return switch (spec.kind()) {
                case POINT -> new PointViewNode(location, spec, retention, parents);
                case ARRAY -> new ArrayViewNode(location, spec, retention, parents);
                default -> throw new IllegalArgumentException("Unsupported capture node kind: " + spec.kind());
            };
        }
    }

    /** 对象图页：对象是节点、指针关系是拓扑边；链式结构由 LINEAR 自动排直，其余交图布局。 */
    private record GraphPage(String key) implements PageType {
        @Override public boolean readyEnabled() { return false; }
        @Override public int maximumNesting() { return Integer.MAX_VALUE; }
        @Override public Layout layout() { return Layout.LINEAR; }
        @Override public Set<ViewNode.Kind> nodeKinds() { return Set.of(ViewNode.Kind.POINT, ViewNode.Kind.ARRAY); }
        @Override public ViewNode create(ViewLocation location, ViewNode.Spec spec, ViewNode.Retention retention,
                                         ParentSelection parents) {
            return switch (spec.kind()) {
                case POINT -> new PointViewNode(location, spec, retention, parents);
                case ARRAY -> new ArrayViewNode(location, spec, retention, parents);
                default -> throw new IllegalArgumentException("Unsupported graph node kind: " + spec.kind());
            };
        }
    }

    private record ObjectRef(long address, CrakenType type) {}
    private record GraphSlot(GraphKey container, int index, GraphKey slot) {}
    private record GraphEdge(GraphKey tail, String tailPort, GraphKey head, String headPort) {}
    private record GraphPlan(LinkedHashMap<GraphKey, ViewNode.Spec> specs, List<GraphSlot> slots,
                             List<GraphEdge> edges, GraphKey root) {
        static GraphPlan empty() { return new GraphPlan(new LinkedHashMap<>(), List.of(), List.of(), null); }
    }

    private final VisualizationSession session;
    private final IrResult ir;
    private final boolean ownsSession;
    private final PageTypeRegistry types = new PageTypeRegistry();
    private final Map<SourceRange, Expansion> expansions = new LinkedHashMap<>();
    private final Map<SourceRange, DebugVariable> declaredVariables = new LinkedHashMap<>();
    private final Map<ViewLocation, Entry> entries = new LinkedHashMap<>();
    private final Map<SourceRange, Long> addresses = new LinkedHashMap<>();
    /** 运行期 IR 分配事件登记表：指针目标的格数只从这里来，不靠点击时探测内存。 */
    private final Map<Long, RuntimeEvent.MemoryRange> recordedAllocations = new LinkedHashMap<>();
    /** 每个变量已经展开过的指针目标地址：别名与回边不再重复建页，防止 map/树的父指针把页数炸开。 */
    private final Map<SourceRange, Set<Long>> expandedTargets = new LinkedHashMap<>();
    private final Map<SourceRange, Integer> pageBudget = new LinkedHashMap<>();
    /** 指针目标是结构体的变量改用单页对象图表达，这里保存每个图页的增量状态。 */
    private final Map<ViewLocation, GraphState> graphs = new LinkedHashMap<>();
    private PageRef page;
    private VisualizationSnapshot published;
    private long revision;

    public DebugCaptureProjector(VisualizationSession session, IrResult ir) {
        this(session, ir, false);
    }

    public DebugCaptureProjector(VisualizationSession session, IrResult ir, boolean ownsSession) {
        this.session = Objects.requireNonNull(session, "session");
        this.ir = Objects.requireNonNull(ir, "ir");
        this.ownsSession = ownsSession;
        types.register(new CapturePage(PAGE_TYPE));
        types.register(new GraphPage(GRAPH_PAGE_TYPE));
        published = session.snapshot();
    }

    public VisualizationSnapshot publishedSnapshot() { return published; }

    public Checkpoint checkpoint() {
        return new Checkpoint(published, page, expansions, recordedAllocations, expandedTargets, pageBudget, graphs);
    }

    public void restore(Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        session.restore(checkpoint.snapshot());
        page = checkpoint.page();
        expansions.clear();
        expansions.putAll(checkpoint.expansions());
        recordedAllocations.clear();
        recordedAllocations.putAll(checkpoint.allocations());
        expandedTargets.clear();
        expandedTargets.putAll(checkpoint.expandedTargets());
        pageBudget.clear();
        pageBudget.putAll(checkpoint.pageBudget());
        graphs.clear();
        graphs.putAll(checkpoint.graphs());
        rebuildIndexes();
        published = session.snapshot();
    }

    /** “开始”时登记选中变量：结构体与数组按类型建第一层，其余显示值卡片。 */
    public VisualizationSnapshot declare(List<DebugVariable> variables) {
        Objects.requireNonNull(variables, "variables");
        if (!variables.isEmpty() && page == null) page = session.initializeRoot(types.require(PAGE_TYPE));
        var commands = new ArrayList<VisualizationCommand>();
        for (var variable : variables) {
            if (expansions.containsKey(variable.definition())) continue;
            declareOnDemand(variable, commands);
        }
        apply(commands, "capture:declare");
        return published;
    }

    /** 测试与旧调用方使用的入口：没有运行期事件时只刷新值，不展开指针。 */
    public VisualizationSnapshot project(List<DebugCapture> captures, DebugMemoryReader memory) {
        return project(captures, memory, List.of());
    }

    /**
     * 停止点投影：消费本区间的运行期 IR 事件（分配/释放/重分配）建立分配登记表，刷新值，
     * 并按 IR 的声明类型 + 分配登记表在停止点把结构展开成页面。点击只做页面导航，不读内存。
     */
    public VisualizationSnapshot project(List<DebugCapture> captures, DebugMemoryReader memory,
                                         List<RuntimeEvent> events) {
        Objects.requireNonNull(captures, "captures");
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(events, "events");
        ingest(events);
        if (captures.isEmpty() && expansions.isEmpty()) return published;
        if (page == null) page = session.initializeRoot(types.require(PAGE_TYPE));
        var setup = new ArrayList<VisualizationCommand>();
        for (var capture : captures) {
            if (!expansions.containsKey(capture.variable().definition()))
                declareOnDemand(capture.variable(), setup);
            if (capture.address() > 0) addresses.put(capture.variable().definition(), capture.address());
        }
        apply(setup, "capture:declare");

        var refresh = new ArrayList<VisualizationCommand>();
        // 值属于停止点的内存快照：每个停止点都要刷新已登记变量，不能只在有捕获事件的停止点刷新，
        // 否则成员槽会停留在上一次捕获时的旧值（例如构造前的“未初始化”）。
        for (var known : expansions.entrySet()) {
            var variable = declaredVariables.get(known.getKey());
            if (variable != null) refreshValues(known.getValue(), variable, memory, refresh);
        }
        apply(refresh, "capture:refresh:" + (++revision));

        // 结构在停止点由 IR 类型与分配记录展开；点击入口只是焦点导航。
        // 展开本身不改变当前焦点，否则每停一步焦点都会跳到最后一个新建的槽。
        var previousFocus = session.model().interaction().focus();
        expandReachable(memory);
        refreshGraphLayers(memory);
        var anchor = previousFocus != null ? previousFocus
                : expansions.values().stream().findFirst().map(Expansion::anchor).orElse(null);
        if (anchor != null && session.model().node(anchor).parents() != null)
            apply(List.of(new VisualizationCommand.SetFocus(path(anchor))), "capture:focus");

        var commands = new ArrayList<VisualizationCommand>();
        for (var capture : captures) {
            var expansion = expansions.get(capture.variable().definition());
            if (expansion == null) continue;
            if (capture.kind() == DebugCapture.Kind.REMOVE) {
                var node = session.model().node(expansion.anchor());
                if (!node.content().fields().containsKey("state")) {
                    var fields = new LinkedHashMap<>(node.content().fields());
                    fields.put("value", "已离开作用域 · 行 " + capture.range().startLine());
                    fields.put("state", "removed");
                    commands.add(new VisualizationCommand.SetContent(path(expansion.anchor()),
                            spec(node.content().kind(), node.content().label(), fields, ColorSpec.Preset.NEUTRAL)));
                }
                commands.add(new VisualizationCommand.Touch(path(expansion.anchor()), AccessKind.DELETE));
            } else {
                var target = deepestCaptureNode(capture, expansion.anchor());
                commands.add(new VisualizationCommand.Touch(path(target), access(capture.kind())));
            }
        }
        apply(commands, "capture:" + (++revision));
        return published;
    }

    /** 运行期事件 → 分配登记表：分配/释放/重分配都以事件为准，不猜内存布局。 */
    private void ingest(List<RuntimeEvent> events) {
        for (var event : events) {
            if (event instanceof RuntimeEvent.Allocated allocated) {
                recordedAllocations.put(allocated.range().allocationId(), allocated.range());
            } else if (event instanceof RuntimeEvent.Released released) {
                recordedAllocations.remove(released.range().allocationId());
            } else if (event instanceof RuntimeEvent.Reallocated reallocated) {
                if (reallocated.previous() != null)
                    recordedAllocations.remove(reallocated.previous().allocationId());
                recordedAllocations.put(reallocated.replacement().allocationId(), reallocated.replacement());
            }
        }
    }

    /**
     * 中间步进：只并入事件与地址、按需登记变量，不做结构展开与值刷新。跨断点命令可能走过成千个
     * 上下文，最终停止点由 {@link #project} 完整投影，中间上下文只需保持事件登记表连续。
     */
    public void observe(List<DebugCapture> captures, List<RuntimeEvent> events) {
        Objects.requireNonNull(captures, "captures");
        Objects.requireNonNull(events, "events");
        ingest(events);
        var commands = new ArrayList<VisualizationCommand>();
        for (var capture : captures) {
            if (capture.address() > 0) addresses.put(capture.variable().definition(), capture.address());
            if (expansions.containsKey(capture.variable().definition())) continue;
            if (page == null) page = session.initializeRoot(types.require(PAGE_TYPE));
            declareOnDemand(capture.variable(), commands);
        }
        apply(commands, "capture:observe");
    }

    /**
     * 读写命中的元素：页面已在停止点建好，这里只按捕获里的字节偏移沿声明类型定位最深的值槽，
     * 不读内存、不建页。数组按元素步长，结构体按字段区间。
     */
    private ViewLocation deepestCaptureNode(DebugCapture capture, ViewLocation fallback) {
        int offset = capture.offset();
        if (offset < 0) return fallback;
        var expansion = expansions.get(capture.variable().definition());
        if (expansion == null) return fallback;
        var type = capture.variable().declaredType().unqualified();
        var path = capture.variable().sourceName();
        int remaining = offset;
        int slot = -1;
        boolean descended = false;
        ViewLocation located = fallback;
        while (true) {
            String nextPath;
            CrakenType nextType;
            int index;
            if (type instanceof CrakenType.ArrayType array) {
                int stride = typeSize(array.elementType());
                if (stride <= 0) break;
                index = remaining / stride;
                if (index < 0 || index >= array.length()) break;
                remaining -= index * stride;
                nextPath = path + "[" + index + "]";
                nextType = array.elementType().unqualified();
            } else {
                var layout = structLayout(type);
                if (layout == null) break;
                int position = remaining;
                index = -1;
                var fields = layout.fields();
                for (int candidate = 0; candidate < fields.size(); candidate++) {
                    var field = fields.get(candidate);
                    if (position >= field.offset() && position < (long) field.offset() + field.size()) {
                        index = candidate;
                        break;
                    }
                }
                if (index < 0) break;
                var field = fields.get(index);
                remaining -= field.offset();
                nextPath = path + "." + field.name();
                nextType = field.type().unqualified();
            }
            path = nextPath;
            type = nextType;
            slot = index;
            String currentPath = path;
            var entry = expansion.entries().stream()
                    .filter(candidate -> candidate.path().equals(currentPath)
                            && candidate.variable().definition().equals(capture.variable().definition()))
                    .findFirst().orElse(null);
            if (entry != null) {
                located = entry.node();
                descended = true;
                slot = -1;
                continue;
            }
            break;
        }
        if (slot < 0) return located;
        // 入口节点与下一页容器同名，必须沿归属走一步拿到真正的容器，不能按标签查。
        var container = descended ? ownedChild(located) : located;
        if (container == null) return fallback;
        int leafSlot = slot;
        return session.model().pages().get(container.pageId()).composition().values().stream()
                .filter(link -> link.parent().equals(container) && link.slot() == leafSlot)
                .map(link -> link.child()).findFirst().orElse(fallback);
    }

    /** 入口在下一页的容器节点；没有跨页归属时返回 null。 */
    private ViewLocation ownedChild(ViewLocation parent) {
        return session.model().ownership().values().stream()
                .filter(binding -> binding.key().pre().equals(parent)
                        && !binding.key().nxt().page().equals(parent.page()))
                .map(binding -> binding.key().nxt())
                .min(Comparator.comparingLong(ViewLocation::nodeId)).orElse(null);
    }

    /**
     * 停止点把每个已知入口展开成下一层：数组/结构体按 IR 声明类型下降，指针按运行期分配登记表
     * 推断格数。展开在停止点完成，宿主点击只切换页面焦点，不在点击时读内存或建页。
     */
    private void expandReachable(DebugMemoryReader memory) {
        var pending = new ArrayDeque<Entry>(entries.values());
        var attempted = new HashSet<ViewLocation>();
        int budget = MAX_NODES;
        while (!pending.isEmpty() && budget-- > 0) {
            var entry = pending.poll();
            if (entry == null || !attempted.add(entry.node())) continue;
            if (!entries.containsKey(entry.node())) continue;
            var definition = entry.variable().definition();
            var expansion = expansions.get(definition);
            if (expansion == null || expansion.nodes().size() >= MAX_NODES_PER_VARIABLE) continue;
            if (pageBudget.getOrDefault(definition, 0) >= MAX_PAGES_PER_VARIABLE) continue;
            if (entry.pointer() && entry.depth() >= MAX_POINTER_DEPTH) continue;
            var child = expandOnce(entry, memory);
            if (child == null) continue;
            pageBudget.merge(definition, 1, Integer::sum);
            entries.remove(entry.node());
            pending.addAll(child.entries());
        }
    }

    /** 展开一个入口；失败返回 null（入口保留，等到下一个停止点再试）。 */
    private Expansion expandOnce(Entry entry, DebugMemoryReader memory) {
        long base = entry.base() != 0 ? entry.base() : addresses.getOrDefault(entry.variable().definition(), 0L);
        long target = base + entry.offset();
        if (entry.pointer()) {
            // 指针值读自当前停止点的不可变快照，发生在停止点投影内，不在点击时。
            if (base == 0) return null;
            try { target = memory.readPointer(memory.resolve(base), entry.offset()); }
            catch (DebugMemoryReader.MemoryReadException failure) { return null; }
            if (target == 0) {
                var node = session.model().node(entry.node());
                apply(List.of(new VisualizationCommand.SetContent(path(entry.node()),
                        spec(node.content().kind(), node.content().label(), Map.of(
                                "name", node.content().label(), "value", "NULL", "type",
                                fieldTypeName(entry)), ColorSpec.Preset.NEUTRAL))), "capture:null");
                return null;
            }
            // 循环只可能由指针跟随产生；数组/结构体沿类型下降时地址可以相同，不算循环。
            if (entry.ancestors().contains(target)) return null;
            // 指向结构体的指针不再逐层建页：目标对象与它引用的对象画成同一张对象图。
            var pointee = ((CrakenType.PointerType) entry.type().unqualified()).pointee();
            if (structLayout(pointee) != null) {
                // 图层已存在（例如历史恢复后重建索引）时不再展开，由停止点刷新负责对账。
                if (graphs.containsKey(entry.node())) return null;
                var graph = openGraphLayer(entry, target, pointee, memory);
                if (graph != null) {
                    expansions.put(entry.variable().definition(),
                            merge(expansions.get(entry.variable().definition()), graph));
                    return graph;
                }
            }
        }
        var type = entry.type().unqualified();
        int inferredCount = 0;
        if (entry.pointer()) {
            // 指针目标没有声明长度：只认运行期分配事件记录的块，剩余字节除以单格大小得到格数。
            var pointee = ((CrakenType.PointerType) type).pointee();
            inferredCount = extentCount(target, pointee);
            if (inferredCount <= 0) return null;
            // 同一目标只展开一次：map/树的 parent/left/right 会反复指向同一节点，别名不重复建页。
            if (!expandedTargets.computeIfAbsent(entry.variable().definition(), key -> new LinkedHashSet<>())
                    .add(target)) return null;
        } else if (!(type instanceof CrakenType.ArrayType) && structLayout(type) == null) {
            return null;
        }
        var childPage = session.initializePage(types.require(PAGE_TYPE), entry.node());
        var commands = new ArrayList<VisualizationCommand>();
        var ancestors = new LinkedHashSet<>(entry.ancestors());
        if (entry.pointer()) ancestors.add(target);
        Expansion child;
        int childDepth = entry.depth() + 1;
        if (entry.pointer()) {
            var pointee = ((CrakenType.PointerType) type).pointee();
            child = buildArrayLayer(entry.variable(), childPage, entry.node(),
                    new CrakenType.ArrayType(pointee, inferredCount), entry.path(), 0, target, ancestors,
                    childDepth, commands);
        } else if (type instanceof CrakenType.ArrayType array) {
            child = buildArrayLayer(entry.variable(), childPage, entry.node(), array, entry.path(),
                    entry.offset(), base, ancestors, childDepth, commands);
        } else {
            var layout = structLayout(type);
            child = buildStructLayer(entry.variable(), childPage, entry.node(), layout, entry.path(),
                    entry.offset(), base, ancestors, childDepth, commands);
        }
        expansions.put(entry.variable().definition(), merge(expansions.get(entry.variable().definition()), child));
        apply(commands, "capture:expand");
        // 新层节点已落库，随后按当前停止点快照刷新该层的值。
        var refresh = new ArrayList<VisualizationCommand>();
        refreshValues(child, entry.variable(), memory, refresh);
        apply(refresh, "capture:expand:values");
        return child;
    }

    /** 按声明类型就地建立第一层：数组/结构体成容器，标量成值卡片。 */
    private Expansion declareOnDemand(DebugVariable variable, List<VisualizationCommand> commands) {
        var unqualified = variable.declaredType().unqualified();
        Expansion expansion;
        if (unqualified instanceof CrakenType.ArrayType array) {
            expansion = buildArrayLayer(variable, page, null, array, variable.sourceName(), 0, 0, Set.of(), 0, commands);
        } else {
            var layout = structLayout(unqualified);
            if (layout != null) {
                expansion = buildStructLayer(variable, page, null, layout, variable.sourceName(), 0, 0, Set.of(), 0,
                        commands);
            } else if (unqualified instanceof CrakenType.PointerType) {
                // 指针变量与指针成员一样是入口：目标格数只认运行期分配事件，停止点再展开第二层。
                expansion = declarePointerEntry(variable, commands);
            } else {
                var location = session.reserveNodeId(page);
                commands.add(new VisualizationCommand.AddNode(new OperationPath(null, location), card(variable, 0)));
                expansion = new Expansion(location, List.of(new ValueSlot(location, variable.declaredType(), 0, 0)),
                        List.of(location), List.of(), 0);
            }
        }
        expansions.put(variable.definition(), expansion);
        declaredVariables.put(variable.definition(), variable);
        return expansion;
    }

    /** 指针变量的第一层入口：显示变量地址、指针值与类型，展开发生在停止点投影里。 */
    private Expansion declarePointerEntry(DebugVariable variable, List<VisualizationCommand> commands) {
        var location = session.reserveNodeId(page);
        var fields = new LinkedHashMap<String, String>();
        fields.put("type", typeName(variable.declaredType()));
        fields.put("value", "未解析");
        fields.put("address", "未解析");
        commands.add(new VisualizationCommand.AddNode(new OperationPath(null, location),
                spec(ViewNode.Kind.POINT, variable.sourceName(), fields, ColorSpec.Preset.NEUTRAL)));
        var entry = new Entry(location, variable, variable.sourceName(), variable.declaredType(), 0, 0,
                true, 0, Set.of());
        entries.put(location, entry);
        return new Expansion(location, List.of(new ValueSlot(location, variable.declaredType(), 0, 0)),
                List.of(location), List.of(entry), 0);
    }

    /** 指针目标是结构体时，把可达对象与指针关系投影成同一张对象图页（不再每个指针一层页）。 */
    private Expansion openGraphLayer(Entry entry, long target, CrakenType pointee, DebugMemoryReader memory) {
        var plan = planGraph(target, pointee, memory);
        if (plan.root() == null || plan.specs().isEmpty()) return null;
        var graphPage = session.initializePage(types.require(GRAPH_PAGE_TYPE), entry.node());
        var commands = new ArrayList<VisualizationCommand>();
        var nodes = new LinkedHashMap<GraphKey, ViewLocation>();
        for (var key : plan.specs().keySet()) nodes.put(key, session.reserveNodeId(graphPage));
        for (var planned : plan.specs().entrySet())
            commands.add(new VisualizationCommand.AddNode(new OperationPath(entry.node(), nodes.get(planned.getKey())),
                    planned.getValue()));
        for (var slot : plan.slots())
            commands.add(new VisualizationCommand.Compose(nodes.get(slot.container()), nodes.get(slot.slot()),
                    slot.index()));
        commands.add(new VisualizationCommand.AttachOwnership(entry.node(), nodes.get(plan.root()),
                "debug:slot:" + entry.path()));
        for (var edge : plan.edges())
            addGraphConnect(commands, edge, nodes,
                    location -> new OperationPath(entry.node(), location));
        apply(commands, "capture:graph");
        graphs.put(entry.node(), new GraphState(entry, graphPage, nodes));
        return new Expansion(nodes.get(plan.root()), List.of(), List.copyOf(nodes.values()), List.of(), target);
    }

    /**
     * 以声明类型 + 运行期分配记录摊平对象图：结构体对象一个节点，指向结构体的字段一条边，
     * 指向结构体的指针数组建一个容器节点加槽位；按对象地址去重，环自然被收敛。
     */
    private GraphPlan planGraph(long rootAddress, CrakenType rootType, DebugMemoryReader memory) {
        var specs = new LinkedHashMap<GraphKey, ViewNode.Spec>();
        var slots = new ArrayList<GraphSlot>();
        var edges = new LinkedHashSet<GraphEdge>();
        var pending = new ArrayDeque<ObjectRef>();
        var visited = new HashSet<Long>();
        pending.add(new ObjectRef(rootAddress, rootType));
        GraphKey root = null;
        while (!pending.isEmpty() && specs.size() < MAX_GRAPH_NODES && edges.size() < MAX_GRAPH_EDGES) {
            var reference = pending.poll();
            if (reference.address() == 0 || !visited.add(reference.address())) continue;
            var layout = structLayout(reference.type().unqualified());
            if (layout == null || !live(memory, reference.address())) continue;
            var key = new GraphKey(reference.address(), -1);
            if (root == null) root = key;
            var fields = new LinkedHashMap<String, String>();
            for (var field : layout.fields()) {
                var fieldType = field.type().unqualified();
                if (!(fieldType instanceof CrakenType.PointerType pointer)) {
                    fields.put(field.name(), valueText(field.type(), reference.address(), field.offset(), memory));
                    continue;
                }
                Long value = readPointer(memory, reference.address(), field.offset());
                fields.put(field.name(), value == null ? "不可读" : value == 0 ? "NULL" : hex(value));
                if (value == null || value == 0) continue;
                var pointee = pointer.pointee().unqualified();
                if (structLayout(pointee) != null) {
                    edges.add(new GraphEdge(key, "field:" + field.name(), new GraphKey(value, -1),
                            fieldEntryPort(pointee, field.name())));
                    pending.add(new ObjectRef(value, pointer.pointee()));
                } else if (pointee instanceof CrakenType.PointerType elements
                        && structLayout(elements.pointee()) != null) {
                    appendPointerArray(specs, slots, edges, pending, memory, key, field.name(), value, elements);
                }
            }
            fields.put("address", hex(reference.address()));
            specs.put(key, spec(ViewNode.Kind.POINT, typeName(reference.type()), fields, ColorSpec.Preset.NEUTRAL));
        }
        return new GraphPlan(specs, slots, List.copyOf(edges), root);
    }

    /** 目标卡片有同名字段时从它的西侧同高接入（链表箭头保持水平），否则接左边缘中点。 */
    private String fieldEntryPort(CrakenType pointee, String field) {
        var layout = structLayout(pointee);
        if (layout == null) return "west";
        return layout.fields().stream().anyMatch(candidate -> candidate.name().equals(field))
                ? "field-west:" + field : "west";
    }

    /** 指向结构体的指针数组：一个容器节点 + N 个槽位，每个非空槽位连到目标对象。 */
    private void appendPointerArray(Map<GraphKey, ViewNode.Spec> specs, List<GraphSlot> slots,
                                    Set<GraphEdge> edges, Deque<ObjectRef> pending, DebugMemoryReader memory,
                                    GraphKey owner, String name, long address, CrakenType.PointerType elements) {
        int count = extentCount(address, elements);
        if (count <= 0) return;
        count = Math.min(count, MAX_GRAPH_ARRAY_SLOTS);
        var container = new GraphKey(address, -1);
        boolean first = specs.putIfAbsent(container, spec(ViewNode.Kind.ARRAY, name,
                Map.of("type", typeName(new CrakenType.ArrayType(elements, count)), "address", hex(address)),
                ColorSpec.Preset.NEUTRAL)) == null;
        edges.add(new GraphEdge(owner, "field:" + name, container, "node"));
        if (!first) return;
        for (int index = 0; index < count && specs.size() < MAX_GRAPH_NODES; index++) {
            long slotAddress = address + (long) index * typeSize(elements);
            Long element = readPointer(memory, slotAddress, 0);
            var slot = new GraphKey(address, index);
            specs.put(slot, spec(ViewNode.Kind.POINT, "[" + index + "]",
                    Map.of("value", element == null ? "不可读" : element == 0 ? "NULL" : hex(element)),
                    ColorSpec.Preset.NEUTRAL));
            slots.add(new GraphSlot(container, index, slot));
            if (element != null && element != 0) {
                edges.add(new GraphEdge(slot, "node", new GraphKey(element, -1), "node"));
                pending.add(new ObjectRef(element, elements.pointee()));
            }
        }
    }

    /** 每个停止点重算对象图：结构不变只刷新值与边，结构变化才重建节点。 */
    private void refreshGraphLayers(DebugMemoryReader memory) {
        for (var state : List.copyOf(graphs.values())) {
            var entry = state.root();
            var definition = entry.variable().definition();
            long base = entry.base() != 0 ? entry.base() : addresses.getOrDefault(definition, 0L);
            if (base == 0 || session.model().pages().get(state.page().pageId()) == null) continue;
            Long target;
            try { target = memory.readPointer(memory.resolve(base), entry.offset()); }
            catch (DebugMemoryReader.MemoryReadException failure) { continue; }
            var pointee = ((CrakenType.PointerType) entry.type().unqualified()).pointee();
            reconcileGraph(state, target == 0 ? GraphPlan.empty() : planGraph(target, pointee, memory));
        }
    }

    private void reconcileGraph(GraphState state, GraphPlan plan) {
        var pageModel = session.model().pages().get(state.page().pageId());
        if (pageModel == null) return;
        var commands = new ArrayList<VisualizationCommand>();
        if (!state.nodes().keySet().equals(plan.specs().keySet())) {
            for (var location : deletionOrder(pageModel, state.nodes().values()))
                commands.add(new VisualizationCommand.DeleteNode(path(location)));
            var nodes = new LinkedHashMap<GraphKey, ViewLocation>();
            for (var key : plan.specs().keySet()) nodes.put(key, session.reserveNodeId(state.page()));
            for (var planned : plan.specs().entrySet())
                commands.add(new VisualizationCommand.AddNode(
                        new OperationPath(state.root().node(), nodes.get(planned.getKey())), planned.getValue()));
            if (plan.root() != null) {
                for (var slot : plan.slots())
                    commands.add(new VisualizationCommand.Compose(nodes.get(slot.container()), nodes.get(slot.slot()),
                            slot.index()));
                commands.add(new VisualizationCommand.AttachOwnership(state.root().node(), nodes.get(plan.root()),
                        "debug:slot:" + state.root().path()));
            }
            for (var edge : plan.edges())
                addGraphConnect(commands, edge, nodes,
                        location -> new OperationPath(state.root().node(), location));
            apply(commands, "capture:graph:" + (++revision));
            graphs.put(state.root().node(), new GraphState(state.root(), state.page(), nodes));
            return;
        }
        for (var planned : plan.specs().entrySet()) {
            var location = state.nodes().get(planned.getKey());
            if (!session.model().node(location).content().equals(planned.getValue()))
                commands.add(new VisualizationCommand.SetContent(path(location), planned.getValue()));
        }
        reconcileGraphEdges(commands, pageModel, state.nodes(), plan.edges());
        apply(commands, "capture:graph:" + (++revision));
    }

    private void reconcileGraphEdges(List<VisualizationCommand> commands, PageModel pageModel,
                                     Map<GraphKey, ViewLocation> locations, List<GraphEdge> desiredEdges) {
        var byLocation = new HashMap<ViewLocation, GraphKey>();
        locations.forEach((key, location) -> byLocation.put(location, key));
        var desired = new LinkedHashSet<>(desiredEdges);
        for (var edge : pageModel.topology().values()) {
            var a = byLocation.get(edge.a());
            var b = byLocation.get(edge.b());
            if (a == null || b == null) continue;
            GraphEdge current = switch (edge.direction()) {
                case FORWARD -> new GraphEdge(a, edge.aPort(), b, edge.bPort());
                case BACKWARD -> new GraphEdge(b, edge.bPort(), a, edge.aPort());
                default -> new GraphEdge(a, edge.aPort(), b, edge.bPort());
            };
            if (!desired.remove(current))
                commands.add(new VisualizationCommand.Disconnect(path(edge.a()), path(edge.b()),
                        edge.aPort(), edge.bPort()));
        }
        for (var edge : desired) addGraphConnect(commands, edge, locations);
    }

    private void addGraphConnect(List<VisualizationCommand> commands, GraphEdge edge,
                                 Map<GraphKey, ViewLocation> locations) {
        addGraphConnect(commands, edge, locations, this::path);
    }

    private void addGraphConnect(List<VisualizationCommand> commands, GraphEdge edge,
                                 Map<GraphKey, ViewLocation> locations,
                                 java.util.function.Function<ViewLocation, OperationPath> paths) {
        var tail = locations.get(edge.tail());
        var head = locations.get(edge.head());
        if (tail == null || head == null) return;
        commands.add(new VisualizationCommand.Connect(paths.apply(tail), paths.apply(head), TopologyEdge.Direction.FORWARD,
                edge.tailPort(), edge.headPort(), EdgeStyle.DEFAULT));
    }

    /** 组合子节点先删、容器后删，避免级联释放后的二次删除。 */
    private static List<ViewLocation> deletionOrder(PageModel pageModel, Collection<ViewLocation> nodes) {
        var children = new HashSet<ViewLocation>();
        pageModel.composition().values().forEach(link -> children.add(link.child()));
        var ordered = new ArrayList<ViewLocation>();
        nodes.stream().filter(node -> !children.contains(node)).forEach(ordered::add);
        nodes.stream().filter(children::contains).forEach(ordered::add);
        return ordered;
    }

    private static boolean live(DebugMemoryReader memory, long address) {
        try { memory.resolve(address); return true; }
        catch (DebugMemoryReader.MemoryReadException failure) { return false; }
    }

    private static Long readPointer(DebugMemoryReader memory, long address, int offset) {
        try { return memory.readPointer(memory.resolve(address), offset); }
        catch (DebugMemoryReader.MemoryReadException failure) { return null; }
    }

    private static String hex(long value) { return String.format("0x%x", value); }

    private static Expansion merge(Expansion previous, Expansion child) {
        var values = new ArrayList<>(previous.values());
        values.addAll(child.values());
        var nodes = new ArrayList<>(previous.nodes());
        nodes.addAll(child.nodes());
        var entries = new ArrayList<>(previous.entries());
        entries.addAll(child.entries());
        return new Expansion(previous.anchor(), values, nodes, entries, previous.base());
    }

    /** 数组层：容器节点 + 每槽一个入口（元素是数组/结构体/指向它们的指针）或值槽。 */
    private Expansion buildArrayLayer(DebugVariable variable, PageRef page, ViewLocation parent, CrakenType.ArrayType array,
                                      String path, int baseOffset, long base, Set<Long> ancestors,
                                      int depth, List<VisualizationCommand> commands) {
        if (elementCount(array) > MAX_CELLS) {
            var node = session.reserveNodeId(page);
            commands.add(new VisualizationCommand.AddNode(ownerPath(parent, node), card(variable, baseOffset)));
            if (parent != null) commands.add(new VisualizationCommand.AttachOwnership(parent, node, "debug:slot:" + path));
            return new Expansion(node, List.of(), List.of(node), List.of(), base);
        }
        var container = session.reserveNodeId(page);
        commands.add(new VisualizationCommand.AddNode(ownerPath(parent, container),
                spec(ViewNode.Kind.ARRAY, path, Map.of("type", typeName(array)), ColorSpec.Preset.NEUTRAL)));
        if (parent != null) commands.add(new VisualizationCommand.AttachOwnership(parent, container, "debug:slot:" + path));
        var values = new ArrayList<ValueSlot>();
        var nodes = new ArrayList<ViewLocation>();
        var entries = new ArrayList<Entry>();
        nodes.add(container);
        int stride = typeSize(array.elementType());
        for (int index = 0; index < array.length(); index++) {
            String childPath = path + "[" + index + "]";
            int offset = baseOffset + index * stride;
            var element = array.elementType();
            var slot = session.reserveNodeId(page);
            var unqualified = element.unqualified();
            var pointer = unqualified instanceof CrakenType.PointerType;
            var expandable = unqualified instanceof CrakenType.ArrayType
                    || structLayout(unqualified) != null || pointer;
            commands.add(new VisualizationCommand.AddNode(ownerPath(parent, slot),
                    expandable
                            ? spec(ViewNode.Kind.POINT, childPath,
                                    pointer ? Map.of("type", typeName(element), "value", "未解析")
                                            : Map.of("type", typeName(element)), ColorSpec.Preset.NEUTRAL)
                            : spec(ViewNode.Kind.POINT, "·", Map.of(), ColorSpec.Preset.NEUTRAL)));
            if (parent != null) commands.add(new VisualizationCommand.AttachOwnership(parent, slot,
                    (expandable ? "debug:slot:" : "debug:cell:") + childPath));
            commands.add(new VisualizationCommand.Compose(container, slot, index));
            nodes.add(slot);
            if (expandable) {
                var entry = new Entry(slot, variable, childPath, element, offset, base, pointer, depth, ancestors);
                entries.add(entry);
                this.entries.put(slot, entry);
            }
            if (pointer || !expandable) values.add(new ValueSlot(slot, element, offset, base));
        }
        return new Expansion(container, values, nodes, entries, base);
    }

    /** 结构体层：容器节点 + 按布局声明顺序的成员槽；可展开成员是入口，标量成员是值槽。 */
    private Expansion buildStructLayer(DebugVariable variable, PageRef page, ViewLocation parent, StructLayout layout,
                                       String path, int baseOffset, long base, Set<Long> ancestors,
                                       int depth, List<VisualizationCommand> commands) {
        var container = session.reserveNodeId(page);
        commands.add(new VisualizationCommand.AddNode(ownerPath(parent, container),
                spec(ViewNode.Kind.ARRAY, path, Map.of("type", typeName(variable.declaredType())), ColorSpec.Preset.NEUTRAL)));
        if (parent != null) commands.add(new VisualizationCommand.AttachOwnership(parent, container, "debug:slot:" + path));
        var values = new ArrayList<ValueSlot>();
        var nodes = new ArrayList<ViewLocation>();
        var entries = new ArrayList<Entry>();
        nodes.add(container);
        int slot = 0;
        for (var field : layout.fields()) {
            var fieldType = field.type();
            var unqualified = fieldType.unqualified();
            var member = session.reserveNodeId(page);
            boolean pointer = unqualified instanceof CrakenType.PointerType;
            boolean expandable = unqualified instanceof CrakenType.ArrayType
                    || structLayout(unqualified) != null || pointer;
            var memberPath = path + "." + field.name();
            commands.add(new VisualizationCommand.AddNode(ownerPath(parent, member),
                    spec(ViewNode.Kind.POINT, field.name(),
                            expandable
                                    ? pointer ? Map.of("type", typeName(fieldType), "value", "未解析")
                                            : Map.of("type", typeName(fieldType))
                                    : Map.of("value", "未解析"),
                            ColorSpec.Preset.NEUTRAL)));
            if (parent != null) commands.add(new VisualizationCommand.AttachOwnership(parent, member,
                    (expandable ? "debug:slot:" : "debug:cell:") + memberPath));
            commands.add(new VisualizationCommand.Compose(container, member, slot++));
            nodes.add(member);
            if (expandable) {
                var entry = new Entry(member, variable, memberPath, fieldType, baseOffset + field.offset(),
                        base, pointer, depth, ancestors);
                entries.add(entry);
                this.entries.put(member, entry);
            }
            if (pointer || !expandable)
                values.add(new ValueSlot(member, fieldType, baseOffset + field.offset(), base));
        }
        return new Expansion(container, values, nodes, entries, base);
    }

    private void refreshValues(Expansion expansion, DebugVariable variable, DebugMemoryReader memory,
                               List<VisualizationCommand> commands) {
        for (var slot : expansion.values()) {
            var node = session.model().node(slot.node());
            if (node.content().kind() != ViewNode.Kind.POINT) continue;
            long base = slot.base() != 0 ? slot.base() : addresses.getOrDefault(variable.definition(), 0L);
            String text = base == 0 ? "未解析" : valueText(slot.type(), base, slot.offset(), memory);
            if (node.content().fields().containsKey("value")) {
                // 结构体成员：名字留在标题，值写入 value 行。
                var fields = new LinkedHashMap<>(node.content().fields());
                fields.put("value", text);
                if (fields.containsKey("address"))
                    fields.put("address", base == 0 ? "未解析" : String.format("0x%x", base + slot.offset()));
                if (!fields.equals(node.content().fields()))
                    commands.add(new VisualizationCommand.SetContent(path(slot.node()),
                            spec(ViewNode.Kind.POINT, node.content().label(), fields, ColorSpec.Preset.NEUTRAL)));
            } else if (!text.equals(node.content().label()))
                commands.add(new VisualizationCommand.SetContent(path(slot.node()),
                        spec(ViewNode.Kind.POINT, text, node.content().fields(), ColorSpec.Preset.NEUTRAL)));
        }
    }

    private String valueText(CrakenType type, long base, int offset, DebugMemoryReader memory) {
        var unqualified = type.unqualified();
        if (unqualified instanceof CrakenType.StructType || unqualified instanceof CrakenType.ArrayType) return "<聚合>";
        try {
            return memory.readScalar(memory.resolve(base + offset), 0, scalar(type));
        } catch (DebugMemoryReader.MemoryReadException failure) {
            return failure.reason() == DebugMemoryReader.Reason.UNINITIALIZED ? "<未初始化>" : "<不可读>";
        }
    }

    private ViewNode.Spec card(DebugVariable variable, int offset) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("type", typeName(variable.declaredType()));
        fields.put("value", "·");
        fields.put("address", "未解析");
        return spec(ViewNode.Kind.POINT, variable.sourceName(), fields, ColorSpec.Preset.NEUTRAL);
    }

    private static ViewNode.Spec spec(ViewNode.Kind kind, String label, Map<String, String> fields, ColorSpec color) {
        return new ViewNode.Spec(kind, label, fields, color);
    }

    private void apply(List<VisualizationCommand> commands, String step) {
        if (commands.isEmpty()) return;
        var result = session.modify(new MutationBatch(commands, step));
        if (!result.succeeded()) throw new IllegalArgumentException(result.error().code() + ": " + result.error().message());
        published = session.snapshot();
    }

    private static OperationPath ownerPath(ViewLocation parent, ViewLocation node) {
        return new OperationPath(parent, node);
    }

    /** OWNED 节点必须带有效上游路径；ROOT 节点用 null。 */
    private OperationPath path(ViewLocation location) {
        return new OperationPath(session.model().node(location).parents().selected(), location);
    }

    private void rebuildIndexes() {
        entries.clear();
        expansions.values().forEach(expansion -> expansion.entries().forEach(entry -> entries.put(entry.node(), entry)));
        entries.keySet().removeIf(graphs::containsKey);
    }

    private StructLayout structLayout(CrakenType type) {
        return type.unqualified() instanceof CrakenType.StructType struct
                ? ir.structLayouts().get(struct.name()) : null;
    }

    private static AccessKind access(DebugCapture.Kind kind) {
        return switch (kind) {
            case CREATE -> AccessKind.ALLOCATE;
            case READ -> AccessKind.READ;
            case WRITE -> AccessKind.WRITE;
            case REMOVE -> AccessKind.DELETE;
        };
    }

    private String fieldTypeName(Entry entry) { return typeName(entry.type()); }

    private int typeSize(CrakenType type) {
        var unqualified = type.unqualified();
        if (unqualified instanceof CrakenType.PointerType) return Long.BYTES;
        if (unqualified instanceof CrakenType.ArrayType array) return Math.multiplyExact(array.length(), typeSize(array.elementType()));
        var layout = structLayout(unqualified);
        if (layout != null) return layout.size();
        if (unqualified instanceof CrakenType.ScalarType scalar) return scalar.kind().sizeBytes();
        return Long.BYTES;
    }

    /**
     * 指针目标的格数：目标地址必须落在运行期 IR 分配事件记录过的块内，块内从目标开始的剩余
     * 字节除以单格大小就是格数。没有分配记录（悬垂、外部地址、未插桩 alloc）时返回 0，不猜测。
     */
    private int extentCount(long target, CrakenType elementType) {
        int elementSize = typeSize(elementType);
        if (elementSize <= 0) return 0;
        for (var range : recordedAllocations.values()) {
            if (target < range.address()) continue;
            long offset = target - range.address();
            if (offset >= range.size()) continue;
            return (int) ((range.size() - offset) / elementSize);
        }
        return 0;
    }

    private static int elementCount(CrakenType.ArrayType array) {
        int total = 1;
        CrakenType current = array;
        while (current.unqualified() instanceof CrakenType.ArrayType inner) {
            total = Math.multiplyExact(total, inner.length());
            current = inner.elementType();
        }
        return total;
    }

    private String typeName(CrakenType type) {
        var unqualified = type.unqualified();
        if (unqualified instanceof CrakenType.PointerType pointer) return typeName(pointer.pointee()) + "*";
        if (unqualified instanceof CrakenType.ArrayType array) return typeName(array.elementType()) + "[" + array.length() + "]";
        if (unqualified instanceof CrakenType.StructType struct) {
            String name = ir.displayName(struct.name());
            return name.contains("::") ? name : "struct " + name;
        }
        return unqualified.toString();
    }

    private static DebugMemoryReader.ScalarType scalar(CrakenType type) {
        if (type.isPointer()) return DebugMemoryReader.ScalarType.POINTER;
        if (type.unqualified() instanceof CrakenType.ScalarType scalar) {
            return switch (scalar.kind().sizeBytes()) {
                case 1 -> DebugMemoryReader.ScalarType.SIGNED8;
                case 2 -> DebugMemoryReader.ScalarType.SIGNED16;
                case 4 -> DebugMemoryReader.ScalarType.SIGNED32;
                default -> DebugMemoryReader.ScalarType.SIGNED64;
            };
        }
        return DebugMemoryReader.ScalarType.SIGNED64;
    }

    @Override public void close() {
        if (ownsSession) session.close();
    }
}
