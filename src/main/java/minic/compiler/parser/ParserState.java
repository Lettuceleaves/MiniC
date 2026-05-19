package minic.compiler.parser;

import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;

final class ParserState {
    private final List<Token> tokens;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final ParserTrace trace;
    private int currentIndex;

    ParserState(List<Token> tokens) {
        this(tokens, null);
    }

    ParserState(List<Token> tokens, ParserTrace trace) {
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("tokens must contain EOF");
        }
        this.tokens = tokens;
        this.trace = trace;
    }

    List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    List<Token> tokens() {
        return tokens;
    }

    int currentIndex() {
        return currentIndex;
    }

    boolean match(TokenKind kind) {
        if (!check(kind)) {
            return false;
        }
        advance();
        return true;
    }

    Token consume(TokenKind kind, String message) {
        if (check(kind)) {
            return advance();
        }
        report(peek(), message);
        return null;
    }

    boolean check(TokenKind kind) {
        return peek().kind() == kind;
    }

    Token advance() {
        if (!isAtEnd()) {
            currentIndex++;
        }
        Token token = previous();
        if (trace != null) {
            trace.consume(token);
        }
        return token;
    }

    boolean isAtEnd() {
        return peek().kind() == TokenKind.EOF;
    }

    Token peek() {
        return tokens.get(currentIndex);
    }

    Token peekAt(int offset) {
        int index = currentIndex + offset;
        if (index >= tokens.size()) {
            return tokens.getLast();
        }
        return tokens.get(index);
    }

    Token previous() {
        return tokens.get(currentIndex - 1);
    }

    void report(Token token, String message) {
        diagnostics.add(new Diagnostic("PAR001", DiagnosticSeverity.ERROR, message, token.range()));
    }

    void report(SourceRange range, String message) {
        diagnostics.add(new Diagnostic("PAR001", DiagnosticSeverity.ERROR, message, range));
    }

    void enter(String rule) {
        if (trace != null) {
            trace.enter(rule, peek().range());
        }
    }

    void exit(String rule, minic.source.SourceRange range) {
        if (trace != null) {
            trace.exit(rule, range);
        }
    }

    void build(Object node, String label, minic.source.SourceRange range) {
        if (trace != null) {
            trace.build(node, label, range);
        }
    }

    void synchronizeFunction() {
        if (isAtEnd()) {
            return;
        }
        advance();
        while (!isAtEnd()
                && previous().kind() != TokenKind.RIGHT_BRACE
                && previous().kind() != TokenKind.SEMICOLON) {
            advance();
        }
    }

    void synchronizeStatement() {
        while (!isAtEnd() && !check(TokenKind.SEMICOLON) && !check(TokenKind.RIGHT_BRACE)) {
            advance();
        }
        match(TokenKind.SEMICOLON);
    }
}
