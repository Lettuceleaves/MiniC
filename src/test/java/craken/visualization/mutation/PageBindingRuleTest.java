package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.PageBindingRule;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.*;
import java.util.Set;
import static craken.visualization.api.VisualizationCommand.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class PageBindingRuleTest {
    DefaultVisualizationSession session;
    PageRef root;
    ViewLocation a, b;
    @BeforeEach void setup() {
        session = new DefaultVisualizationSession(); root = session.initializeRoot(BuiltinPageTypes.point());
        a = add(root, null, "a"); b = add(root, null, "b");
    }
    @AfterEach void close() { session.close(); }
    ViewLocation add(PageRef page, ViewLocation parent, String label) {
        var node = session.reserveNodeId(page); session.addNode(new OperationPath(parent, node), ViewNode.Spec.point(label)); return node;
    }
    long rule(PageRef page) { return session.model().pageRules().values().stream().filter(r -> r.child().equals(page)).findFirst().orElseThrow().id(); }
    void apply(VisualizationCommand... commands) { session.modify(MutationBatch.of(commands)).requireSuccess(); }
    @Test void nodeRuleBindsEveryChildWithoutStealingExplicitSelection() {
        var page = session.initializePage(BuiltinPageTypes.graph(true), a);
        var child = add(page, b, "child");
        assertEquals(Set.of(a,b), Set.copyOf(session.model().node(child).parents().parents()));
        assertEquals(b, session.model().node(child).parents().selected());
        assertEquals(1, session.model().pageRules().size());
    }
    @Test void wholePageRuleTracksNewParentsAndReadyChildren() {
        var page = session.initializePage(BuiltinPageTypes.graph(true), PageBindingRule.Spec.page(root));
        var c = add(page, a, "c"); var d = add(page, b, "d"); var e = add(root, null, "e");
        for (var n : new ViewLocation[]{c,d}) assertEquals(Set.of(a,b,e), Set.copyOf(session.model().node(n).parents().parents()));
        assertEquals(Set.of(d.nodeId()), session.model().pages().get(page.pageId()).ready());
        assertEquals(b, session.model().node(d).parents().selected());
    }
    @Test void unbindOnlyRemovesItsSourceAndDoesNotRetainEmptyPage() {
        var page = session.initializePage(BuiltinPageTypes.graph(true), a); var c = add(page, a, "c");
        apply(new UnbindPage(rule(page)));
        assertEquals(Set.of(a), Set.copyOf(session.model().node(c).parents().parents()));
        apply(new DetachOwnership(a,c));
        assertFalse(session.model().pages().containsKey(page.pageId()));
    }
    @Test void dynamicCycleFailsAtomically() {
        var p = session.initializePage(BuiltinPageTypes.graph(true), a); var x = add(p,a,"x");
        var q = session.initializePage(BuiltinPageTypes.graph(true), x); var y = add(q,x,"y");
        var before = session.model();
        var result = session.modify(MutationBatch.of(new BindPage(p, PageBindingRule.Spec.node(y))));
        assertFalse(result.succeeded()); assertEquals(VisualizationError.Code.OWNERSHIP_CYCLE,result.error().code());
        assertSame(before,session.model());
    }
    @Test void deletedChildIsNotResurrectedAndRuleKeepsValidEmptyPage() {
        var p = session.initializePage(BuiltinPageTypes.graph(true), a); var x = add(p,a,"x");
        apply(new DeleteNode(new OperationPath(a,x)));
        assertTrue(session.model().pages().get(p.pageId()).nodes().isEmpty());
        var y = add(p,b,"y"); assertTrue(y.nodeId()>x.nodeId());
        assertEquals(Set.of(a,b),Set.copyOf(session.model().node(y).parents().parents()));
        assertFalse(session.model().pages().get(p.pageId()).nodes().containsKey(x.nodeId()));
    }
    @Test void emptyRulePageRemovedWhenRuleIsRemoved() {
        var p = session.initializePage(BuiltinPageTypes.graph(true), a);
        apply(new UnbindPage(rule(p)));
        assertFalse(session.model().pages().containsKey(p.pageId()));
    }
}
