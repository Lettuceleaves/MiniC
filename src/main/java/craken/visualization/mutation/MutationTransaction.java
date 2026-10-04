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
    private final Set<ViewLocation> reservations;
    private final List<ViewLocation> created = new ArrayList<>();
    private final Set<PageRef> affected = new LinkedHashSet<>();
    private int commandIndex;

    public MutationTransaction(ContainerModel base, Set<ViewLocation> reservations, MonotonicIds relations) {
        this.base = base;
        this.pages = new LinkedHashMap<>(base.pages());
        this.ownership = new OwnershipStore(base.ownership(), relations);
        this.reservations = Set.copyOf(reservations);
    }
    public void apply(MutationBatch batch) {
        for (commandIndex = 0; commandIndex < batch.commands().size(); commandIndex++) {
            switch (batch.commands().get(commandIndex)) {
                case AddNode add -> add(add);
                case AttachOwnership attach -> attach(attach.pre(), attach.nxt(), attach.source());
            }
        }
    }
    private void add(AddNode add) {
        OperationPath path = add.path();
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
        put(node);
        created.add(node.location());
        if (path.pre() != null) attach(path.pre(), path.nxt(), "explicit");
    }
    private void attach(ViewLocation pre, ViewLocation nxt, String source) {
        ViewNode parent = node(pre), child = node(nxt);
        CommandValidator.ownership(parent, child);
        ownership.add(pre, nxt, source);
        put(child.withState(child.content(), child.parents().add(pre)));
        affected.add(pre.page());
    }
    private ViewNode node(ViewLocation location) { return CommandValidator.node(base.id(), pages, location); }
    private void put(ViewNode node) {
        PageModel page = pages.get(node.location().pageId());
        pages.put(page.ref().pageId(), page.withNode(node));
        affected.add(page.ref());
    }
    public ContainerModel finish() {
        OwnershipDagValidator.validate(pages, ownership);
        return new ContainerModel(base.id(), base.root(), pages, base.version() + 1, ownership.bindings());
    }
    public int commandIndex() { return commandIndex; }
    public List<ViewLocation> created() { return List.copyOf(created); }
    public Set<PageRef> affected() { return Set.copyOf(affected); }
}
