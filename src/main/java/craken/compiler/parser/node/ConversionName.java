package craken.compiler.parser.node;

import craken.SourceRange;
import craken.compiler.type.CrakenType;
import java.util.Objects;

/** Source-only conversion-function-id; range includes operator and the complete written type-id. */
public record ConversionName(CrakenType targetType, boolean explicitSpecifier, SourceRange range) {
    public ConversionName {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(range, "range");
    }

    /** Resolved type identity is separate from the source spelling retained by range. */
    public String spelling() { return "operator " + targetType; }
}
