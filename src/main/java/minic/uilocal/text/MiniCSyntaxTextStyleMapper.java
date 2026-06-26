package minic.uilocal.text;

import minic.uiapi.UiLexerTokenVisualDto;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Maps lexer token kinds to reusable text style roles.
 */
public final class MiniCSyntaxTextStyleMapper {
    private static final Set<String> TYPE_KINDS = Set.of(
            "BOOL", "CHAR", "INT", "LONG", "FLOAT", "DOUBLE", "STRUCT"
    );
    private static final Set<String> CONTROL_KINDS = Set.of(
            "RETURN", "IF", "ELSE", "WHILE", "DO", "FOR", "BREAK", "CONTINUE",
            "SWITCH", "CASE", "DEFAULT"
    );
    private static final Set<String> KEYWORD_KINDS = Set.of(
            "EXTERN", "SIZEOF"
    );
    private static final Set<String> STRING_KINDS = Set.of(
            "STRING_LITERAL", "CHAR_LITERAL"
    );
    private static final Set<String> LITERAL_KINDS = Set.of(
            "INTEGER_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL",
            "BOOL_LITERAL", "NULL_LITERAL"
    );
    private static final Set<String> PUNCTUATION_KINDS = Set.of(
            "LEFT_PAREN", "RIGHT_PAREN", "LEFT_BRACE", "RIGHT_BRACE",
            "LEFT_BRACKET", "RIGHT_BRACKET", "SEMICOLON", "COMMA", "DOT",
            "ELLIPSIS", "QUESTION", "COLON"
    );

    private final MiniCTextStyleResolver resolver;

    public MiniCSyntaxTextStyleMapper() {
        this(MiniCTextStyles.defaultResolver());
    }

    public MiniCSyntaxTextStyleMapper(MiniCTextStyleResolver resolver) {
        this.resolver = resolver;
    }

    public MiniCTextStyleRole roleFor(String tokenKind) {
        if (TYPE_KINDS.contains(tokenKind) || CONTROL_KINDS.contains(tokenKind) || KEYWORD_KINDS.contains(tokenKind)) {
            return MiniCTextStyleRole.CODE_KEYWORD;
        }
        if (STRING_KINDS.contains(tokenKind)) {
            return MiniCTextStyleRole.CODE_STRING;
        }
        if (LITERAL_KINDS.contains(tokenKind)) {
            return MiniCTextStyleRole.CODE_LITERAL;
        }
        if ("IDENTIFIER".equals(tokenKind)) {
            return MiniCTextStyleRole.CODE_IDENTIFIER;
        }
        return MiniCTextStyleRole.CODE_OPERATOR;
    }

    public MiniCTextStyleRole roleForToken(List<UiLexerTokenVisualDto> tokens, int index) {
        if (index < 0 || index >= tokens.size()) {
            return MiniCTextStyleRole.CODE_PLAIN;
        }
        UiLexerTokenVisualDto token = tokens.get(index);
        String kind = token.kind();
        if (TYPE_KINDS.contains(kind)) {
            return MiniCTextStyleRole.CODE_TYPE;
        }
        if (CONTROL_KINDS.contains(kind)) {
            return MiniCTextStyleRole.CODE_CONTROL;
        }
        if (KEYWORD_KINDS.contains(kind)) {
            return MiniCTextStyleRole.CODE_KEYWORD;
        }
        if (STRING_KINDS.contains(kind)) {
            return MiniCTextStyleRole.CODE_STRING;
        }
        if (LITERAL_KINDS.contains(kind)) {
            return MiniCTextStyleRole.CODE_LITERAL;
        }
        if ("IDENTIFIER".equals(kind)) {
            return roleForIdentifier(tokens, index);
        }
        if (PUNCTUATION_KINDS.contains(kind)) {
            return MiniCTextStyleRole.CODE_PUNCTUATION;
        }
        if ("EOF".equals(kind)) {
            return MiniCTextStyleRole.CODE_PLAIN;
        }
        return MiniCTextStyleRole.CODE_OPERATOR;
    }

    private MiniCTextStyleRole roleForIdentifier(List<UiLexerTokenVisualDto> tokens, int index) {
        String previousKind = kindAt(tokens, index - 1);
        String nextKind = kindAt(tokens, index + 1);
        if ("STRUCT".equals(previousKind)) {
            return MiniCTextStyleRole.CODE_TYPE;
        }
        if ("LEFT_PAREN".equals(nextKind)) {
            return MiniCTextStyleRole.CODE_FUNCTION;
        }
        return MiniCTextStyleRole.CODE_VARIABLE;
    }

    private String kindAt(List<UiLexerTokenVisualDto> tokens, int index) {
        if (index < 0 || index >= tokens.size()) {
            return "";
        }
        return tokens.get(index).kind();
    }

    public Collection<MiniCTextStyleState> statesFor(boolean diagnostic) {
        return diagnostic ? List.of(MiniCTextStyleState.DIAGNOSTIC) : List.of();
    }

    public Collection<String> styleClassesFor(String tokenKind, boolean diagnostic) {
        return resolver.styleClasses(roleFor(tokenKind), statesFor(diagnostic));
    }

    public Collection<String> styleClassesForToken(
            List<UiLexerTokenVisualDto> tokens,
            int index,
            boolean diagnostic
    ) {
        return resolver.styleClasses(roleForToken(tokens, index), statesFor(diagnostic));
    }
}
