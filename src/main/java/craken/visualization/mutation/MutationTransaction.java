package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.PageBindingRule;
import craken.visualization.navigation.FocusController;
import java.util.*;
import static craken.visualization.api.VisualizationError.Code.*;

/** Copy-on-write draft; only finish() produces a state that can be published. */
public final class MutationTransaction {
    private final ContainerModel base;
    private final Map<Long, PageModel> pages;
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
        this.pages = new LinkedHashMap<>(base.pages());
        this.ownership = new OwnershipStore(base.ownership(), relations);
        this.relationIds = relations;
        this.reservations = Set.copyOf(reservations);
        this.rules = new LinkedHashMap<>(base.pageRules());
        this.focus = new FocusController(base.interaction());
        this.focus.remember(pages);
    }
    public void apply(MutationBatch batch) {
        sourceStep=batch.sourceStep();
        commandCount=batch.commands().size();
        for (commandIndex = 0; commandIndex < batch.commands().size(); commandIndex++) {
            switch (batch.commands().get(commandIndex)) {
                case SetPageLayout layout -> {
                    var page = CommandValidator.page(base.id(), pages, layout.page());
                    pages.put(page.ref().pageId(), page.withLayoutHints(layout.hints())); affected.add(page.ref());
                }
                case AddNode add -> add(add);
                case AttachOwnership attach -> { validateExplicitSource(attach.source()); attach(attach.pre(), attach.nxt(), attach.source()); focus.access(attach.nxt(),AccessKind.WRITE); }
                case Compose compose -> compose(compose);
                case Connect connect -> connect(connect);
                case Disconnect disconnect -> disconnect(disconnect);
                case DeleteNode delete -> { explicitDeletes.add(select(delete.path()).location()); focus.access(delete.path().nxt(),AccessKind.DELETE); }
                case DetachOwnership detach -> detach(detach);
                case BindPage bind -> bind(bind);
                case UnbindPage unbind -> unbind(unbind.ruleId());
                case SetContent content -> setContent(content);
                case Touch touch -> {
                    var node = select(touch.path()); focus.access(touch.path().nxt(),touch.kind());
                    reads.add(new MutationResult.ReadValue(commandIndex, touch.path(), touch.kind(), node.content()));
                }
                case SetFocus selected -> { select(selected.path()); focus.focus(selected.path().nxt()); }
                case ClearFocus ignored -> focus.clear();
                case Configure configure -> focus.configure(configure.options());
            }
            focus.remember(pages);
        }
    }
    private void add(AddNode add) {
        OperationPath path = add.path();
        requireNotDeleted(path.nxt());
        if (path.pre() != null) requireNotDeleted(path.pre());
        PageModel page = CommandValidator.page(base.id(), pages, path.nxt().page());
        ViewNode existing = page.nodes().get(path.nxt().nodeId());
        if (existing != null) {
            if (path.pre() != null) attach(path.pre(), path.nxt(), "explicit");
            else if (existing.retention() != ViewNode.Retention.ROOT)
                throw CommandValidator.failure(INVALID_OWNERSHIP, "OWNED node requires an upstream path");
            focus.access(path.nxt(),AccessKind.ALLOCATE);
            return;
        }
        if (!reservations.contains(path.nxt()))
            throw CommandValidator.failure(UNRESERVED_POSITION, "Position was not reserved or has already been consumed");
        if (!page.type().nodeKinds().contains(add.spec().kind()))
            throw CommandValidator.failure(PAGE_TYPE_MISMATCH, "Unsupported node kind");
        if (add.spec().color() != null) craken.visualization.style.ColorValidator.validate(add.spec().color());
        ViewNode.Retention retention = path.pre() == null ? ViewNode.Retention.ROOT : ViewNode.Retention.OWNED;
        if (path.pre() == null && !page.ref().equals(base.root()))
            throw CommandValidator.failure(INVALID_OWNERSHIP, "Only root page permits ROOT nodes");
        var selection = path.pre() == null ? ParentSelection.ROOT : ParentSelection.ROOT.add(path.pre());
        ViewNode node = ViewNodeContract.require(page.type().create(path.nxt(), add.spec(), retention, selection),
                path.nxt(), add.spec(), retention, selection, List.of());
        pages.put(page.ref().pageId(), page.withAllocatedNode(node));
        affected.add(page.ref());
        created.add(node.location());
        if (path.pre() != null) attach(path.pre(), path.nxt(), "explicit");
        for (var rule : rules.values()) expand(rule, node.location());
        focus.access(path.nxt(),AccessKind.ALLOCATE);
    }
    private void setContent(SetContent content) {
        var node=select(content.path());
        if (node.content().kind()!=content.spec().kind()) throw CommandValidator.failure(PAGE_TYPE_MISMATCH,"A node kind cannot change");
        if (content.spec().color() != null) craken.visualization.style.ColorValidator.validate(content.spec().color());
        var updated=ViewNodeContract.withState(node,content.spec(),node.parents());
        put(updated); focus.access(node.location(),AccessKind.WRITE);
    }
    private void bind(BindPage bind) {
        PageBindingRuleExpander.validate(base.id(),pages,bind.child(),bind.binding());
        var rule = new PageBindingRule(relationIds.next(),bind.child(),bind.binding());
        rules.put(rule.id(),rule); createdRules.add(rule.id()); affected.add(rule.child());
        expand(rule,null);
    }
    private void expand(PageBindingRule rule, ViewLocation added) {
        PageBindingRuleExpander.expand(rule,pages,added,explicitDeletes,(pre,nxt) -> {
            ViewLocation selected = node(nxt).parents().selected();
            attach(pre,nxt,rule.source());
            if (selected != null && ownership.parents(nxt).contains(selected)) {
                var n = node(nxt); put(ViewNodeContract.withState(n,n.content(),n.parents().select(selected)));
            }
        });
    }
    private void unbind(long id) {
        var rule = rules.remove(id);
        if (rule == null) throw CommandValidator.failure(INVALID_COMMAND,"Unknown page binding rule");
        for (var binding : ownership.bindings().values())
            ownership.removeSource(binding.key().pre(),binding.key().nxt(),rule.source());
        affected.add(rule.child());
    }
    private static void validateExplicitSource(String source) {
        if (source.isBlank() || source.startsWith("rule:")) throw CommandValidator.failure(INVALID_COMMAND,"Reserved or empty ownership source");
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
        // Removing a relationship is not selecting that relationship as an access path.
        // Cleanup advances only when the selected effective parent actually disappears.
        ownership.removeSource(detach.pre(), detach.nxt(), detach.source());
        affected.add(detach.pre().page()); affected.add(detach.nxt().page());
        focus.access(detach.nxt(), AccessKind.WRITE);
    }
    private void compose(Compose compose) {
        requireNotDeleted(compose.parent()); requireNotDeleted(compose.child());
        ViewNode parent = node(compose.parent()), child = node(compose.child());
        PageModel page = pages.get(parent.location().pageId());
        pages.put(page.ref().pageId(), CompositionStore.compose(page, parent, child, compose.slot()));
        affected.add(page.ref());
    }
    private ViewNode select(OperationPath path) {
        requireNotDeleted(path.nxt());
        if (path.pre() != null) {
            requireNotDeleted(path.pre());
            if (!ownership.parents(path.nxt()).contains(path.pre()))
                throw CommandValidator.failure(INVALID_OWNERSHIP, "Operation requires a currently effective upstream");
        }
        ViewNode node = CommandValidator.path(base.id(), pages, path);
        put(node);
        focus.access(node.location(),AccessKind.WRITE);
        return node;
    }
    private void connect(Connect connect) {
        ViewNode a = select(connect.a()), b = select(connect.b());
        var page = pages.get(a.location().pageId());
        pages.put(page.ref().pageId(), TopologyStore.connect(page, a.location(), b.location(), connect.direction(), connect.aPort(), connect.bPort(), connect.style(), relationIds));
    }
    private void disconnect(Disconnect disconnect) {
        ViewNode a = select(disconnect.a()), b = select(disconnect.b());
        var page = pages.get(a.location().pageId());
        pages.put(page.ref().pageId(), TopologyStore.disconnect(page, a.location(), b.location(), disconnect.aPort(), disconnect.bPort()));
    }
    private ViewNode node(ViewLocation location) { return CommandValidator.node(base.id(), pages, location); }
    private void put(ViewNode node) {
        PageModel page = pages.get(node.location().pageId());
        pages.put(page.ref().pageId(), page.withNode(node));
        affected.add(page.ref());
    }
    public ContainerModel finish() {
        release(LifetimePlanner.plan(pages, ownership, explicitDeletes));
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
        return new ContainerModel(base.id(), base.root(), pages, base.version() + 1, ownership.bindings(), rules,focus.finish(pages),base.epoch(),sourceStep);
    }
    private void release(Set<ViewLocation> deleted) {
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
        // Empty page dependencies can form chains. Prune to a fixed point without recursive calls.
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
}
