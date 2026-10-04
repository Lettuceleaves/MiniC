package craken.visualization.model.node;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;

public class GraphViewNode extends ViewNode {
    public GraphViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents) {
        super(location, content, retention, parents);
        if (content.kind() != Kind.GRAPH) throw new IllegalArgumentException("Wrong node kind");
    }
    @Override public ViewNode withState(Spec content, ParentSelection parents) {
        return new GraphViewNode(location(), content, retention(), parents);
    }
}
