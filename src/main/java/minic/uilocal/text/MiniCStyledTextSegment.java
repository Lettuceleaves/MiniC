package minic.uilocal.text;

import java.util.Objects;

/**
 * One styled run inside a rendered text line.
 */
public record MiniCStyledTextSegment(String text, MiniCTextStyleRole role) {
    public MiniCStyledTextSegment {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(role, "role");
    }
}
