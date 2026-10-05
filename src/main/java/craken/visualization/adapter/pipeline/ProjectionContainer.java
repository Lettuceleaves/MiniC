package craken.visualization.adapter.pipeline;

import craken.visualization.api.*;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.model.relation.TopologyEdge;
import craken.visualization.snapshot.VisualizationSnapshot;
import java.util.*;

/** Applies a desired plan to a private core session and retains stable cell/AST identities. */
final class ProjectionContainer {
    final VisualizationSession session;
    final Map<ProjectionKey, ViewLocation> locations;
    PipelineProjectionPlan plan;
    ProjectionContainer() { this(new DefaultVisualizationSession(), new LinkedHashMap<>(), null); }
    private ProjectionContainer(VisualizationSession session, Map<ProjectionKey, ViewLocation> locations, PipelineProjectionPlan plan) {
        this.session = session; this.locations = locations; this.plan = plan;
    }
    ProjectionContainer draft() { return new ProjectionContainer(session, new LinkedHashMap<>(locations), plan); }
    VisualizationSnapshot snapshot() {
        var snapshot = session.snapshot();
        if (plan == null || snapshot.interaction().accessed() == null) return snapshot;
        var highlighted = new LinkedHashSet<ViewLocation>();
        plan.highlights().forEach(key -> highlighted.add(locations.get(key)));
        var pages = new LinkedHashMap<Long, VisualizationSnapshot.PageState>();
        for (var page : snapshot.pages().values()) {
            var nodes = new LinkedHashMap<Long, VisualizationSnapshot.NodeState>();
            page.nodes().forEach((id, node) -> nodes.put(id, node.location().equals(snapshot.interaction().accessed())
                    ? new VisualizationSnapshot.NodeState(node.location(), node.content(), node.retention(), node.parents(), highlighted)
                    : node));
            pages.put(page.ref().pageId(), new VisualizationSnapshot.PageState(page.ref(), page.type(), nodes,
                    page.anchor(), page.composition(), page.topology(), page.ready(), page.layoutHints()));
        }
        return new VisualizationSnapshot(snapshot.containerId(), snapshot.root(), pages, snapshot.ownership(), snapshot.pageRules(),
                snapshot.interaction(), snapshot.sourceVersion(), snapshot.epoch(), snapshot.sourceStep(), snapshot.highWater());
    }
    void apply(PipelineProjectionPlan next, String sourceStep) {
        var model = session.model();
        PageRef page = model.root() == null ? session.initializeRoot(next.pageType()) : model.root();
        model = session.model();
        if (!model.pages().get(page.pageId()).type().key().equals(next.pageType().key()))
            throw new IllegalArgumentException("Page type cannot change inside one stage");
        var commands = new ArrayList<VisualizationCommand>();
        var wanted = new LinkedHashSet<ProjectionKey>();
        next.nodes().forEach(node -> wanted.add(node.key()));
        var existingPage = model.pages().get(page.pageId());
        for (var node : next.nodes()) {
            ViewLocation location = locations.get(node.key());
            if (location == null) {
                location = session.reserveNodeId(page);
                locations.put(node.key(), location);
                commands.add(new VisualizationCommand.AddNode(new OperationPath(null, location), node.content()));
            } else if (!model.node(location).content().equals(node.content()))
                commands.add(new VisualizationCommand.SetContent(new OperationPath(null, location), node.content()));
        }
        var desiredEdges = new LinkedHashSet<Edge>();
        next.edges().forEach(edge -> desiredEdges.add(new Edge(locations.get(edge.parent()), locations.get(edge.child()), edge.direction())));
        var existingEdges = new LinkedHashSet<Edge>();
        for (var edge : existingPage.topology().values()) {
            var key = new Edge(edge.a(), edge.b(), edge.direction());
            existingEdges.add(key);
            if (!desiredEdges.contains(key)) commands.add(new VisualizationCommand.Disconnect(path(edge.a()), path(edge.b())));
        }
        // Sequence cell removal never deletes its array parent; AST topology has no lifecycle ownership.
        for (var entry : new ArrayList<>(locations.entrySet())) if (!wanted.contains(entry.getKey())) {
            commands.add(new VisualizationCommand.DeleteNode(path(entry.getValue())));
            locations.remove(entry.getKey());
        }
        var existingComposition = new HashSet<CompositionKey>();
        existingPage.composition().values().forEach(old -> existingComposition.add(
                new CompositionKey(old.parent(), old.child(), old.slot())));
        for (var link : next.composition()) {
            ViewLocation parent = locations.get(link.parent()), child = locations.get(link.child());
            if (!existingComposition.contains(new CompositionKey(parent, child, link.slot())))
                commands.add(new VisualizationCommand.Compose(parent, child, link.slot()));
        }
        for (var edge : desiredEdges) if (!existingEdges.contains(edge))
            commands.add(new VisualizationCommand.Connect(path(edge.parent()), path(edge.child()), edge.direction()));
        ProjectionKey selected = next.focus() != null ? next.focus() : next.nodes().isEmpty() ? null : next.nodes().getFirst().key();
        if (selected != null) {
            commands.add(new VisualizationCommand.SetFocus(path(locations.get(selected))));
            commands.add(new VisualizationCommand.Touch(path(locations.get(selected)), AccessKind.READ));
        }
        MutationResult result = session.modify(new MutationBatch(commands, sourceStep));
        if (!result.succeeded()) throw new IllegalArgumentException(result.error().code() + ": " + result.error().message());
        plan = next;
    }
    private static OperationPath path(ViewLocation location) { return new OperationPath(null, location); }
    private record Edge(ViewLocation parent, ViewLocation child, TopologyEdge.Direction direction) {
        Edge {
            // Core topology stores the lower node ID first, with the direction reversed as needed.
            if (parent.nodeId() > child.nodeId()) {
                var previousParent = parent;
                parent = child;
                child = previousParent;
                direction = direction.reversed();
            }
        }
    }
    private record CompositionKey(ViewLocation parent, ViewLocation child, int slot) { }
}
