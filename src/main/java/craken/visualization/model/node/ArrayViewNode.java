package craken.visualization.model.node;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;

public class ArrayViewNode extends ViewNode {
    public ArrayViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents) {
        super(location, content, retention, parents);
        if (content.kind() != Kind.ARRAY) throw new IllegalArgumentException("Wrong node kind");
    }
    @Override public ViewNode withState(Spec content, ParentSelection parents) {
        return new ArrayViewNode(location(), content, retention(), parents);
    }
}
