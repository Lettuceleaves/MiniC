package craken.visualization.model.node;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;

public class TreeViewNode extends ViewNode {
    public TreeViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents) {
        super(location, content, retention, parents);
        if (content.kind() != Kind.TREE) throw new IllegalArgumentException("Wrong node kind");
    }
    @Override public ViewNode withState(Spec content, ParentSelection parents) {
        return new TreeViewNode(location(), content, retention(), parents);
    }
}
