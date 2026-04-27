package minic.source;

import java.util.Objects;

public record SourceRange(SourceFile sourceFile, int startOffset, int endOffset) {
    public SourceRange {
        Objects.requireNonNull(sourceFile, "sourceFile");
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must be non-negative");
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset must be greater than or equal to startOffset");
        }
        if (endOffset > sourceFile.content().length()) {
            throw new IllegalArgumentException("endOffset out of bounds: " + endOffset);
        }
    }

    public SourcePosition startPosition() {
        return sourceFile.positionAt(startOffset);
    }

    public SourcePosition endPosition() {
        return sourceFile.positionAt(endOffset);
    }

    public String text() {
        return sourceFile.content().substring(startOffset, endOffset);
    }
}
