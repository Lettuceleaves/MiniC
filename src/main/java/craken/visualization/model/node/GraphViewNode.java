package craken.visualization.model.node;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;
import java.util.List;

public class GraphViewNode extends ViewNode {
    public GraphViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents) {
        this(location, content, retention, parents, List.of());
    }
    protected GraphViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents,
                             List<ViewLocation> children) {
        super(location, content, retention, parents, children);
        if (content.kind() != Kind.GRAPH) throw new IllegalArgumentException("Wrong node kind");
    }
    @Override public ViewNode withState(Spec content, ParentSelection parents) {
        return new GraphViewNode(location(), content, retention(), parents, children());
    }
    @Override public ViewNode withChildren(List<ViewLocation> children) {
        return new GraphViewNode(location(), content(), retention(), parents(), children);
    }
}
