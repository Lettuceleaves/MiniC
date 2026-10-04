package craken.visualization.api;

import craken.visualization.model.ViewNode;
import craken.visualization.model.relation.TopologyEdge.Direction;
import java.util.Objects;

public sealed interface VisualizationCommand {
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
    record Connect(OperationPath a, OperationPath b, Direction direction) implements VisualizationCommand {
        public Connect { Objects.requireNonNull(a); Objects.requireNonNull(b); Objects.requireNonNull(direction); }
        public Connect(OperationPath a, OperationPath b) { this(a, b, Direction.NONE); }
    }
    record Disconnect(OperationPath a, OperationPath b) implements VisualizationCommand {
        public Disconnect { Objects.requireNonNull(a); Objects.requireNonNull(b); }
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
