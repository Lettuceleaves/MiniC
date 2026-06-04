package minic.ui.text;

import java.util.Collection;
import java.util.List;

/**
 * Maps lexer token kinds to reusable text style roles.
 */
public final class MiniCSyntaxTextStyleMapper {
    private final MiniCTextStyleResolver resolver;

    public MiniCSyntaxTextStyleMapper() {
        this(MiniCTextStyles.defaultResolver());
    }

    public MiniCSyntaxTextStyleMapper(MiniCTextStyleResolver resolver) {
        this.resolver = resolver;
    }

    public MiniCTextStyleRole roleFor(String tokenKind) {
        return switch (tokenKind) {
            case "BOOL", "CHAR", "INT", "LONG", "FLOAT", "DOUBLE", "EXTERN", "STRUCT",
                    "RETURN", "IF", "ELSE", "WHILE", "FOR", "BREAK", "CONTINUE" -> MiniCTextStyleRole.CODE_KEYWORD;
            case "STRING_LITERAL", "CHAR_LITERAL" -> MiniCTextStyleRole.CODE_STRING;
            case "INTEGER_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL",
                    "BOOL_LITERAL", "NULL_LITERAL" -> MiniCTextStyleRole.CODE_LITERAL;
            case "IDENTIFIER" -> MiniCTextStyleRole.CODE_IDENTIFIER;
            default -> MiniCTextStyleRole.CODE_OPERATOR;
        };
    }

    public Collection<MiniCTextStyleState> statesFor(boolean diagnostic) {
        return diagnostic ? List.of(MiniCTextStyleState.DIAGNOSTIC) : List.of();
    }

    public Collection<String> styleClassesFor(String tokenKind, boolean diagnostic) {
        return resolver.styleClasses(roleFor(tokenKind), statesFor(diagnostic));
    }
}
