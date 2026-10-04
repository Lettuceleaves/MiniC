package craken.visualization.api;

import craken.visualization.model.*;
import java.util.Set;

/**
 * Caller registration supplies semantics; the container does not inspect VM or JVM types.
 * Registration freezes metadata and the nesting callback. Factory, copy, nesting and highlight
 * callbacks must be deterministic and must not retain mutable caller state or mutate old nodes.
 */
public interface PageType {
    enum Layout { POINT, ARRAY, LINEAR, TREE, STRESS }
    @FunctionalInterface
    interface SemanticNestingPolicy { int increment(ViewNode parent, ViewNode child); }
    String key();
    boolean readyEnabled();
    int maximumNesting();
    Layout layout();
    Set<ViewNode.Kind> nodeKinds();
    ViewNode create(ViewLocation location, ViewNode.Spec spec, ViewNode.Retention retention, ParentSelection parents);
    default SemanticNestingPolicy nestingPolicy() {
        return (parent, child) -> child.content().kind() == ViewNode.Kind.POINT ? 0 : 1;
    }
    default ViewNode copy(ViewNode node) { return node.copy(); }
}
