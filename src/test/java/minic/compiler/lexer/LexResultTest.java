package minic.compiler.lexer;

import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class LexResultTest {
    @Test
    void storesImmutableTokensAndDiagnostics() {
        SourceFile sourceFile = new SourceFile("result.mc", "@");
        SourceRange range = new SourceRange(sourceFile, 0, 1);
        Token token = new Token(TokenKind.EOF, "", new SourceRange(sourceFile, 1, 1));
        Diagnostic diagnostic = new Diagnostic(
                "LEX001",
                DiagnosticSeverity.ERROR,
                "非法字符",
                range
        );
        ArrayList<Token> tokens = new ArrayList<>();
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();

        LexResult result = new LexResult(tokens, diagnostics);
        tokens.add(token);
        diagnostics.add(diagnostic);

        assertThat(result.tokens()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }
}
