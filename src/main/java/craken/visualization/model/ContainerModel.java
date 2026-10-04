package craken.visualization.model;

import craken.visualization.api.*;
import java.util.LinkedHashMap;
import java.util.Map;

public record ContainerModel(long id, PageRef root, Map<Long, PageModel> pages, long version) {
    public ContainerModel {
        pages = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(pages));
    }
    public ViewNode node(ViewLocation location) {
        if (location.containerId() != id) throw new IllegalArgumentException("Foreign container");
        PageModel page = pages.get(location.pageId());
        if (page == null || !page.nodes().containsKey(location.nodeId()))
            throw new IllegalArgumentException("Unknown node: " + location);
        return page.nodes().get(location.nodeId());
    }
}
