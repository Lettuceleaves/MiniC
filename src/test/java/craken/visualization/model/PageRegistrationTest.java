package craken.visualization.model;

import craken.visualization.api.*;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import craken.visualization.model.node.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class PageRegistrationTest {
    @Test void anAlreadyRegisteredTypeCanInitializeAnotherPage() {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.point());
            var owner = session.reserveNodeId(root);
            session.addNode(new OperationPath(null, owner), ViewNode.Spec.point("owner"));
            var registered = session.model().pages().get(root.pageId()).type();
            var child = session.initializePage(registered, owner);
            assertSame(registered, session.model().pages().get(child.pageId()).type());
        }
    }
    @Test void callerRegistriesAndSessionRegistriesCanReuseTheSameTypeDefinition() {
        try (var session = new DefaultVisualizationSession()) {
            var caller = new PageTypeRegistry(); caller.register(BuiltinPageTypes.point());
            var root = session.initializeRoot(BuiltinPageTypes.point());
            var owner = session.reserveNodeId(root);
            session.addNode(new OperationPath(null, owner), ViewNode.Spec.point("owner"));
            var child = session.initializePage(caller.require("point"), owner);
            assertEquals("point", session.model().pages().get(child.pageId()).type().key());
        }
    }
    @Test void callerMustRegisterTheTypeAndRootIsUnique() {
        try (var session = new DefaultVisualizationSession()) {
            assertThrows(NullPointerException.class, () -> session.initializeRoot(null));
            var root = session.initializeRoot(BuiltinPageTypes.point());
            assertEquals(root, session.model().root());
            assertThrows(IllegalStateException.class, () -> session.initializeRoot(BuiltinPageTypes.point()));
        }
    }
    @Test void pagesHaveIndependentTypesAndFullyQualifiedNodes() {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.point());
            var owner = session.reserveNodeId(root);
            assertInstanceOf(PointViewNode.class, session.addNode(new OperationPath(null, owner), ViewNode.Spec.point("singleton")));
            var array = session.initializePage(BuiltinPageTypes.array(), owner);
            var position = session.reserveNodeId(array);
            var node = session.addNode(new OperationPath(owner, position), new ViewNode.Spec(ViewNode.Kind.ARRAY, "values"));
            assertInstanceOf(ArrayViewNode.class, node);
            assertEquals(ViewNode.Retention.OWNED, node.retention());
            assertEquals(owner, node.parents().selected());
            assertEquals(node, session.model().node(position));
            assertNotEquals(root, array);
            assertThrows(IllegalArgumentException.class, () -> session.reserveNodeId(new PageRef(999, 1)));
        }
    }
    @Test void pageCannotAdmitNullOwnerOutsideRootOrUnsupportedNodeKinds() {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.point());
            var owner = session.reserveNodeId(root);
            session.addNode(new OperationPath(null, owner), ViewNode.Spec.point("root"));
            var page = session.initializePage(BuiltinPageTypes.array(), owner);
            var reserved = session.reserveNodeId(page);
            assertThrows(IllegalArgumentException.class,
                    () -> session.addNode(new OperationPath(null, reserved), ViewNode.Spec.point("orphan")));
            assertThrows(IllegalArgumentException.class,
                    () -> session.addNode(new OperationPath(owner, reserved), new ViewNode.Spec(ViewNode.Kind.TREE, "wrong")));
            assertTrue(session.model().pages().get(page.pageId()).nodes().isEmpty());
        }
    }
    @Test void callerContentAndOldCommittedModelsRemainIndependent() {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.point());
            var oldModel = session.model();
            var values = new HashMap<String, String>();
            values.put("value", "before");
            var node = session.addNode(new OperationPath(null, session.reserveNodeId(root)),
                    new ViewNode.Spec(ViewNode.Kind.POINT, "point", values));
            values.put("value", "after");
            assertEquals("before", node.content().fields().get("value"));
            assertTrue(oldModel.pages().get(root.pageId()).nodes().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> session.model().pages().clear());
        }
    }
    @Test void extensionFactoryCannotCorruptTheRegisteredIdentity() {
        PageType invalidFactory = new PageType() {
            public String key() { return "bad-factory"; }
            public boolean readyEnabled() { return false; }
            public int maximumNesting() { return 0; }
            public Layout layout() { return Layout.POINT; }
            public Set<ViewNode.Kind> nodeKinds() { return Set.of(ViewNode.Kind.POINT); }
            public ViewNode create(ViewLocation p, ViewNode.Spec s, ViewNode.Retention r, ParentSelection parents) {
                return new PointViewNode(new ViewLocation(p.containerId(), p.pageId(), p.nodeId() + 1), s, r, parents);
            }
        };
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(invalidFactory);
            var before = session.model();
            var reserved = session.reserveNodeId(root);
            assertThrows(IllegalArgumentException.class,
                    () -> session.addNode(new OperationPath(null, reserved), ViewNode.Spec.point("x")));
            assertSame(before, session.model());
        }
    }
    @Test void closedSessionCannotAllocateAndCloseIsIdempotent() {
        var session = new DefaultVisualizationSession();
        var page = session.initializeRoot(BuiltinPageTypes.point());
        session.close();
        session.close();
        assertThrows(IllegalStateException.class, () -> session.reserveNodeId(page));
        assertTrue(session.model().pages().isEmpty());
    }
}
