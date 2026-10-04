package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.ViewNode;
import craken.visualization.support.ModelFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class TouchReadResultTest {
    @Test void repeatedReadsReturnTheContentAtTheirOwnCommandPosition() {
        try (var f = new ModelFixture()) {
            var path = new OperationPath(null, f.r);
            var result = f.session.modify(MutationBatch.of(new Touch(path, AccessKind.READ),
                    new SetContent(path, ViewNode.Spec.point("updated")), new Touch(path, AccessKind.READ))).requireSuccess();
            assertEquals(List.of("root", "updated"), result.reads().stream().map(value -> value.content().label()).toList());
            assertEquals(List.of(0, 2), result.reads().stream().map(MutationResult.ReadValue::commandIndex).toList());
            assertEquals(path, result.reads().getFirst().path());
            assertThrows(UnsupportedOperationException.class, () -> result.reads().clear());
        }
    }
    @Test void aFailedBatchDoesNotLeakPartialReadResults() {
        try (var f = new ModelFixture()) {
            var before = f.session.model();
            var result = f.session.modify(MutationBatch.of(new Touch(new OperationPath(null, f.r), AccessKind.READ),
                    new DeleteNode(new OperationPath(null, new ViewLocation(f.r.containerId(), f.r.pageId(), 999)))));
            assertFalse(result.succeeded()); assertTrue(result.reads().isEmpty()); assertSame(before, f.session.model());
        }
    }
}
