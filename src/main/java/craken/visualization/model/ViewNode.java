package craken.visualization.model;

import craken.visualization.api.ViewLocation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable model node. Custom implementations return copies and never retain GUI objects. */
public abstract class ViewNode {
    public enum Kind { POINT, ARRAY, LINKED, TREE, GRAPH }
    public enum Retention { ROOT, OWNED }
    public record Spec(Kind kind, String label, Map<String, String> fields) {
        public Spec {
            Objects.requireNonNull(kind, "kind");
            label = Objects.requireNonNullElse(label, "");
            fields = Map.copyOf(fields);
        }
        public Spec(Kind kind, String label) { this(kind, label, Map.of()); }
        public static Spec point(String label) { return new Spec(Kind.POINT, label); }
    }
    private final ViewLocation location;
    private final Spec content;
    private final Retention retention;
    private final ParentSelection parents;

    protected ViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents) {
        this.location = Objects.requireNonNull(location);
        this.content = Objects.requireNonNull(content);
        this.retention = Objects.requireNonNull(retention);
        this.parents = Objects.requireNonNull(parents);
    }
    public final ViewLocation location() { return location; }
    public final Spec content() { return content; }
    public final Retention retention() { return retention; }
    public final ParentSelection parents() { return parents; }
    public List<ViewLocation> children() { return List.of(); }
    public Set<ViewLocation> highlights() { return Set.of(location); }
    public abstract ViewNode withState(Spec content, ParentSelection parents);
    public final ViewNode copy() { return withState(content, parents); }
}
