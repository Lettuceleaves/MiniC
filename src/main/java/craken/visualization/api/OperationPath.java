package craken.visualization.api;

import java.util.Objects;

public record OperationPath(ViewLocation pre, ViewLocation nxt) {
    public OperationPath {
        Objects.requireNonNull(nxt, "nxt");
        if (pre != null && pre.containerId() != nxt.containerId())
            throw new IllegalArgumentException("Ownership cannot cross containers");
    }
}
