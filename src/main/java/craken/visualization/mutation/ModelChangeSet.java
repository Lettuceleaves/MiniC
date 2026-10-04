package craken.visualization.mutation;

import craken.visualization.api.PageRef;
import java.util.Set;

public record ModelChangeSet(long version, Set<PageRef> pages, String sourceStep) {
    public ModelChangeSet { pages = Set.copyOf(pages); }
}
