package minic.source;

import java.util.Objects;

public record SourceFile(String path, String content) {
    public SourceFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
    }

    public SourcePosition positionAt(int offset) {
        if (offset < 0 || offset > content.length()) {
            throw new IllegalArgumentException("offset out of bounds: " + offset);
        }

        int line = 1;
        int lineStartOffset = 0;
        for (int index = 0; index < offset; index++) {
            if (content.charAt(index) == '\n') {
                line++;
                lineStartOffset = index + 1;
            }
        }

        return new SourcePosition(offset, line, offset - lineStartOffset + 1);
    }
}
