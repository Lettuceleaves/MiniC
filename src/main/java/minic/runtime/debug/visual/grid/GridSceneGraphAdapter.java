package minic.runtime.debug.visual.grid;

import minic.runtime.debug.visual.GraphComponent;
import minic.runtime.debug.visual.GraphEdge;
import minic.runtime.debug.visual.GraphNode;
import minic.runtime.debug.visual.GraphStructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts grid scenes to the existing graph visual transport.
 */
public final class GridSceneGraphAdapter {
    private GridSceneGraphAdapter() {
    }

    public static GraphStructure toGraphStructure(GridScene scene) {
        String componentId = "component-" + scene.name();
        List<GraphNode> nodes = scene.nodes().stream()
                .map(node -> new GraphNode(
                        node.id(),
                        node.label(),
                        node.valueRef(),
                        componentId,
                        nodeMetadata(node)
                ))
                .toList();
        List<GraphEdge> edges = scene.edges().stream()
                .map(edge -> new GraphEdge(
                        edge.id(),
                        edge.fromNodeId(),
                        edge.toNodeId(),
                        edge.label(),
                        true,
                        edgeMetadata(edge)
                ))
                .toList();
        GraphComponent component = new GraphComponent(componentId, scene.name(), nodes.stream().map(GraphNode::id).toList());
        return new GraphStructure(
                scene.id(),
                scene.name(),
                scene.kind(),
                "grid",
                nodes,
                edges,
                List.of(component),
                List.of(),
                List.of()
        );
    }

    private static Map<String, String> nodeMetadata(GridSceneNode node) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>(node.metadata());
        metadata.put("gridX", Integer.toString(node.bounds().x()));
        metadata.put("gridY", Integer.toString(node.bounds().y()));
        metadata.put("gridWidth", Integer.toString(node.bounds().width()));
        metadata.put("gridHeight", Integer.toString(node.bounds().height()));
        return metadata;
    }

    private static Map<String, String> edgeMetadata(GridSceneEdge edge) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>(edge.metadata());
        metadata.put("fromAnchor", edge.fromAnchor().name());
        metadata.put("toAnchor", edge.toAnchor().name());
        metadata.put("gridStartX", Integer.toString(edge.start().x()));
        metadata.put("gridStartY", Integer.toString(edge.start().y()));
        metadata.put("gridEndX", Integer.toString(edge.end().x()));
        metadata.put("gridEndY", Integer.toString(edge.end().y()));
        return metadata;
    }
}
