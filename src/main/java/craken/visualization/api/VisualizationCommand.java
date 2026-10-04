package craken.visualization.api;

import craken.visualization.model.ViewNode;
import craken.visualization.model.relation.TopologyEdge.Direction;
import java.util.Objects;

public sealed interface VisualizationCommand {
    record SetPageLayout(PageRef page, PageLayoutHints hints) implements VisualizationCommand {
        public SetPageLayout { Objects.requireNonNull(page); Objects.requireNonNull(hints); }
    }
    record SetContent(OperationPath path, ViewNode.Spec spec) implements VisualizationCommand {
        public SetContent { Objects.requireNonNull(path); Objects.requireNonNull(spec); }
    }
    record Touch(OperationPath path, AccessKind kind) implements VisualizationCommand {
        public Touch { Objects.requireNonNull(path); Objects.requireNonNull(kind); }
    }
    record SetFocus(OperationPath path) implements VisualizationCommand { public SetFocus { Objects.requireNonNull(path); } }
    /** Navigation management: keep live nodes and options, clear the current focus and highlight. */
    record ClearFocus() implements VisualizationCommand {}
    record Configure(VisualizationOptions options) implements VisualizationCommand { public Configure { Objects.requireNonNull(options); } }
    record BindPage(PageRef child, craken.visualization.model.relation.PageBindingRule.Spec binding) implements VisualizationCommand {
        public BindPage { Objects.requireNonNull(child); Objects.requireNonNull(binding); }
    }
    record UnbindPage(long ruleId) implements VisualizationCommand {}
    record DeleteNode(OperationPath path) implements VisualizationCommand {
        public DeleteNode { Objects.requireNonNull(path); }
    }
    record DetachOwnership(ViewLocation pre, ViewLocation nxt, String source) implements VisualizationCommand {
        public DetachOwnership { Objects.requireNonNull(pre); Objects.requireNonNull(nxt); Objects.requireNonNull(source); }
        public DetachOwnership(ViewLocation pre, ViewLocation nxt) { this(pre, nxt, "explicit"); }
    }
    record Connect(OperationPath a, OperationPath b, Direction direction, String aPort, String bPort, craken.visualization.style.EdgeStyle style) implements VisualizationCommand {
        public Connect { Objects.requireNonNull(a); Objects.requireNonNull(b); Objects.requireNonNull(direction); Objects.requireNonNull(style); craken.visualization.model.relation.TopologyEdge.requirePort(aPort); craken.visualization.model.relation.TopologyEdge.requirePort(bPort); }
        public Connect(OperationPath a, OperationPath b, Direction direction) { this(a, b, direction, "node", "node", craken.visualization.style.EdgeStyle.DEFAULT); }
        public Connect(OperationPath a, OperationPath b) { this(a, b, Direction.NONE); }
    }
    record Disconnect(OperationPath a, OperationPath b, String aPort, String bPort) implements VisualizationCommand {
        public Disconnect { Objects.requireNonNull(a); Objects.requireNonNull(b); craken.visualization.model.relation.TopologyEdge.requirePort(aPort); craken.visualization.model.relation.TopologyEdge.requirePort(bPort); }
        public Disconnect(OperationPath a, OperationPath b) { this(a, b, "node", "node"); }
    }
    record Compose(ViewLocation parent, ViewLocation child, int slot) implements VisualizationCommand {
        public Compose {
            Objects.requireNonNull(parent); Objects.requireNonNull(child);
            if (slot < 0) throw new IllegalArgumentException("Negative slot");
        }
    }
    record AddNode(OperationPath path, ViewNode.Spec spec) implements VisualizationCommand {
        public AddNode { Objects.requireNonNull(path); Objects.requireNonNull(spec); }
    }
    record AttachOwnership(ViewLocation pre, ViewLocation nxt, String source) implements VisualizationCommand {
        public AttachOwnership {
            Objects.requireNonNull(pre); Objects.requireNonNull(nxt); Objects.requireNonNull(source);
            if (source.isBlank()) throw new IllegalArgumentException("Empty ownership source");
        }
        public AttachOwnership(ViewLocation pre, ViewLocation nxt) { this(pre, nxt, "explicit"); }
    }
}
