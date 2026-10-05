package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.CompositionLink;
import craken.visualization.model.relation.PageBindingRule;
import craken.visualization.model.relation.TopologyEdge;
import craken.visualization.navigation.FocusController;
import java.util.*;
import java.util.function.Function;
import static craken.visualization.api.VisualizationError.Code.*;

/**
 * Copy-on-write draft at batch granularity: touched pages are copied once into mutable drafts,
 * commands mutate them in place, and finish() materializes immutable pages exactly once.
 * Only a fully validated transaction can be published.
 */
public final class MutationTransaction {
    private final ContainerModel base;
    private final Map<Long, PageDraft> drafts = new LinkedHashMap<>();
    private final OwnershipStore ownership;
    private final MonotonicIds relationIds;
    private final Set<ViewLocation> reservations;
    private final List<ViewLocation> created = new ArrayList<>();
    private final List<MutationResult.ReadValue> reads = new ArrayList<>();
    private final Set<PageRef> affected = new LinkedHashSet<>();
    private final Set<ViewLocation> explicitDeletes = new LinkedHashSet<>();
    private final Map<Long, PageBindingRule> rules;
    private final List<Long> createdRules = new ArrayList<>();
    private final FocusController focus;
    private int commandIndex;
    private int commandCount;
    private String sourceStep;

    public MutationTransaction(ContainerModel base, Set<ViewLocation> reservations, MonotonicIds relations) {
        this.base = base;
        base.pages().forEach((id, page) -> drafts.put(id, new PageDraft(page)));
        this.ownership = new OwnershipStore(base.ownership(), relations);
        this.relationIds = relations;
        this.reservations = Set.copyOf(reservations);
        this.rules = new LinkedHashMap<>(base.pageRules());
        this.focus = new FocusController(base.interaction());
        this.focus.remember(this::liveNode);
    }

    public void apply(MutationBatch batch) {
        sourceStep = batch.sourceStep();
        commandCount = batch.commands().size();
        for (commandIndex = 0; commandIndex < batch.commands().size(); commandIndex++) {
            switch (batch.commands().get(commandIndex)) {
                case SetPageLayout layout -> {
                    var page = draft(layout.page());
                    page.layoutHints = layout.hints();
                    affected.add(page.ref);
                }
                case AddNode add -> add(add);
                case AttachOwnership attach -> { validateExplicitSource(attach.source()); attach(attach.pre(), attach.nxt(), attach.source()); focus.access(attach.nxt(), AccessKind.WRITE); }
                case Compose compose -> compose(compose);
                case Connect connect -> connect(connect);
                case Disconnect disconnect -> disconnect(disconnect);
                case DeleteNode delete -> { explicitDeletes.add(select(delete.path()).location()); focus.access(delete.path().nxt(), AccessKind.DELETE); }
                case DetachOwnership detach -> detach(detach);
                case BindPage bind -> bind(bind);
                case UnbindPage unbind -> unbind(unbind.ruleId());
                case SetContent content -> setContent(content);
                case Touch touch -> {
                    var node = select(touch.path()); focus.access(touch.path().nxt(), touch.kind());
                    reads.add(new MutationResult.ReadValue(commandIndex, touch.path(), touch.kind(), node.content()));
                }
                case SetFocus selected -> { select(selected.path()); focus.focus(selected.path().nxt()); }
                case ClearFocus ignored -> focus.clear();
                case Configure configure -> focus.configure(configure.options());
            }
            focus.remember(this::liveNode);
        }
    }

