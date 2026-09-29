package minic.compiler.lexer;

import minic.compiler.SourceFile;
import minic.compiler.lexer.token.TokenType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void recognizesAllSupportedCNumericFormsAndRejectsInvalidRadixDigits() {
        var valid = new Lexer(new SourceFile("numbers.mc", """
                0 077 0xff 0b1010 1'000 0xFF'00 42u 42UL 42llu
                .5 1. 1e-3 0x1.fp+3 1'2.5'0e1
                """)).lex();
        assertTrue(valid.diagnostics().isEmpty(), () -> valid.diagnostics().toString());
        assertEquals(14, valid.tokens().size() - 1);
        assertTrue(valid.tokens().stream().anyMatch(token -> token.lexeme().equals("0x1.fp+3")
                && token.type() == TokenType.DOUBLE_LITERAL));

        var invalid = new Lexer(new SourceFile("bad-numbers.mc", "0b102 0x1g 09")).lex();
        assertFalse(invalid.diagnostics().isEmpty());
    }
}
