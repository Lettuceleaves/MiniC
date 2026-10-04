package craken.visualization.api;

import craken.visualization.model.ViewNode;
import java.util.Objects;

public sealed interface VisualizationCommand {
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