    private void add(AddNode add) {
        OperationPath path = add.path();
        requireNotDeleted(path.nxt());
        if (path.pre() != null) requireNotDeleted(path.pre());
        PageDraft page = draft(path.nxt().page());
        ViewNode existing = page.nodes.get(path.nxt().nodeId());
        if (existing != null) {
            if (path.pre() != null) attach(path.pre(), path.nxt(), "explicit");
            else if (existing.retention() != ViewNode.Retention.ROOT)
                throw CommandValidator.failure(INVALID_OWNERSHIP, "OWNED node requires an upstream path");
            focus.access(path.nxt(), AccessKind.ALLOCATE);
            return;
        }
        if (!reservations.contains(path.nxt()))
            throw CommandValidator.failure(UNRESERVED_POSITION, "Position was not reserved or has already been consumed");
        if (!page.type.nodeKinds().contains(add.spec().kind()))
            throw CommandValidator.failure(PAGE_TYPE_MISMATCH, "Unsupported node kind");
        if (add.spec().color() != null) craken.visualization.style.ColorValidator.validate(add.spec().color());
        ViewNode.Retention retention = path.pre() == null ? ViewNode.Retention.ROOT : ViewNode.Retention.OWNED;
        if (path.pre() == null && !page.ref.equals(base.root()))
            throw CommandValidator.failure(INVALID_OWNERSHIP, "Only root page permits ROOT nodes");
        var selection = path.pre() == null ? ParentSelection.ROOT : ParentSelection.ROOT.add(path.pre());
        ViewNode node = ViewNodeContract.require(page.type.create(path.nxt(), add.spec(), retention, selection),
                path.nxt(), add.spec(), retention, selection, List.of());
        if (page.type.readyEnabled() && !page.nodes.isEmpty()) page.ready.add(node.location().nodeId());
        page.nodes.put(node.location().nodeId(), node);
        affected.add(page.ref);
        created.add(node.location());
        if (path.pre() != null) attach(path.pre(), path.nxt(), "explicit");
        for (var rule : rules.values()) expand(rule, node.location());
        focus.access(path.nxt(), AccessKind.ALLOCATE);
    }

    private void setContent(SetContent content) {
        var node = select(content.path());
        if (node.content().kind() != content.spec().kind()) throw CommandValidator.failure(PAGE_TYPE_MISMATCH, "A node kind cannot change");
        if (content.spec().color() != null) craken.visualization.style.ColorValidator.validate(content.spec().color());
        var updated = ViewNodeContract.withState(node, content.spec(), node.parents());
        put(updated); focus.access(node.location(), AccessKind.WRITE);
    }

    private void bind(BindPage bind) {
        draft(bind.child());
        draft(bind.binding().parentPage());
        if (bind.child().equals(bind.binding().parentPage()))
            throw CommandValidator.failure(INVALID_OWNERSHIP, "Rule must cross pages");
        if (bind.binding().parentNode() != null) node(bind.binding().parentNode());
        var rule = new PageBindingRule(relationIds.next(), bind.child(), bind.binding());
        rules.put(rule.id(), rule); createdRules.add(rule.id()); affected.add(rule.child()); affected.add(rule.spec().parentPage());
        expand(rule, null);
    }

    private void expand(PageBindingRule rule, ViewLocation added) {
        PageBindingRuleExpander.expand(rule, this::view, added, explicitDeletes, (pre, nxt) -> {
            ViewLocation selected = node(nxt).parents().selected();
            attach(pre, nxt, rule.source());
            if (selected != null && ownership.parents(nxt).contains(selected)) {
                var n = node(nxt); put(ViewNodeContract.withState(n, n.content(), n.parents().select(selected)));
            }
        });
    }

    private void unbind(long id) {
        var rule = rules.remove(id);
        if (rule == null) throw CommandValidator.failure(INVALID_COMMAND, "Unknown page binding rule");
        for (var binding : ownership.bindings().values()) if (binding.sources().contains(rule.source())) {
            affected.add(binding.key().pre().page()); affected.add(binding.key().nxt().page());
            ownership.removeSource(binding.key().pre(), binding.key().nxt(), rule.source());
        }
        affected.add(rule.child()); affected.add(rule.spec().parentPage());
    }

