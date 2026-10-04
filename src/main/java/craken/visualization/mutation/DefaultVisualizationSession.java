package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.PageBindingRule;
import java.util.*;

/** Serial model writer; callers and renderers receive immutable committed values. */
public final class DefaultVisualizationSession implements VisualizationSession {
    private static final MonotonicIds CONTAINERS = new MonotonicIds();
    private final MonotonicIds pages = new MonotonicIds();
    private final MonotonicIds relations = new MonotonicIds();
    private final Map<Long, MonotonicIds> nodes = new HashMap<>();
    private final Set<ViewLocation> reservations = new HashSet<>();
    private final PageTypeRegistry types = new PageTypeRegistry();
    private ContainerModel model = new ContainerModel(CONTAINERS.next(), null, Map.of(), 0);
    private boolean closed;

    @Override public synchronized ContainerModel model() { return model; }
    @Override public synchronized MutationResult modify(MutationBatch batch) {
        if (closed) return MutationResult.failed(model.version(),
                new VisualizationError(VisualizationError.Code.SESSION_CLOSED, "Session closed", 0));
        Objects.requireNonNull(batch, "batch");
        var transaction = new MutationTransaction(model, reservations, relations);
        try {
            transaction.apply(batch);
            ContainerModel next = transaction.finish();
            model = next;
            var change = new ModelChangeSet(next.version(), transaction.affected(), batch.sourceStep());
            return new MutationResult(next.version(), transaction.created(), change, null, transaction.createdRules());
        } catch (VisualizationError.Failure failure) {
            return MutationResult.failed(model.version(),
                    new VisualizationError(failure.code(), failure.getMessage(), transaction.commandIndex()));
        } catch (IllegalArgumentException | NullPointerException failure) {
            return MutationResult.failed(model.version(), new VisualizationError(
                    VisualizationError.Code.INVALID_COMMAND, String.valueOf(failure.getMessage()), transaction.commandIndex()));
        } finally {
            // A failed batch leaves holes, never reusable reserved identities.
            for (var command : batch.commands()) if (command instanceof VisualizationCommand.AddNode add)
                reservations.remove(add.path().nxt());
        }
    }
    @Override public synchronized PageRef initializeRoot(PageType type) {
        requireOpen();
        if (model.root() != null) throw new IllegalStateException("Root already initialized");
        return initialize(type, null, true);
    }
    @Override public synchronized PageRef initializePage(PageType type, ViewLocation anchor) {
        return initializePage(type, PageBindingRule.Spec.node(Objects.requireNonNull(anchor, "anchor")));
    }
    @Override public synchronized PageRef initializePage(PageType type, PageBindingRule.Spec binding) {
        requireOpen();
        Objects.requireNonNull(binding);
        requirePage(binding.parentPage());
        if (binding.parentNode() != null) model.node(binding.parentNode());
        return initialize(type, binding, false);
    }
    private PageRef initialize(PageType type, PageBindingRule.Spec binding, boolean root) {
        types.register(type);
        var ref = new PageRef(model.id(), pages.next());
        var updated = new LinkedHashMap<>(model.pages());
        updated.put(ref.pageId(), new PageModel(ref, type, Map.of(), binding == null ? null : binding.parentNode()));
        nodes.put(ref.pageId(), new MonotonicIds());
        var rules = new LinkedHashMap<>(model.pageRules());
        if (binding != null) { var rule = new PageBindingRule(relations.next(), ref, binding); rules.put(rule.id(), rule); }
        model = new ContainerModel(model.id(), root ? ref : model.root(), updated, model.version() + 1, model.ownership(), rules);
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
        var result = modify(MutationBatch.of(new VisualizationCommand.AddNode(path, spec)));
        if (!result.succeeded()) throw new IllegalArgumentException(result.error().code() + ": " + result.error().message());
        return model.node(path.nxt());
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
