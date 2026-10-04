package craken.visualization.mutation;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;
import java.util.*;
import static craken.visualization.api.VisualizationError.Code.*;

public final class OwnershipDagValidator {
    private OwnershipDagValidator() {}
    public static void validate(Map<Long, PageModel> pages, OwnershipStore ownership) {
        var degrees = new HashMap<ViewLocation, Integer>();
        for (PageModel page : pages.values()) for (ViewNode node : page.nodes().values()) {
            var parents = ownership.parents(node.location());
            if (!parents.equals(new HashSet<>(node.parents().parents())))
                throw CommandValidator.failure(INVALID_OWNERSHIP, "Parent indexes disagree");
            if ((node.retention() == ViewNode.Retention.ROOT) != parents.isEmpty())
                throw CommandValidator.failure(INVALID_OWNERSHIP, "Invalid retention/incoming count");
            degrees.put(node.location(), parents.size());
        }
        var queue = new ArrayDeque<ViewLocation>();
        degrees.forEach((location, degree) -> { if (degree == 0) queue.add(location); });
        int visited = 0;
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            visited++;
            for (var child : ownership.children(current)) {
                Integer degree = degrees.get(child);
                if (degree == null) throw CommandValidator.failure(INVALID_OWNERSHIP, "Dangling child");
                degrees.put(child, degree - 1);
                if (degree == 1) queue.addLast(child);
            }
        }
        if (visited != degrees.size())
            throw CommandValidator.failure(OWNERSHIP_CYCLE, "Ownership would create a node cycle");
    }
}
