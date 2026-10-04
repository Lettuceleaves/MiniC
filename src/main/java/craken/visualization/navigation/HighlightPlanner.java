package craken.visualization.navigation;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.ContainerModel;
import java.util.*;

/** Highlights belong to an occurrence's selected node, never to all copies of its page. */
public final class HighlightPlanner {
    private HighlightPlanner() {}
    public static Map<ViewLocation,Set<ViewLocation>> plan(ContainerModel model) {
        var target=model.interaction().accessed();
        if (target==null) return Map.of();
        var chain=model.interaction().options().propagateHighlight()?NavigationResolver.chain(model,target):List.of(target);
        var result=new LinkedHashMap<ViewLocation,Set<ViewLocation>>();
        for (var location:chain) {
            var highlights=model.node(location).highlights();
            for (var h:highlights) if (!h.page().equals(location.page())) throw new IllegalArgumentException("Highlights must belong to their occurrence page");
            result.put(location,Set.copyOf(highlights));
        }
        return Collections.unmodifiableMap(result);
    }
}
