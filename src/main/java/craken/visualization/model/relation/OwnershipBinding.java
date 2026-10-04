package craken.visualization.model.relation;

import craken.visualization.api.ViewLocation;
import java.util.Set;

public record OwnershipBinding(long id, Key key, Set<String> sources) {
    public record Key(ViewLocation pre, ViewLocation nxt) {}
    public OwnershipBinding { sources = Set.copyOf(sources); }
}
