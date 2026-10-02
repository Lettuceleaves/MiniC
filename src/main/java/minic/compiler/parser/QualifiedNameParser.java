package minic.compiler.parser;

import minic.SourceRange;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.node.Declaration.UsingDecl;
import minic.compiler.parser.node.QualifiedName;
import java.util.ArrayList;

/** Shared syntax reader only; name lookup remains a separate semantic operation. */
public final class QualifiedNameParser {
    private QualifiedNameParser() {}

    /** Lookahead for declaration/cast disambiguation; never consumes tokens or emits diagnostics. */
    public static QualifiedName peekName(Parser.Context state, int offset) {
        var start = state.peekAt(offset);
        boolean global = start.type() == TokenType.SCOPE;
        if (global) offset++;
        if (state.peekAt(offset).type() != TokenType.IDENTIFIER) return null;
        var segments = new ArrayList<String>();
        var end = state.peekAt(offset++);
        segments.add(end.lexeme());
        while (state.peekAt(offset).type() == TokenType.SCOPE) {
            offset++;
            if (state.peekAt(offset).type() != TokenType.IDENTIFIER) return null;
            end = state.peekAt(offset++);
            segments.add(end.lexeme());
        }
        return new QualifiedName(global, segments, SourceRange.span(start.range(), end.range()));
    }

    public static QualifiedName parseName(Parser.Context state) {
        var start = state.peek();
        boolean global = state.match(TokenType.SCOPE);
        var first = state.consume(TokenType.IDENTIFIER, "期望限定名称标识符");
        if (first == null) return null;
        var segments = new ArrayList<String>();
        segments.add(first.lexeme());
        var end = first;
        while (state.match(TokenType.SCOPE)) {
            end = state.consume(TokenType.IDENTIFIER, "期望 :: 后的标识符");
            if (end == null) return null;
            segments.add(end.lexeme());
        }
        return new QualifiedName(global, segments, SourceRange.span(start.range(), end.range()));
    }

    public static UsingDecl parseUsing(Parser.Context state) {
        var start = state.consume(TokenType.USING, "期望 using");
        boolean directive = state.match(TokenType.NAMESPACE);
        var target = parseName(state);
        if (state.check(TokenType.EQUAL)) {
            state.unsupportedSyntax(state.peek().range(), "using 类型别名尚未实现");
            return null;
        }
        var end = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (start == null || target == null || end == null) return null;
        if (!directive && !target.global() && target.segments().size() < 2) {
            state.report(target.range(), "using 声明必须包含限定名称");
            return null;
        }
        var declaration = new UsingDecl(target, directive, SourceRange.span(start.range(), end.range()));
        state.build(declaration, "UsingDecl", declaration.range());
        return declaration;
    }
}
