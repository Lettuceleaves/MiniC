package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.model.ViewNode;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Large batches must stay linear: one array of ten thousand rows commits in one pass. */
@Tag("visualization-model")
final class LargeBatchTransactionTest {
    @Test void oneBatchBuildsATenThousandRowArrayWithoutPerCommandCopies() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.array());
            var commands = new ArrayList<VisualizationCommand>();
            var rows = new ArrayList<ViewLocation>();
            var array = session.reserveNodeId(page);
            commands.add(new VisualizationCommand.AddNode(new OperationPath(null, array),
                    new ViewNode.Spec(ViewNode.Kind.ARRAY, "rows")));
            for (int index = 0; index < 10_000; index++) {
                var row = session.reserveNodeId(page);
                commands.add(new VisualizationCommand.AddNode(new OperationPath(null, row),
                        new ViewNode.Spec(ViewNode.Kind.POINT, "row" + index)));
                commands.add(new VisualizationCommand.Compose(array, row, index));
                rows.add(row);
            }
            MutationResult result = assertTimeoutPreemptively(Duration.ofSeconds(20),
                    () -> session.modify(new MutationBatch(commands, "large-batch")));
            result.requireSuccess();
            var arrayNode = session.model().node(array);
            assertEquals(10_000, arrayNode.children().size());
            assertEquals(rows, arrayNode.children(), "children must stay in slot order");
            assertEquals(10_000, session.model().pages().get(page.pageId()).composition().size());
        }
    }
}
