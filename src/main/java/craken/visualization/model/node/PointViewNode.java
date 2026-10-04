package craken.visualization.model.node;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;
import java.util.List;

public class PointViewNode extends ViewNode {
    public PointViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents) {
        this(location, content, retention, parents, List.of());
    }
    protected PointViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents,
                             List<ViewLocation> children) {
        super(location, content, retention, parents, children);
        if (content.kind() != Kind.POINT) throw new IllegalArgumentException("Wrong node kind");
    }
    @Override public ViewNode withState(Spec content, ParentSelection parents) {
        return new PointViewNode(location(), content, retention(), parents, children());
    }
    @Override public ViewNode withChildren(List<ViewLocation> children) {
        return new PointViewNode(location(), content(), retention(), parents(), children);
    }
}
