package craken.visualization.api;

/** A page identity is meaningful only in its original container. */
public record PageRef(long containerId, long pageId) {
    public PageRef {
        if (containerId <= 0 || pageId <= 0) throw new IllegalArgumentException("IDs must be positive");
    }
}
