package craken.visualization.model;

import craken.visualization.api.ViewLocation;
import craken.visualization.api.VisualizationError;
import java.util.List;

/** Checks extension callbacks at their boundary, before their result enters a transaction. */
public final class ViewNodeContract {
    private ViewNodeContract() {}
    public static ViewNode require(ViewNode result, ViewLocation location, ViewNode.Spec content,
                                   ViewNode.Retention retention, ParentSelection parents, List<ViewLocation> children) {
        if (result == null || !result.location().equals(location) || !result.content().equals(content)
                || result.retention() != retention || !result.parents().equals(parents) || !result.children().equals(children))
            throw new VisualizationError.Failure(VisualizationError.Code.PAGE_TYPE_MISMATCH,
                    "Page type factory or node copy violated its contract");
        return result;
    }
    public static ViewNode withState(ViewNode node, ViewNode.Spec content, ParentSelection parents) {
        return require(node.withState(content, parents), node.location(), content, node.retention(), parents, node.children());
    }
    public static ViewNode withChildren(ViewNode node, List<ViewLocation> children) {
        return require(node.withChildren(children), node.location(), node.content(), node.retention(), node.parents(), children);
    }
}
