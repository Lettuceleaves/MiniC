package minic.ui.text;

import java.util.List;
import java.util.Set;

/**
 * Lightweight highlighter for explanatory prose that embeds small code fragments.
 */
public final class MiniCExplanationTextHighlighter {
    private static final Set<String> KEYWORDS = Set.of(
            "auto", "break", "case", "char", "const", "continue", "default", "do",
            "double", "else", "enum", "extern", "float", "for", "goto", "if",
            "int", "long", "return", "short", "signed", "sizeof", "static",
            "struct", "switch", "typedef", "union", "unsigned", "void", "while",
            "load", "store", "call", "branch", "jump"
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
        if (token.startsWith("%") || isIdentifier(token)) {
            return MiniCTextStyleRole.CODE_IDENTIFIER;
        }
        if (isAsciiOperator(token)) {
            return MiniCTextStyleRole.CODE_OPERATOR;
        }
        return MiniCTextStyleRole.BODY;
    }

    private boolean isIdentifier(String token) {
        return token.matches("[A-Za-z_][A-Za-z0-9_.$]*");
    }

    private boolean isNumber(String token) {
        return token.matches("[-+]?0x[0-9A-Fa-f]+|[-+]?[0-9]+(?:\\.[0-9]+)?");
    }

    private boolean isAsciiOperator(String token) {
        return token.matches("[!%&*+\\-/<=>|~^:;,().\\[\\]{}]+");
    }
}
