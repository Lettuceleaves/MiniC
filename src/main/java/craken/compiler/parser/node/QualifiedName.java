package craken.compiler.parser.node;

import craken.SourceRange;
import java.util.List;
import java.util.Objects;

/** Source-level name path. It is not a resolved symbol or an emitted linker name. */
public final class QualifiedName extends AbstractAstNode implements AstNode {
    private final boolean global;
    private final List<String> segments;
    private final SourceRange range;

    @AstNodeConstructor({"global", "segments", "range"})
    public QualifiedName(boolean global, List<String> segments, SourceRange range) {
        segments = List.copyOf(segments);
        Objects.requireNonNull(range, "range");
        if (segments.isEmpty() || segments.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("A qualified name requires nonempty segments");
        }

        this.global = global;
        this.segments = segments;
        this.range = range;
    }

    public boolean global() { return global; }
    public List<String> segments() { return segments; }
    public SourceRange range() { return range; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QualifiedName that)) return false;
        return global == that.global
                && Objects.equals(segments, that.segments)
                && Objects.equals(range, that.range);
    }

    @Override public int hashCode() {
        int result = 0;
        result = 31 * result + Boolean.hashCode(global);
        result = 31 * result + Objects.hashCode(segments);
        result = 31 * result + Objects.hashCode(range);
        return result;
    }

    @Override public String toString() {
        return "QualifiedName[global=" + global + ", segments=" + segments + ", range=" + range + "]";
    }
}
