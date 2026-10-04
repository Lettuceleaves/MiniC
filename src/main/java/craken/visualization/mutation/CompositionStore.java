package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.CompositionLink;
import java.util.*;
import static craken.visualization.api.VisualizationError.Code.*;

public final class CompositionStore {
    private CompositionStore() {}
    public static PageModel compose(PageModel page, ViewNode parent, ViewNode child, int slot) {
        if (!parent.location().page().equals(child.location().page()))
            throw CommandValidator.failure(COMPOSITION_CONFLICT, "Composition must stay in one page");
        var links = new LinkedHashMap<>(page.composition());
        var previous = links.get(child.location().nodeId());
        if (previous != null && (!previous.parent().equals(parent.location()) || previous.slot() != slot))
            throw CommandValidator.failure(COMPOSITION_CONFLICT, "Child already has another parent or slot");
        for (var link : links.values()) if (link.parent().equals(parent.location()) && link.slot() == slot
                && !link.child().equals(child.location()))
            throw CommandValidator.failure(COMPOSITION_CONFLICT, "Slot is already occupied");
        int increment = page.type().nestingPolicy().increment(parent, child);
        if (increment < 0 || increment > 1)
            throw CommandValidator.failure(NESTING_LIMIT, "Nesting policy must return 0 or 1");
        links.put(child.location().nodeId(), new CompositionLink(parent.location(), child.location(), slot, increment));
        validate(page, links);
        var nodes = new LinkedHashMap<>(page.nodes());
        var children = links.values().stream().filter(link -> link.parent().equals(parent.location()))
                .sorted(Comparator.comparingInt(CompositionLink::slot)).map(CompositionLink::child).toList();
        nodes.put(parent.location().nodeId(), ViewNodeContract.withChildren(parent, children));
        return page.withComposition(nodes, links);
    }
    /** Content-dependent extension policies must be re-evaluated after all commands. */
    public static PageModel refreshSemantics(PageModel page) {
        var links = new LinkedHashMap<Long, CompositionLink>();
        for (var link : page.composition().values()) {
            int increment = page.type().nestingPolicy().increment(page.nodes().get(link.parent().nodeId()), page.nodes().get(link.child().nodeId()));
            if (increment < 0 || increment > 1) throw CommandValidator.failure(NESTING_LIMIT, "Nesting policy must return 0 or 1");
            links.put(link.child().nodeId(), new CompositionLink(link.parent(), link.child(), link.slot(), increment));
        }
        validate(page, links);
        return links.equals(page.composition()) ? page : page.withComposition(page.nodes(), links);
    }
    public static void validate(PageModel page, Map<Long, CompositionLink> links) {
        var depths = new HashMap<Long, Integer>();
        for (long node : page.nodes().keySet()) {
            if (depths.containsKey(node)) continue;
            var path = new ArrayDeque<CompositionLink>();
            var seen = new HashSet<Long>();
            long current = node;
            while (!depths.containsKey(current) && links.containsKey(current)) {
                if (!seen.add(current))
                    throw CommandValidator.failure(COMPOSITION_CONFLICT, "Composition contains a cycle");
                var link = links.get(current);
                if (!page.nodes().containsKey(link.parent().nodeId()))
                    throw CommandValidator.failure(COMPOSITION_CONFLICT, "Dangling composition parent");
                path.push(link);
                current = link.parent().nodeId();
            }
            int depth = depths.getOrDefault(current, 0);
            depths.put(current, depth);
            while (!path.isEmpty()) {
                var link = path.pop();
                depth += link.semanticIncrement();
                if (depth > page.type().maximumNesting())
                    throw CommandValidator.failure(NESTING_LIMIT, "Page semantic nesting exceeds " + page.type().maximumNesting());
                depths.put(link.child().nodeId(), depth);
            }
        }
    }
}
