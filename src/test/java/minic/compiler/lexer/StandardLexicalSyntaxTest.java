package minic.compiler.lexer;

import minic.compiler.SourceFile;
import minic.compiler.lexer.token.TokenType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StandardLexicalSyntaxTest {
    @Test
    void skipsMultilineBlockCommentsAndRecognizesNewKeywords() {
        var result = new Lexer(new SourceFile("syntax.mc", """
                unsigned long long value; /* line one
                line two */ union Data { short item; }; enum Kind { A };
                """)).lex();

        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertEquals(
                java.util.List.of(
                        TokenType.UNSIGNED, TokenType.LONG, TokenType.LONG, TokenType.IDENTIFIER,
                        TokenType.SEMICOLON, TokenType.UNION, TokenType.IDENTIFIER,
                        TokenType.LEFT_BRACE, TokenType.SHORT, TokenType.IDENTIFIER,
                        TokenType.SEMICOLON, TokenType.RIGHT_BRACE, TokenType.SEMICOLON,
                        TokenType.ENUM, TokenType.IDENTIFIER, TokenType.LEFT_BRACE,
                        TokenType.IDENTIFIER, TokenType.RIGHT_BRACE, TokenType.SEMICOLON,
                        TokenType.EOF
                ),
                result.tokens().stream().map(token -> token.type()).toList()
        );
    }

    @Test
    void reportsUnterminatedBlockCommentOnce() {
        var result = new Lexer(new SourceFile("syntax.mc", "int value; /* never closed")).lex();

        assertEquals(1, result.diagnostics().size());
        assertEquals("LEX006", result.diagnostics().getFirst().code());
    }
}
