package minic.compiler.parser.node;

import minic.SourceRange;
import java.util.List;
import java.util.Objects;

/** Source-level name path. It is not a resolved symbol or an emitted linker name. */
public record QualifiedName(boolean global, List<String> segments, SourceRange range) implements AstNode {
    public QualifiedName {
        segments = List.copyOf(segments);
        Objects.requireNonNull(range, "range");
        if (segments.isEmpty() || segments.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("A qualified name requires nonempty segments");
        }
    }
}