    private static void validateExplicitSource(String source) {
        if (source.isBlank() || source.startsWith("rule:")) throw CommandValidator.failure(INVALID_COMMAND, "Reserved or empty ownership source");
    }

    private void attach(ViewLocation pre, ViewLocation nxt, String source) {
        requireNotDeleted(pre);
        requireNotDeleted(nxt);
        ViewNode parent = node(pre), child = node(nxt);
        CommandValidator.ownership(parent, child);
        var selection = child.parents();
        for (var old : selection.parents()) if (!ownership.parents(nxt).contains(old)) selection = selection.remove(old);
        ownership.add(pre, nxt, source);
        put(ViewNodeContract.withState(child, child.content(), selection.add(pre)));
        affected.add(pre.page());
    }

    private void requireNotDeleted(ViewLocation location) {
        if (explicitDeletes.contains(location))
            throw CommandValidator.failure(INVALID_COMMAND, "Cannot reuse an explicitly deleted node");
    }

    private void detach(DetachOwnership detach) {
        validateExplicitSource(detach.source());
        requireNotDeleted(detach.pre()); requireNotDeleted(detach.nxt());
        CommandValidator.ownership(node(detach.pre()), node(detach.nxt()));
        if (!ownership.parents(detach.nxt()).contains(detach.pre()))
            throw CommandValidator.failure(INVALID_OWNERSHIP, "Detach requires a currently effective upstream");
        ownership.removeSource(detach.pre(), detach.nxt(), detach.source());
        affected.add(detach.pre().page()); affected.add(detach.nxt().page());
        focus.access(detach.nxt(), AccessKind.WRITE);
    }

    private void compose(Compose compose) {
        requireNotDeleted(compose.parent()); requireNotDeleted(compose.child());
        ViewNode parent = node(compose.parent()), child = node(compose.child());
        if (!parent.location().page().equals(child.location().page()))
            throw CommandValidator.failure(COMPOSITION_CONFLICT, "Composition must stay in one page");
        PageDraft page = draft(parent.location().page());
        if (!explicitDeletes.isEmpty()) {
            // Delete remains deferred for lifetime planning, but its slot is available to a later command.
            page.composition.values().removeIf(link -> link.parent().equals(parent.location())
                    && link.slot() == compose.slot() && explicitDeletes.contains(link.child()));
            page.rebuildSlotOwners();
        }
        var previous = page.composition.get(child.location().nodeId());
        if (previous != null && (!previous.parent().equals(parent.location()) || previous.slot() != compose.slot()))
            throw CommandValidator.failure(COMPOSITION_CONFLICT, "Child already has another parent or slot");
        var slots = page.slotOwners.computeIfAbsent(parent.location().nodeId(), unused -> new HashMap<>());
        ViewLocation occupying = slots.get(compose.slot());
        if (occupying != null && !occupying.equals(child.location()))
            throw CommandValidator.failure(COMPOSITION_CONFLICT, "Slot is already occupied");
        int increment = page.type.nestingPolicy().increment(parent, child);
        if (increment < 0 || increment > 1)
            throw CommandValidator.failure(NESTING_LIMIT, "Nesting policy must return 0 or 1");
        page.composition.put(child.location().nodeId(), new CompositionLink(parent.location(), child.location(), compose.slot(), increment));
        slots.put(compose.slot(), child.location());
        affected.add(page.ref);
    }

    private ViewNode select(OperationPath path) {
        requireNotDeleted(path.nxt());
        if (path.pre() != null) {
            requireNotDeleted(path.pre());
            if (!ownership.parents(path.nxt()).contains(path.pre()))
                throw CommandValidator.failure(INVALID_OWNERSHIP, "Operation requires a currently effective upstream");
        }
        ViewNode node = node(path.nxt());
        if (node.retention() == ViewNode.Retention.ROOT) {
            if (path.pre() != null) throw CommandValidator.failure(INVALID_OWNERSHIP, "ROOT node requires a null upstream");
        } else {
            if (path.pre() == null || !node.parents().parents().contains(path.pre()))
                throw CommandValidator.failure(INVALID_OWNERSHIP, "Operation requires an effective upstream");
            node(path.pre());
            node = ViewNodeContract.withState(node, node.content(), node.parents().select(path.pre()));
        }
        put(node);
        focus.access(node.location(), AccessKind.WRITE);
        return node;
    }

