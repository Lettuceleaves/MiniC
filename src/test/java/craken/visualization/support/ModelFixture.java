package craken.visualization.support;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;

public final class ModelFixture implements AutoCloseable {
    public final DefaultVisualizationSession session = new DefaultVisualizationSession();
    public final PageRef root = session.initializeRoot(BuiltinPageTypes.point());
    public final ViewLocation r = root("root");
    public ViewLocation root(String value) { return node(root, null, value); }
    public PageRef page(ViewLocation parent) { return session.initializePage(BuiltinPageTypes.graph(true), parent); }
    public ViewLocation node(PageRef page, ViewLocation parent, String value) {
        var location = session.reserveNodeId(page);
        session.addNode(new OperationPath(parent, location), ViewNode.Spec.point(value));
        return location;
    }
    @Override public void close() { session.close(); }
}
