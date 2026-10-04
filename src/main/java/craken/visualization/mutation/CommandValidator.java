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
    public static ViewNode path(long containerId, Map<Long, PageModel> pages, OperationPath path) {
        ViewNode node = node(containerId, pages, path.nxt());
        if (node.retention() == ViewNode.Retention.ROOT) {
            if (path.pre() != null) throw failure(INVALID_OWNERSHIP, "ROOT node requires a null upstream");
            return node;
        }
        if (path.pre() == null || !node.parents().parents().contains(path.pre()))
            throw failure(INVALID_OWNERSHIP, "Operation requires an effective upstream");
        node(containerId, pages, path.pre());
        return node.withState(node.content(), node.parents().select(path.pre()));
    }
    public static VisualizationError.Failure failure(VisualizationError.Code code, String message) {
        return new VisualizationError.Failure(code, message);
    }
}