    private void connect(Connect connect) {
        ViewNode a = select(connect.a()), b = select(connect.b());
        if (!a.location().page().equals(b.location().page()))
            throw CommandValidator.failure(INVALID_COMMAND, "Topology must stay in one page");
        TopologyStore.validatePort(a, connect.aPort()); TopologyStore.validatePort(b, connect.bPort());
        ViewLocation va = a.location(), vb = b.location();
        String pa = connect.aPort(), pb = connect.bPort();
        var direction = connect.direction();
        if (va.nodeId() > vb.nodeId() || va.equals(vb) && pa.compareTo(pb) > 0) {
            var swap = va; va = vb; vb = swap;
            var port = pa; pa = pb; pb = port;
            direction = direction.reversed();
        }
        PageDraft page = draft(a.location().page());
        var key = new EdgeKey(va, pa, vb, pb);
        long id = page.edgeIds.getOrDefault(key, 0L);
        if (id == 0) id = relationIds.next();
        page.topology.put(id, new TopologyEdge(id, va, vb, direction, pa, pb, connect.style()));
        page.edgeIds.put(key, id);
        page.ready.remove(va.nodeId()); page.ready.remove(vb.nodeId());
    }

    private void disconnect(Disconnect disconnect) {
        ViewNode a = select(disconnect.a()), b = select(disconnect.b());
        if (!a.location().page().equals(b.location().page()))
            throw CommandValidator.failure(INVALID_COMMAND, "Topology must stay in one page");
        TopologyStore.validatePort(a, disconnect.aPort()); TopologyStore.validatePort(b, disconnect.bPort());
        PageDraft page = draft(a.location().page());
        var key = canonical(a.location(), disconnect.aPort(), b.location(), disconnect.bPort());
        Long id = page.edgeIds.remove(key);
        if (id != null) page.topology.remove(id);
    }

    private ViewNode node(ViewLocation location) {
        PageDraft page = drafts.get(location.pageId());
        ViewNode node = page == null ? null : page.nodes.get(location.nodeId());
        if (node == null) throw CommandValidator.failure(UNKNOWN_POSITION, "Unknown node: " + location);
        return node;
    }

    private ViewNode liveNode(ViewLocation location) {
        PageDraft page = drafts.get(location.pageId());
        return page == null ? null : page.nodes.get(location.nodeId());
    }

    private PageDraft draft(PageRef ref) {
        PageDraft page = drafts.get(ref.pageId());
        if (ref.containerId() != base.id() || page == null)
            throw CommandValidator.failure(UNKNOWN_POSITION, "Unknown page: " + ref);
        return page;
    }

    private void put(ViewNode node) {
        PageDraft page = drafts.get(node.location().pageId());
        if (page == null) throw CommandValidator.failure(UNKNOWN_POSITION, "Unknown page: " + node.location().page());
        page.nodes.put(node.location().nodeId(), node);
        affected.add(page.ref);
    }

    /** Materialize one page on demand; used by rare readers such as rule expansion. */
    private PageModel view(long pageId) { return materialize(pageId); }

