package craken.visualization.model.node;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;
import java.util.List;

public class ArrayViewNode extends ViewNode {
    public ArrayViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents) {
        this(location, content, retention, parents, List.of());
    }
    protected ArrayViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents,
                             List<ViewLocation> children) {
        super(location, content, retention, parents, children);
        if (content.kind() != Kind.ARRAY) throw new IllegalArgumentException("Wrong node kind");
    }
    @Override public ViewNode withState(Spec content, ParentSelection parents) {
        return new ArrayViewNode(location(), content, retention(), parents, children());
    }
    @Override public ViewNode withChildren(List<ViewLocation> children) {
        return new ArrayViewNode(location(), content(), retention(), parents(), children);
    }
}
