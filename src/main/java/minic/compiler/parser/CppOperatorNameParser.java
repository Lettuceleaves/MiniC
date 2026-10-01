package minic.compiler.parser;

import minic.SourceRange;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.node.OperatorName;
import minic.compiler.parser.node.OperatorName.Kind;
import minic.compiler.parser.node.QualifiedName;

import java.util.ArrayList;

/** Declaration-name syntax only: this does not perform operator lookup or overload selection. */
public final class CppOperatorNameParser {
    private CppOperatorNameParser() { }

    public static OperatorName parse(Parser.Context state) {
        Token start = state.consume(TokenType.OPERATOR, "期望 operator");
        if (start == null) return null;
        Token token = state.peek();
        if (!state.isAtEnd()) state.advance();
        Kind kind = switch (token.type()) {
            case PLUS -> Kind.ADD; case MINUS -> Kind.SUBTRACT; case STAR -> Kind.MULTIPLY;
            case SLASH -> Kind.DIVIDE; case PERCENT -> Kind.REMAINDER; case CARET -> Kind.BIT_XOR;
            case AMPERSAND -> Kind.BIT_AND; case PIPE -> Kind.BIT_OR; case TILDE -> Kind.BIT_NOT;
            case BANG -> Kind.LOGICAL_NOT; case EQUAL -> Kind.ASSIGN; case LESS -> Kind.LESS;
            case GREATER -> Kind.GREATER; case PLUS_EQUAL -> Kind.ADD_ASSIGN; case MINUS_EQUAL -> Kind.SUBTRACT_ASSIGN;
            case STAR_EQUAL -> Kind.MULTIPLY_ASSIGN; case SLASH_EQUAL -> Kind.DIVIDE_ASSIGN; case PERCENT_EQUAL -> Kind.REMAINDER_ASSIGN;
            case CARET_EQUAL -> Kind.XOR_ASSIGN; case AMPERSAND_EQUAL -> Kind.AND_ASSIGN; case PIPE_EQUAL -> Kind.OR_ASSIGN;
            case LESS_LESS -> Kind.SHIFT_LEFT; case GREATER_GREATER -> Kind.SHIFT_RIGHT;
            case LESS_LESS_EQUAL -> Kind.SHIFT_LEFT_ASSIGN; case GREATER_GREATER_EQUAL -> Kind.SHIFT_RIGHT_ASSIGN;
            case EQUAL_EQUAL -> Kind.EQUAL; case BANG_EQUAL -> Kind.NOT_EQUAL; case LESS_EQUAL -> Kind.LESS_EQUAL;
            case GREATER_EQUAL -> Kind.GREATER_EQUAL; case AMPERSAND_AMPERSAND -> Kind.LOGICAL_AND;
            case PIPE_PIPE -> Kind.LOGICAL_OR; case PLUS_PLUS -> Kind.INCREMENT; case MINUS_MINUS -> Kind.DECREMENT;
            case COMMA -> Kind.COMMA; case ARROW -> Kind.MEMBER_ACCESS;
            case LEFT_PAREN -> Kind.CALL; case LEFT_BRACKET -> Kind.SUBSCRIPT;
            default -> null;
        };
        if (kind == null) {
            if (token.type() == TokenType.NEW || token.type() == TokenType.DELETE || token.type() == TokenType.STRING_LITERAL)
                state.unsupportedCpp(SourceRange.span(start.range(), token.range()), "分配和字面量运算符声明尚未实现");
            else state.report(token.range(), "期望可重载的运算符名称");
            return null;
        }
        Token end = token;
        if (kind == Kind.CALL || kind == Kind.SUBSCRIPT) {
            end = state.consume(kind == Kind.CALL ? TokenType.RIGHT_PAREN : TokenType.RIGHT_BRACKET, "运算符名称需要完整的 () 或 []");
            if (end == null) return null;
        } else if (kind == Kind.MEMBER_ACCESS && state.check(TokenType.STAR)
                && token.range().endLine() == state.peek().range().startLine()
                && token.range().endByte() == state.peek().range().startByte()) {
            // The core lexer uses ARROW and STAR; only adjacent spelling forms the ->* token.
            end = state.advance(); kind = Kind.POINTER_TO_MEMBER;
        }
        return new OperatorName(kind, SourceRange.span(start.range(), end.range()));
    }

    /** Keep the existing qualified identifier path, permitting an operator-function-id at its end. */
    public static ParsedName parseQualified(Parser.Context state) {
        Token start = state.peek();
        boolean global = state.match(TokenType.SCOPE);
        var segments = new ArrayList<String>();
        Token name;
        OperatorName operator = null;
        while (true) {
            if (state.check(TokenType.OPERATOR)) {
                operator = parse(state);
                if (operator == null) return null;
                name = new Token(TokenType.IDENTIFIER, operator.spelling(), operator.range());
            } else {
                name = state.consume(TokenType.IDENTIFIER, "期望限定声明名称");
                if (name == null) return null;
            }
            segments.add(name.lexeme());
            if (operator != null || !state.match(TokenType.SCOPE)) break;
        }
        return new ParsedName(new QualifiedName(global, segments, SourceRange.span(start.range(), name.range())), name, operator);
    }

    public record ParsedName(QualifiedName qualifiedName, Token nameToken, OperatorName operatorName) { }
}
