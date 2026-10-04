package craken.visualization.model;

import craken.visualization.api.ViewLocation;
import craken.visualization.style.ColorSpec;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable model node. Custom implementations return copies and never retain GUI objects. */
public abstract class ViewNode {
    public enum Kind { POINT, ARRAY, LINKED, TREE, GRAPH }
    public enum Retention { ROOT, OWNED }
    public record Spec(Kind kind, String label, Map<String, String> fields, ColorSpec color) {
        public Spec {
            Objects.requireNonNull(kind, "kind");
            label = Objects.requireNonNullElse(label, "");
            fields = Map.copyOf(fields);
        }
        public Spec(Kind kind, String label, Map<String, String> fields) { this(kind, label, fields, null); }
        public Spec(Kind kind, String label) { this(kind, label, Map.of(), null); }
        public static Spec point(String label) { return new Spec(Kind.POINT, label); }
    }
    private final ViewLocation location;
    private final Spec content;
    private final Retention retention;
    private final ParentSelection parents;
    private final List<ViewLocation> children;

    protected ViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents) {
        this(location, content, retention, parents, List.of());
    }
    protected ViewNode(ViewLocation location, Spec content, Retention retention, ParentSelection parents,
                       List<ViewLocation> children) {
        this.location = Objects.requireNonNull(location);
        this.content = Objects.requireNonNull(content);
        this.retention = Objects.requireNonNull(retention);
        this.parents = Objects.requireNonNull(parents);
        this.children = List.copyOf(children);
    }
    public final ViewLocation location() { return location; }
    public final Spec content() { return content; }
    public final Retention retention() { return retention; }
    public final ParentSelection parents() { return parents; }
    public final List<ViewLocation> children() { return children; }
    public Set<ViewLocation> highlights() { return Set.of(location); }
    public abstract ViewNode withState(Spec content, ParentSelection parents);
    public abstract ViewNode withChildren(List<ViewLocation> children);
    public final ViewNode copy() { return withState(content, parents); }
}
