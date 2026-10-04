package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import java.util.*;

/** Serial model writer; callers and renderers receive immutable committed values. */
public final class DefaultVisualizationSession implements VisualizationSession {
    private static final MonotonicIds CONTAINERS = new MonotonicIds();
    private final MonotonicIds pages = new MonotonicIds();
    private final Map<Long, MonotonicIds> nodes = new HashMap<>();
    private final Set<ViewLocation> reservations = new HashSet<>();
    private final PageTypeRegistry types = new PageTypeRegistry();
    private ContainerModel model = new ContainerModel(CONTAINERS.next(), null, Map.of(), 0);
    private boolean closed;

    @Override public synchronized ContainerModel model() { return model; }
    @Override public synchronized PageRef initializeRoot(PageType type) {
        requireOpen();
        if (model.root() != null) throw new IllegalStateException("Root already initialized");
        return initialize(type, null, true);
    }
    @Override public synchronized PageRef initializePage(PageType type, ViewLocation anchor) {
        requireOpen();
        model.node(Objects.requireNonNull(anchor, "anchor"));
        return initialize(type, anchor, false);
    }
    private PageRef initialize(PageType type, ViewLocation anchor, boolean root) {
        types.register(type);
        var ref = new PageRef(model.id(), pages.next());
        var updated = new LinkedHashMap<>(model.pages());
        updated.put(ref.pageId(), new PageModel(ref, type, Map.of(), anchor));
        nodes.put(ref.pageId(), new MonotonicIds());
        model = new ContainerModel(model.id(), root ? ref : model.root(), updated, model.version() + 1);
        return ref;
    }
    @Override public synchronized ViewLocation reserveNodeId(PageRef page) {
        requireOpen();
        requirePage(page);
        var location = new ViewLocation(page.containerId(), page.pageId(), nodes.get(page.pageId()).next());
        reservations.add(location);
        return location;
    }
    @Override public synchronized ViewNode addNode(OperationPath path, ViewNode.Spec spec) {
        requireOpen();
        PageModel page = requirePage(path.nxt().page());
        if (!reservations.contains(path.nxt())) throw new IllegalArgumentException("Not a reserved position");
        if (!page.type().nodeKinds().contains(spec.kind())) throw new IllegalArgumentException("Unsupported node kind");
        ViewNode.Retention retention = path.pre() == null ? ViewNode.Retention.ROOT : ViewNode.Retention.OWNED;
        if (path.pre() == null && !page.ref().equals(model.root()))
            throw new IllegalArgumentException("Only root page permits ROOT nodes");
        if (path.pre() != null) {
            model.node(path.pre());
            if (path.pre().page().equals(page.ref())) throw new IllegalArgumentException("Ownership must cross pages");
        }
        var parents = path.pre() == null ? ParentSelection.ROOT : ParentSelection.ROOT.add(path.pre());
        ViewNode node = page.type().create(path.nxt(), spec, retention, parents);
        if (node == null || !node.location().equals(path.nxt()) || node.retention() != retention
                || !node.parents().equals(parents) || !node.content().equals(spec))
            throw new IllegalArgumentException("Page type factory violated the node contract");
        var updated = new LinkedHashMap<>(model.pages());
        updated.put(page.ref().pageId(), page.withNode(node));
        model = new ContainerModel(model.id(), model.root(), updated, model.version() + 1);
        reservations.remove(path.nxt());
        return node;
    }
    private PageModel requirePage(PageRef ref) {
        if (ref.containerId() != model.id() || !model.pages().containsKey(ref.pageId()))
            throw new IllegalArgumentException("Unknown page: " + ref);
        return model.pages().get(ref.pageId());
    }
    private void requireOpen() { if (closed) throw new IllegalStateException("Session closed"); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        reservations.clear();
        model = new ContainerModel(model.id(), null, Map.of(), model.version() + 1);
    }
}
