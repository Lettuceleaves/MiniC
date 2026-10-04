package craken.visualization.style;

import craken.visualization.api.*;
import craken.visualization.model.ViewNode;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.snapshot.VisualizationSnapshot;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static craken.visualization.api.VisualizationCommand.*;
import static craken.visualization.style.ColorSpec.Preset.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
final class ColorTransactionTest {
    @Test
    void anInvalidSecondContentColorCannotLeakTheFirstValidContentChange() {
        var session = new DefaultVisualizationSession();
        var page = session.initializeRoot(BuiltinPageTypes.point());
        ViewLocation a = session.reserveNodeId(page), b = session.reserveNodeId(page);
        session.modify(MutationBatch.of(new AddNode(path(a), colored("a before", BLUE)),
                new AddNode(path(b), colored("b before", NEUTRAL)))).requireSuccess();
        VisualizationSnapshot before = session.snapshot();
        MutationResult result = session.modify(MutationBatch.of(new SetContent(path(a), colored("a changed", RED)),
                new SetContent(path(b), colored("bad bright fill", new ColorSpec.CustomFill("#D6A64F")))));
        assertFalse(result.succeeded());
        assertEquals(VisualizationError.Code.INVALID_COMMAND, result.error().code());
        assertTrue(result.error().message().contains("#D6A64F"));
        assertEquals(before, session.snapshot());
    }

    @Test
    void invalidNewNodeColorRollsBackAnEarlierValidContentModification() {
        var session = new DefaultVisualizationSession();
        var page = session.initializeRoot(BuiltinPageTypes.point());
        ViewLocation a = session.reserveNodeId(page);
        session.addNode(path(a), colored("before", BLUE));
        ViewLocation candidate = session.reserveNodeId(page);
        VisualizationSnapshot before = session.snapshot();
        MutationResult result = session.modify(MutationBatch.of(new SetContent(path(a), colored("changed", RED)),
                new AddNode(path(candidate), colored("bad", new ColorSpec.CustomFill("#161B22")))));
        assertFalse(result.succeeded());
        assertEquals(before, session.snapshot());
        assertFalse(session.model().pages().get(page.pageId()).nodes().containsKey(candidate.nodeId()));
    }

    @Test
    void invalidSnapshotColorCannotPublishOtherValidSnapshotChanges() {
        var session = new DefaultVisualizationSession();
        var page = session.initializeRoot(BuiltinPageTypes.point());
        ViewLocation a = session.reserveNodeId(page), b = session.reserveNodeId(page);
        session.modify(MutationBatch.of(new AddNode(path(a), colored("a before", BLUE)),
                new AddNode(path(b), colored("b before", BLACK)))).requireSuccess();
        VisualizationSnapshot before = session.snapshot();
        var oldPage = before.pages().get(page.pageId());
        var nodes = new LinkedHashMap<>(oldPage.nodes());
        var oldA = nodes.get(a.nodeId());
        var oldB = nodes.get(b.nodeId());
        nodes.put(a.nodeId(), new VisualizationSnapshot.NodeState(a, colored("changed", RED), oldA.retention(), oldA.parents(), oldA.highlights()));
        nodes.put(b.nodeId(), new VisualizationSnapshot.NodeState(b, colored("invalid", new ColorSpec.CustomFill("#A43D46")), oldB.retention(), oldB.parents(), oldB.highlights()));
        var pages = new LinkedHashMap<>(before.pages());
        pages.put(page.pageId(), new VisualizationSnapshot.PageState(page, oldPage.type(), nodes, oldPage.anchor(),
                oldPage.composition(), oldPage.topology(), oldPage.ready()));
        var invalid = new VisualizationSnapshot(before.containerId(), before.root(), pages, before.ownership(), before.pageRules(),
                before.interaction(), before.sourceVersion(), before.epoch(), before.sourceStep(), before.highWater());
        var published = session.model();
        assertThrows(IllegalArgumentException.class, () -> session.restore(invalid));
        assertSame(published, session.model());
        assertEquals(before, session.snapshot());
    }

    @Test
    void validExplicitColorsAndLegacyInheritanceSurviveSnapshotRoundTrips() {
        var session = new DefaultVisualizationSession();
        var page = session.initializeRoot(BuiltinPageTypes.point());
        ViewLocation inherited = session.reserveNodeId(page), explicit = session.reserveNodeId(page);
        session.modify(MutationBatch.of(new AddNode(path(inherited), new ViewNode.Spec(ViewNode.Kind.POINT, "old API", Map.of())),
                new AddNode(path(explicit), colored("explicit", new ColorSpec.CustomFill("#445577"))))).requireSuccess();
        var saved = session.snapshot();
        session.restore(saved);
        assertNull(session.model().node(inherited).content().color());
        assertEquals(new ColorSpec.CustomFill("#445577"), session.model().node(explicit).content().color());
        assertEquals("#3E4D6B", ColorValidator.validate(session.model().node(explicit).content().color()).header().hex());
    }

    private static OperationPath path(ViewLocation location) { return new OperationPath(null, location); }
    private static ViewNode.Spec colored(String label, ColorSpec color) { return new ViewNode.Spec(ViewNode.Kind.POINT, label, Map.of(), color); }
}
