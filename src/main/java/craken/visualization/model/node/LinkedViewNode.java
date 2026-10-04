package craken.visualization.model.node;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;
import java.util.List;

public class LinkedViewNode extends ViewNode {
    public LinkedViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents) {
        this(location, content, retention, parents, List.of());
    }
    protected LinkedViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents,
                             List<ViewLocation> children) {
        super(location, content, retention, parents, children);
        if (content.kind() != Kind.LINKED) throw new IllegalArgumentException("Wrong node kind");
    }
    @Override public ViewNode withState(Spec content, ParentSelection parents) {
        return new LinkedViewNode(location(), content, retention(), parents, children());
    }
    @Override public ViewNode withChildren(List<ViewLocation> children) {
        return new LinkedViewNode(location(), content(), retention(), parents(), children);
    }
}
