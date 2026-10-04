package craken.visualization.model;

import java.util.Set;

/** A weak component, ordered by its smallest member ID rather than a reusable identity. */
public record Part(long id, Set<Long> members) {
    public Part { members = Set.copyOf(members); }
}
