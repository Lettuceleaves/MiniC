package craken.visualization.api;

/** Stable identity, independent of an address, object value or screen position. */
public record ViewLocation(long containerId, long pageId, long nodeId) {
    public ViewLocation {
        if (containerId <= 0 || pageId <= 0 || nodeId <= 0)
            throw new IllegalArgumentException("IDs must be positive");
    }

    public PageRef page() { return new PageRef(containerId, pageId); }
}
