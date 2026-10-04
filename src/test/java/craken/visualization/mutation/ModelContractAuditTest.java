package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.*;
import craken.visualization.support.ModelFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class ModelContractAuditTest {
    @Test void aFinalDagFailureReportsAnExistingCommandIndex() {
        try (var f = new ModelFixture()) {
            var a = f.node(f.page(f.r), f.r, "a");
            var b = f.node(f.page(a), a, "b");
            var result = f.session.modify(MutationBatch.of(new AttachOwnership(b, a)));
            assertEquals(VisualizationError.Code.OWNERSHIP_CYCLE, result.error().code());
            assertEquals(0, result.error().commandIndex());
        }
    }
    @Test void registeredMetadataIsFrozenEvenWhenTheCallerMutatesItsType() {
        try (var session = new DefaultVisualizationSession()) {
            var type = new ExtensionType();
            var root = session.initializeRoot(type);
            var before = session.snapshot();
            type.ready = true;
            type.kinds.add(ViewNode.Kind.ARRAY);
            assertEquals(before.pages().get(root.pageId()).type(), session.snapshot().pages().get(root.pageId()).type());
            var reserved = session.reserveNodeId(root);
            assertFalse(session.modify(MutationBatch.of(new AddNode(new OperationPath(null, reserved),
                    new ViewNode.Spec(ViewNode.Kind.ARRAY, "not registered")))).succeeded());
            session.restore(before);
        }
    }

    @Test void factoryCannotInventCompositionChildren() {
        try (var session = new DefaultVisualizationSession()) {
            var type = new ExtensionType(); type.mode = Mode.FACTORY_CHILDREN;
            var root = session.initializeRoot(type);
            var reserved = session.reserveNodeId(root);
            var before = session.model();
            var result = session.modify(MutationBatch.of(new AddNode(new OperationPath(null, reserved), ViewNode.Spec.point("x"))));
            assertFalse(result.succeeded());
            assertSame(before, session.model());
        }
    }

    @Test void selectingAnUpstreamCannotSilentlyRewriteCustomNodeContent() {
        try (var session = new DefaultVisualizationSession()) {
            var type = new ExtensionType();
            var root = session.initializeRoot(type);
            var owner = add(session, root, null, "owner");
            var page = session.initializePage(type, owner);
            var child = add(session, page, owner, "child");
            var before = session.model();
            type.mode = Mode.COPY_CONTENT;
            assertFalse(session.modify(MutationBatch.of(new Touch(new OperationPath(owner, child), AccessKind.READ))).succeeded());
            assertSame(before, session.model());
        }
    }

    @Test void compositionCopiesMustPreserveTheRequestedChildren() {
        try (var session = new DefaultVisualizationSession()) {
            var type = new ExtensionType();
            var root = session.initializeRoot(type);
            var parent = add(session, root, null, "parent");
            var child = add(session, root, null, "child");
            var before = session.model(); type.mode = Mode.DROP_CHILDREN;
            assertFalse(session.modify(MutationBatch.of(new Compose(parent, child, 0))).succeeded());
            assertSame(before, session.model());
        }
    }

    @Test void aContentChangeRechecksSemanticNestingBeforePublication() {
        try (var session = new DefaultVisualizationSession()) {
            var type = new ExtensionType(); type.semantic = true;
            var root = session.initializeRoot(type);
            var outer = add(session, root, null, "structure");
            var middle = add(session, root, null, "structure");
            var value = add(session, root, null, "scalar");
            session.modify(MutationBatch.of(new Compose(outer, middle, 0), new Compose(middle, value, 0))).requireSuccess();
            var before = session.model();
            var result = session.modify(MutationBatch.of(new SetContent(new OperationPath(null, value), ViewNode.Spec.point("structure"))));
            assertFalse(result.succeeded());
            assertEquals(VisualizationError.Code.NESTING_LIMIT, result.error().code());
            assertSame(before, session.model());
        }
    }

    @Test void aDetachedUpstreamCannotBeUsedByALaterCommandInTheSameBatch() {
        try (var f = new ModelFixture()) {
            var other = f.root("other");
            var child = f.node(f.page(f.r), f.r, "child");
            f.session.modify(MutationBatch.of(new AttachOwnership(other, child))).requireSuccess();
            var before = f.session.model();
            var result = f.session.modify(MutationBatch.of(new DetachOwnership(f.r, child),
                    new Touch(new OperationPath(f.r, child), AccessKind.READ)));
            assertFalse(result.succeeded());
            assertSame(before, f.session.model());
        }
    }

    @Test void anExplicitlyDeletedUpstreamCannotBeUsedByALaterCommand() {
        try (var f = new ModelFixture()) {
            var other = f.root("other");
            var child = f.node(f.page(f.r), f.r, "child");
            f.session.modify(MutationBatch.of(new AttachOwnership(other, child))).requireSuccess();
            var before = f.session.model();
            var result = f.session.modify(MutationBatch.of(new DeleteNode(new OperationPath(null, f.r)),
                    new Touch(new OperationPath(f.r, child), AccessKind.READ)));
            assertFalse(result.succeeded());
            assertSame(before, f.session.model());
        }
    }

    @Test void composingIntoADeletedNodeCannotAccidentallyDeleteAnOtherwiseLiveNode() {
        try (var f = new ModelFixture()) {
            var other = f.root("other"); var before = f.session.model();
            var result = f.session.modify(MutationBatch.of(new DeleteNode(new OperationPath(null, f.r)), new Compose(f.r, other, 0)));
            assertFalse(result.succeeded());
            assertSame(before, f.session.model());
        }
    }

    private static ViewLocation add(DefaultVisualizationSession session, PageRef page, ViewLocation pre, String label) {
        var at = session.reserveNodeId(page);
        session.addNode(new OperationPath(pre, at), ViewNode.Spec.point(label)); return at;
    }
    private enum Mode { NORMAL, FACTORY_CHILDREN, COPY_CONTENT, DROP_CHILDREN }
    private static final class ExtensionType implements PageType {
        boolean ready, semantic;
        Mode mode = Mode.NORMAL;
        final Set<ViewNode.Kind> kinds = EnumSet.of(ViewNode.Kind.POINT);
        public String key() { return "audit-extension"; }
        public boolean readyEnabled() { return ready; }
        public int maximumNesting() { return 1; }
        public Layout layout() { return Layout.POINT; }
        public Set<ViewNode.Kind> nodeKinds() { return kinds; }
        public SemanticNestingPolicy nestingPolicy() {
            return semantic ? (p, c) -> c.content().label().equals("structure") ? 1 : 0 : PageType.super.nestingPolicy();
        }
        public ViewNode create(ViewLocation p, ViewNode.Spec s, ViewNode.Retention r, ParentSelection parents) {
            return new ExtensionNode(this, p, s, r, parents, mode == Mode.FACTORY_CHILDREN ? List.of(p) : List.of());
        }
    }
    private static final class ExtensionNode extends ViewNode {
        private final ExtensionType type;
        ExtensionNode(ExtensionType type, ViewLocation p, Spec s, Retention r, ParentSelection parents, List<ViewLocation> children) {
            super(p, s, r, parents, children); this.type = type;
        }
        public ViewNode withState(Spec s, ParentSelection parents) {
            return new ExtensionNode(type, location(), type.mode == Mode.COPY_CONTENT ? Spec.point("corrupt") : s, retention(), parents, children());
        }
        public ViewNode withChildren(List<ViewLocation> children) {
            return new ExtensionNode(type, location(), content(), retention(), parents(), type.mode == Mode.DROP_CHILDREN ? List.of() : children);
        }
    }
}
