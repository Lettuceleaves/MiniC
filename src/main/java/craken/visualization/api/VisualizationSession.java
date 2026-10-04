package craken.visualization.api;

import craken.visualization.model.*;

public interface VisualizationSession extends AutoCloseable {
    PageRef initializeRoot(PageType type);
    PageRef initializePage(PageType type, ViewLocation anchor);
    ViewLocation reserveNodeId(PageRef page);
    ViewNode addNode(OperationPath path, ViewNode.Spec spec);
    MutationResult modify(MutationBatch batch);
    ContainerModel model();
    @Override void close();
}
