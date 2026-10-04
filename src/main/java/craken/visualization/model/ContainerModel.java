package craken.visualization.model;

import craken.visualization.api.*;
import java.util.LinkedHashMap;
import java.util.Map;
import craken.visualization.model.relation.OwnershipBinding;

public record ContainerModel(long id, PageRef root, Map<Long, PageModel> pages, long version,
                             Map<OwnershipBinding.Key, OwnershipBinding> ownership) {
    public ContainerModel {
        pages = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(pages));
        ownership = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(ownership));
    }
    public ContainerModel(long id, PageRef root, Map<Long, PageModel> pages, long version) {
        this(id, root, pages, version, Map.of());
    }
    public ViewNode node(ViewLocation location) {
        if (location.containerId() != id) throw new IllegalArgumentException("Foreign container");
        PageModel page = pages.get(location.pageId());
        if (page == null || !page.nodes().containsKey(location.nodeId()))
            throw new IllegalArgumentException("Unknown node: " + location);
        return page.nodes().get(location.nodeId());
    }
}
