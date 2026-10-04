package craken.visualization.navigation;

import craken.visualization.model.ContainerModel;
import craken.visualization.api.ViewLocation;
import java.util.*;

public final class NavigationResolver {
    private NavigationResolver() {}
    public static List<ViewLocation> chain(ContainerModel model,ViewLocation start) {
        var path=new ArrayList<ViewLocation>(); var visited=new HashSet<ViewLocation>();
        for (var at=start; at!=null; ) {
            if (!visited.add(at)) throw new IllegalArgumentException("Corrupt cyclic navigation path");
            path.add(at); at=model.node(at).parents().selected();
        }
        return List.copyOf(path);
    }
    public static NavigationFrame resolve(ContainerModel model) {
        if (model.focus()==null) return new NavigationFrame(model.root()==null?List.of():List.of(new NavigationFrame.PageOccurrence(model.root(),null,Set.of())));
        var highlights=HighlightPlanner.plan(model);
        var nodes=new ArrayList<>(chain(model,model.focus())); Collections.reverse(nodes);
        return new NavigationFrame(nodes.stream().map(n->new NavigationFrame.PageOccurrence(n.page(),n,highlights.getOrDefault(n,Set.of()))).toList());
    }
}