    public ContainerModel finish() {
        Map<Long, PageModel> pages = materializeAll();
        release(pages, LifetimePlanner.plan(pages, ownership, explicitDeletes));
        OwnershipDagValidator.validate(pages, ownership);
        for (PageRef ref : affected) if (pages.containsKey(ref.pageId())) {
            var page = CompositionStore.refreshSemantics(pages.get(ref.pageId()));
            page.layoutHints().validate(page.ref(), page.nodes().keySet());
            TopologyStore.validate(page);
            for (var node : page.nodes().values()) for (var highlight : node.highlights())
                if (!highlight.page().equals(page.ref()) || !page.nodes().containsKey(highlight.nodeId()))
                    throw CommandValidator.failure(PAGE_TYPE_MISMATCH, "Node highlight must refer to a live node in its page");
            pages.put(ref.pageId(), PartPlanner.plan(page));
        }
        Function<ViewLocation, ViewNode> live = location -> {
            PageModel page = pages.get(location.pageId());
            return page == null ? null : page.nodes().get(location.nodeId());
        };
        return new ContainerModel(base.id(), base.root(), pages, base.version() + 1, ownership.bindings(),
                rules, focus.finish(live), base.epoch(), sourceStep);
    }

    private Map<Long, PageModel> materializeAll() {
        var pages = new LinkedHashMap<Long, PageModel>();
        for (long pageId : drafts.keySet()) {
            PageModel page = materialize(pageId);
            if (page != null) pages.put(pageId, page);
        }
        return pages;
    }

    private PageModel materialize(long pageId) {
        PageDraft page = drafts.get(pageId);
        if (page == null) return null;
        var byParent = new HashMap<Long, List<CompositionLink>>();
        for (var link : page.composition.values()) byParent.computeIfAbsent(link.parent().nodeId(), unused -> new ArrayList<>()).add(link);
        for (var entry : byParent.entrySet()) {
            ViewNode node = page.nodes.get(entry.getKey());
            if (node == null) continue;
            var children = entry.getValue().stream().sorted(Comparator.comparingInt(CompositionLink::slot))
                    .map(CompositionLink::child).toList();
            if (!node.children().equals(children)) page.nodes.put(entry.getKey(), ViewNodeContract.withChildren(node, children));
        }
        return new PageModel(page.ref, page.type, page.nodes, page.anchor, page.composition,
                page.topology, page.ready, Map.of(), Map.of(), page.layoutHints);
    }

    private void release(Map<Long, PageModel> pages, Set<ViewLocation> deleted) {
        for (var binding : ownership.bindings().values())
            if (deleted.contains(binding.key().pre()) || deleted.contains(binding.key().nxt())) {
                affected.add(binding.key().pre().page()); affected.add(binding.key().nxt().page());
            }
        for (var rule : rules.values()) if (deleted.contains(rule.spec().parentNode())) {
            affected.add(rule.spec().parentPage()); affected.add(rule.child());
        }
        deleted.forEach(ownership::removeNode);
        rules.values().removeIf(rule -> deleted.contains(rule.spec().parentNode()));
        for (PageModel page : new ArrayList<>(pages.values())) {
            var kept = new LinkedHashMap<Long, ViewNode>();
            boolean changed = false;
            for (ViewNode node : page.nodes().values()) {
                if (deleted.contains(node.location())) { changed = true; continue; }
                ParentSelection selection = node.parents();
                for (var old : node.parents().parents())
                    if (!ownership.parents(node.location()).contains(old)) selection = selection.remove(old);
                var children = node.children().stream().filter(child -> !deleted.contains(child)).toList();
                if (!selection.equals(node.parents()) || !children.equals(node.children())) {
                    node = ViewNodeContract.withChildren(ViewNodeContract.withState(node, node.content(), selection), children);
                    changed = true;
                }
                kept.put(node.location().nodeId(), node);
            }
            var retainingRules = rules.values().stream().filter(rule -> rule.child().equals(page.ref())).toList();
            ViewLocation anchor = retainingRules.stream().map(rule -> rule.spec().parentNode()).filter(Objects::nonNull).findFirst().orElse(null);
            if (!page.ref().equals(base.root()) && kept.isEmpty() && retainingRules.isEmpty()) {
                pages.remove(page.ref().pageId());
                affected.add(page.ref());
                continue;
            }
            if (changed || !Objects.equals(anchor, page.anchor())) {
                var composition = new LinkedHashMap<>(page.composition());
                composition.values().removeIf(link -> deleted.contains(link.parent()) || deleted.contains(link.child()));
                var topology = new LinkedHashMap<>(page.topology());
                topology.values().removeIf(edge -> deleted.contains(edge.a()) || deleted.contains(edge.b()));
                var ready = new HashSet<>(page.ready());
                ready.retainAll(kept.keySet());
                pages.put(page.ref().pageId(), new PageModel(page.ref(), page.type(), kept, anchor,
                        composition, topology, ready, Map.of(), Map.of(), page.layoutHints().without(deleted)));
                affected.add(page.ref());
            }
        }
        boolean pruned;
        do {
            pruned = rules.values().removeIf(rule -> !pages.containsKey(rule.child().pageId()) || !pages.containsKey(rule.spec().parentPage().pageId()));
            var retained = new HashSet<PageRef>(); rules.values().forEach(rule -> retained.add(rule.child()));
            for (var page : new ArrayList<>(pages.values())) if (!page.ref().equals(base.root()) && page.nodes().isEmpty() && !retained.contains(page.ref())) {
                pages.remove(page.ref().pageId()); affected.add(page.ref()); pruned = true;
            }
        } while (pruned);
    }

