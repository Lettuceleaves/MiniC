package craken.visualization.model.node;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;

public class LinkedViewNode extends ViewNode {
    public LinkedViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents) {
        super(location, content, retention, parents);
        if (content.kind() != Kind.LINKED) throw new IllegalArgumentException("Wrong node kind");
    }
    @Override public ViewNode withState(Spec content, ParentSelection parents) {
        return new LinkedViewNode(location(), content, retention(), parents);
    }
}
