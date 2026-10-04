package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import java.util.Map;
import static craken.visualization.api.VisualizationError.Code.*;

public final class CommandValidator {
    private CommandValidator() {}
    public static PageModel page(long containerId, Map<Long, PageModel> pages, PageRef ref) {
        PageModel page = pages.get(ref.pageId());
        if (ref.containerId() != containerId || page == null)
            throw failure(UNKNOWN_POSITION, "Unknown page: " + ref);
        return page;
    }
    public static ViewNode node(long containerId, Map<Long, PageModel> pages, ViewLocation location) {
        ViewNode node = page(containerId, pages, location.page()).nodes().get(location.nodeId());
        if (node == null) throw failure(UNKNOWN_POSITION, "Unknown node: " + location);
        return node;
    }
    public static void ownership(ViewNode parent, ViewNode child) {
        if (parent.location().containerId() != child.location().containerId()
                || parent.location().page().equals(child.location().page()) || child.retention() == ViewNode.Retention.ROOT)
            throw failure(INVALID_OWNERSHIP, "Ownership must connect distinct pages to an OWNED node");
    }
    public static VisualizationError.Failure failure(VisualizationError.Code code, String message) {
        return new VisualizationError.Failure(code, message);
    }
}
