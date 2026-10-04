package craken.visualization.model;

import craken.visualization.api.ViewLocation;
import java.util.ArrayList;
import java.util.List;

/** Ordered effective parents. Reverse lookup indexes never create reverse ownership. */
public record ParentSelection(List<ViewLocation> parents, int index) {
    public static final ParentSelection ROOT = new ParentSelection(List.of(), -1);
    public ParentSelection {
        parents = List.copyOf(parents);
        if (parents.stream().distinct().count() != parents.size()
                || (parents.isEmpty() ? index != -1 : index < 0 || index >= parents.size()))
            throw new IllegalArgumentException("Invalid parent selection");
    }
    public ViewLocation selected() { return index < 0 ? null : parents.get(index); }
    public ParentSelection select(ViewLocation parent) {
        int selected = parents.indexOf(parent);
        if (selected < 0) throw new IllegalArgumentException("Not an effective parent");
        return new ParentSelection(parents, selected);
    }
    public ParentSelection add(ViewLocation parent) {
        var updated = new ArrayList<>(parents);
        if (!updated.contains(parent)) updated.add(parent);
        return new ParentSelection(updated, updated.indexOf(parent));
    }
    public ParentSelection remove(ViewLocation parent) {
        int removed = parents.indexOf(parent);
        if (removed < 0) return this;
        var updated = new ArrayList<>(parents);
        updated.remove(removed);
        if (updated.isEmpty()) return ROOT;
        int selected = removed < index ? index - 1 : removed == index ? index % updated.size() : index;
        return new ParentSelection(updated, selected);
    }
}
