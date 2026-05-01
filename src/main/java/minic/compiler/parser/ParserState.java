package minic.compiler.parser;

import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;

import java.util.ArrayList;
import java.util.List;

final class ParserState {
    private final List<Token> tokens;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private int currentIndex;

    ParserState(List<Token> tokens) {
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("tokens must contain EOF");
        }
        this.tokens = tokens;
    }

    List<Diagnostic> diagnostics() {
        return diagnostics;
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
        return previous();
    }

    boolean isAtEnd() {
        return peek().kind() == TokenKind.EOF;
    }

    Token peek() {
        return tokens.get(currentIndex);
    }

    Token previous() {
        return tokens.get(currentIndex - 1);
    }

    void report(Token token, String message) {
        diagnostics.add(new Diagnostic("PAR001", DiagnosticSeverity.ERROR, message, token.range()));
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
