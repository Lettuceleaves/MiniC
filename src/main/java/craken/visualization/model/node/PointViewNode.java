package craken.visualization.model.node;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;

public class PointViewNode extends ViewNode {
    public PointViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents) {
        super(location, content, retention, parents);
        if (content.kind() != Kind.POINT) throw new IllegalArgumentException("Wrong node kind");
    }
    @Override public ViewNode withState(Spec content, ParentSelection parents) {
        return new PointViewNode(location(), content, retention(), parents);
    }
}
