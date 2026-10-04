package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.*;
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
    private final Set<PageRef> affected = new LinkedHashSet<>();
    private final Set<ViewLocation> explicitDeletes = new LinkedHashSet<>();
    private int commandIndex;

    public MutationTransaction(ContainerModel base, Set<ViewLocation> reservations, MonotonicIds relations) {
        this.base = base;
        this.pages = new LinkedHashMap<>(base.pages());
        this.ownership = new OwnershipStore(base.ownership(), relations);
        this.relationIds = relations;
        this.reservations = Set.copyOf(reservations);
    }
    public void apply(MutationBatch batch) {
        for (commandIndex = 0; commandIndex < batch.commands().size(); commandIndex++) {
            switch (batch.commands().get(commandIndex)) {
                case AddNode add -> add(add);
                case AttachOwnership attach -> attach(attach.pre(), attach.nxt(), attach.source());
                case Compose compose -> compose(compose);
                case Connect connect -> connect(connect);
                case Disconnect disconnect -> disconnect(disconnect);
                case DeleteNode delete -> explicitDeletes.add(select(delete.path()).location());
                case DetachOwnership detach -> detach(detach);
            }
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
            return;
        }
        if (!reservations.contains(path.nxt()))
            throw CommandValidator.failure(UNRESERVED_POSITION, "Position was not reserved or has already been consumed");
        if (!page.type().nodeKinds().contains(add.spec().kind()))
            throw CommandValidator.failure(PAGE_TYPE_MISMATCH, "Unsupported node kind");
        ViewNode.Retention retention = path.pre() == null ? ViewNode.Retention.ROOT : ViewNode.Retention.OWNED;
        if (path.pre() == null && !page.ref().equals(base.root()))
            throw CommandValidator.failure(INVALID_OWNERSHIP, "Only root page permits ROOT nodes");
        var selection = path.pre() == null ? ParentSelection.ROOT : ParentSelection.ROOT.add(path.pre());
        ViewNode node = page.type().create(path.nxt(), add.spec(), retention, selection);
        if (node == null || !node.location().equals(path.nxt()) || node.retention() != retention
                || !node.parents().equals(selection) || !node.content().equals(add.spec()))
            throw CommandValidator.failure(PAGE_TYPE_MISMATCH, "Page type factory violated the node contract");
        pages.put(page.ref().pageId(), page.withAllocatedNode(node));
        affected.add(page.ref());
        created.add(node.location());
        if (path.pre() != null) attach(path.pre(), path.nxt(), "explicit");
    }
    private void attach(ViewLocation pre, ViewLocation nxt, String source) {
        requireNotDeleted(pre);
        requireNotDeleted(nxt);
        ViewNode parent = node(pre), child = node(nxt);
        CommandValidator.ownership(parent, child);
        ownership.add(pre, nxt, source);
        put(child.withState(child.content(), child.parents().add(pre)));
        affected.add(pre.page());
    }
    private void requireNotDeleted(ViewLocation location) {
        if (explicitDeletes.contains(location))
            throw CommandValidator.failure(INVALID_COMMAND, "Cannot reuse an explicitly deleted node");
    }
    private void detach(DetachOwnership detach) {
        select(new OperationPath(detach.pre(), detach.nxt()));
        ownership.removeSource(detach.pre(), detach.nxt(), detach.source());
        affected.add(detach.pre().page());
    }
    private void compose(Compose compose) {
        ViewNode parent = node(compose.parent()), child = node(compose.child());
        PageModel page = pages.get(parent.location().pageId());
        pages.put(page.ref().pageId(), CompositionStore.compose(page, parent, child, compose.slot()));
        affected.add(page.ref());
    }
    private ViewNode select(OperationPath path) {
        ViewNode node = CommandValidator.path(base.id(), pages, path);
        put(node);
        return node;
    }
    private void connect(Connect connect) {
        ViewNode a = select(connect.a()), b = select(connect.b());
        var page = pages.get(a.location().pageId());
        pages.put(page.ref().pageId(), TopologyStore.connect(page, a.location(), b.location(), connect.direction(), relationIds));
    }
    private void disconnect(Disconnect disconnect) {
        ViewNode a = select(disconnect.a()), b = select(disconnect.b());
        var page = pages.get(a.location().pageId());
        pages.put(page.ref().pageId(), TopologyStore.disconnect(page, a.location(), b.location()));
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
        for (PageRef ref : affected) if (pages.containsKey(ref.pageId()))
            pages.put(ref.pageId(), PartPlanner.plan(pages.get(ref.pageId())));
        return new ContainerModel(base.id(), base.root(), pages, base.version() + 1, ownership.bindings());
    }
    private void release(Set<ViewLocation> deleted) {
        deleted.forEach(ownership::removeNode);
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
                    node = node.withState(node.content(), selection).withChildren(children);
                    changed = true;
                }
                kept.put(node.location().nodeId(), node);
            }
            ViewLocation anchor = deleted.contains(page.anchor()) ? null : page.anchor();
            if (!page.ref().equals(base.root()) && kept.isEmpty() && anchor == null) {
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
                        composition, topology, ready, Map.of(), Map.of()));
                affected.add(page.ref());
            }
        }
    }
    public int commandIndex() { return commandIndex; }
    public List<ViewLocation> created() { return List.copyOf(created); }
    public Set<PageRef> affected() { return Set.copyOf(affected); }
}
