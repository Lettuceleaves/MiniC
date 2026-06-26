package minic.uilocal.text;

import java.util.List;
import java.util.Set;

/**
 * Lightweight highlighter for MiniC IR text rows.
 */
public final class MiniCIrTextHighlighter {
    private static final Set<String> DIRECTIVES = Set.of("function", "block", "declare");
    private static final Set<String> OPERATIONS = Set.of(
            "check_initialized", "address_of",
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
        if (DIRECTIVES.contains(normalized)) {
            return MiniCTextStyleRole.CODE_DIRECTIVE;
        }
        if (OPERATIONS.contains(normalized)) {
            return MiniCTextStyleRole.CODE_KEYWORD;
        }
        if (isNumber(token)) {
            return MiniCTextStyleRole.CODE_LITERAL;
        }
        if (startsLabel(token, startOffset, fullLine) || token.startsWith(".") || token.startsWith("$")) {
            return MiniCTextStyleRole.CODE_LABEL;
        }
        if (token.startsWith("@") || followsCallTarget(startOffset, fullLine)) {
            return MiniCTextStyleRole.CODE_FUNCTION;
        }
        if (token.startsWith("%") || token.startsWith("&") || isIdentifier(token)) {
            return MiniCTextStyleRole.CODE_VARIABLE;
        }
        if (isPunctuation(token)) {
            return MiniCTextStyleRole.CODE_PUNCTUATION;
        }
        return MiniCTextStyleRole.CODE_OPERATOR;
    }

    private boolean isIdentifier(String token) {
        return token.matches("[A-Za-z_@][A-Za-z0-9_.$]*");
    }

    private boolean isNumber(String token) {
        return token.matches("[-+]?0x[0-9A-Fa-f]+|[-+]?[0-9]+(?:\\.[0-9]+)?");
    }

    private boolean startsLabel(String token, int startOffset, String fullLine) {
        int index = startOffset + token.length();
        return index < fullLine.length() && fullLine.charAt(index) == ':';
    }

    private boolean followsCallTarget(int startOffset, String fullLine) {
        String prefix = fullLine.substring(0, Math.max(0, startOffset)).stripTrailing().toLowerCase();
        return prefix.endsWith("call") || prefix.endsWith("call*") || prefix.endsWith("declare");
    }

    private boolean isPunctuation(String token) {
        return token.length() == 1 && "(),:[]{}".contains(token);
    }
}
