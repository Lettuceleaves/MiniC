package craken.visualization.navigation;

import craken.visualization.api.*;
import java.util.*;

public record NavigationFrame(List<PageOccurrence> occurrences) {
    public NavigationFrame { occurrences = List.copyOf(occurrences); }
    public record PageOccurrence(PageRef page, ViewLocation node, Set<ViewLocation> highlights) {
        public PageOccurrence { Objects.requireNonNull(page); highlights = Set.copyOf(highlights); }
    }
}
