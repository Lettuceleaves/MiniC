package craken.visualization.navigation;

import craken.visualization.api.ViewLocation;
import craken.visualization.api.PageRef;
import craken.visualization.model.ContainerModel;
import java.util.*;

/** Access highlights are assigned to one displayed occurrence, never broadcast to every copy of its page. */
public final class HighlightPlanner {
    private HighlightPlanner() {}
    public static Map<ViewLocation,Set<ViewLocation>> plan(ContainerModel model) {
        var displayed = new ArrayList<>(NavigationResolver.chain(model, model.focus())); Collections.reverse(displayed);
        return plan(model, displayed);
    }
    /** A null key denotes the initialized root occurrence when automatic navigation has not selected a node. */
    static Map<ViewLocation,Set<ViewLocation>> plan(ContainerModel model, List<ViewLocation> displayed) {
        var target=model.interaction().accessed();
        if (target==null) return Collections.emptyMap();
        var chain = new ArrayList<>(NavigationResolver.chain(model, target)); Collections.reverse(chain);
        var positions = new HashMap<PageRef, List<Integer>>(); var exact = new HashSet<ViewLocation>();
        for (int index = 0; index < displayed.size(); index++) {
            var node = displayed.get(index); exact.add(node);
            positions.computeIfAbsent(node.page(), ignored -> new ArrayList<>()).add(index);
        }
        int shared = 0;
        while (shared < Math.min(chain.size(), displayed.size()) && chain.get(shared).equals(displayed.get(shared))) shared++;
        var result=new LinkedHashMap<ViewLocation,Set<ViewLocation>>();
        int first = model.interaction().options().propagateHighlight() ? 0 : chain.size() - 1;
        for (int depth = first; depth < chain.size(); depth++) {
            var location = chain.get(depth); ViewLocation occurrence;
            if (exact.contains(location)) occurrence = location;
            else if (displayed.isEmpty() && location.page().equals(model.root())) occurrence = null;
            else {
                var candidates = positions.get(location.page());
                if (candidates == null) continue; // An unseen page must not be inserted by an access.
                occurrence = displayed.get(closestContext(candidates, shared, depth));
            }
            var highlights=model.node(location).highlights();
            for (var h:highlights) if (!h.page().equals(location.page())) throw new IllegalArgumentException("Highlights must belong to their occurrence page");
            result.computeIfAbsent(occurrence, ignored -> new LinkedHashSet<>()).addAll(highlights);
        }
        result.replaceAll((occurrence, values) -> Set.copyOf(values));
        return Collections.unmodifiableMap(result);
    }
    private static int closestContext(List<Integer> candidates, int shared, int depth) {
        // Each selected-parent chain is unique. Once two paths diverge they cannot rejoin;
        // a single common-prefix calculation therefore ranks every ancestor context.
        int first = lowerBound(candidates, shared);
        if (first == candidates.size()) return candidates.getLast();
        int next = Math.max(first, lowerBound(candidates, depth));
        if (next == candidates.size()) return candidates.getLast();
        if (next == first) return candidates.get(next);
        int before = candidates.get(next - 1), after = candidates.get(next);
        return depth - before < after - depth ? before : after;
    }
    private static int lowerBound(List<Integer> values, int target) {
        int low = 0, high = values.size();
        while (low < high) { int middle = (low + high) >>> 1; if (values.get(middle) < target) low = middle + 1; else high = middle; }
        return low;
    }
}
