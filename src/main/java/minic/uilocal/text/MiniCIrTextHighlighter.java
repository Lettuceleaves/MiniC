package minic.uilocal.text;

import java.util.List;
import java.util.Set;

/**
 * Lightweight highlighter for MiniC IR text rows.
 */
public final class MiniCIrTextHighlighter {
    private static final Set<String> KEYWORDS = Set.of(
            "function", "block", "declare", "check_initialized", "address_of",
            "load", "store", "load_ptr", "store_ptr", "element_address", "field_address",
            "size", "offset", "check_nonzero", "cast", "to", "call", "call*",
            "branch", "jump", "return", "add", "subtract", "multiply", "divide",
            "modulo", "bitwise_and", "bitwise_or", "bitwise_xor", "shift_left",
            "shift_right", "logical_and", "logical_or", "equal", "not_equal",
            "less_than", "less_equal", "greater_than", "greater_equal"
    );

    public List<MiniCStyledTextSegment> highlight(String line) {
        return MiniCLineTokenHighlighter.highlight(line, this::roleFor);
    }

    private MiniCTextStyleRole roleFor(String token, int startOffset, String fullLine) {
        String normalized = token.toLowerCase();
        if (KEYWORDS.contains(normalized)) {
            return MiniCTextStyleRole.CODE_KEYWORD;
        }
        if (isNumber(token)) {
            return MiniCTextStyleRole.CODE_LITERAL;
        }
        if (token.startsWith("%") || token.startsWith("$") || token.startsWith("&") || isIdentifier(token)) {
            return MiniCTextStyleRole.CODE_IDENTIFIER;
        }
        return MiniCTextStyleRole.CODE_OPERATOR;
    }

    private boolean isIdentifier(String token) {
        return token.matches("[A-Za-z_][A-Za-z0-9_.$]*");
    }

    private boolean isNumber(String token) {
        return token.matches("[-+]?0x[0-9A-Fa-f]+|[-+]?[0-9]+(?:\\.[0-9]+)?");
    }
}