    public int commandIndex() { return Math.min(commandIndex, Math.max(0, commandCount - 1)); }
    public List<ViewLocation> created() { return List.copyOf(created); }
    public List<MutationResult.ReadValue> reads() { return List.copyOf(reads); }
    public List<Long> createdRules() { return List.copyOf(createdRules); }
    public Set<PageRef> affected() { return Set.copyOf(affected); }

    private record EdgeKey(ViewLocation a, String aPort, ViewLocation b, String bPort) {
        private static EdgeKey canonical(ViewLocation a, String aPort, ViewLocation b, String bPort) {
            if (a.nodeId() > b.nodeId() || a.equals(b) && aPort.compareTo(bPort) > 0) return new EdgeKey(b, bPort, a, aPort);
            return new EdgeKey(a, aPort, b, bPort);
        }
    }

    private static EdgeKey canonical(ViewLocation a, String aPort, ViewLocation b, String bPort) {
        return EdgeKey.canonical(a, aPort, b, bPort);
    }

    /** Mutable per-page draft; commands update it in place and indexes keep slot/edge lookups constant. */
    private static final class PageDraft {
        final PageRef ref;
        final PageType type;
        final Map<Long, ViewNode> nodes = new LinkedHashMap<>();
        ViewLocation anchor;
        final Map<Long, CompositionLink> composition = new LinkedHashMap<>();
        final Map<Long, TopologyEdge> topology = new LinkedHashMap<>();
        final Set<Long> ready = new HashSet<>();
        PageLayoutHints layoutHints;
        final Map<Long, Map<Integer, ViewLocation>> slotOwners = new HashMap<>();
        final Map<EdgeKey, Long> edgeIds = new HashMap<>();

        PageDraft(PageModel page) {
            ref = page.ref(); type = page.type();
            nodes.putAll(page.nodes());
            anchor = page.anchor();
            composition.putAll(page.composition());
            topology.putAll(page.topology());
            ready.addAll(page.ready());
            layoutHints = page.layoutHints();
            rebuildSlotOwners();
            for (var edge : topology.values()) edgeIds.put(EdgeKey.canonical(edge.a(), edge.aPort(), edge.b(), edge.bPort()), edge.id());
        }

        void rebuildSlotOwners() {
            slotOwners.clear();
            for (var link : composition.values()) slotOwners.computeIfAbsent(link.parent().nodeId(), unused -> new HashMap<>()).put(link.slot(), link.child());
        }
    }
}
