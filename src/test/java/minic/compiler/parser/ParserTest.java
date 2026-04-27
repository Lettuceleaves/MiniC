package minic.compiler.parser;

import minic.compiler.ast.FunctionDecl;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParserTest {
    @Test
    void parsesEmptyProgram() {
        SourceFile sourceFile = new SourceFile("empty.mc", "");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.program().functions()).isEmpty();
        assertThat(result.program().range()).isEqualTo(new SourceRange(sourceFile, 0, 0));
    }

    @Test
    void parsesFunctionWithoutParameters() {
        SourceFile sourceFile = new SourceFile("main.mc", "int main() {}");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.program().functions()).hasSize(1);
        FunctionDecl functionDecl = result.program().functions().getFirst();
        assertThat(functionDecl.name()).isEqualTo("main");
        assertThat(functionDecl.parameters()).isEmpty();
        assertThat(functionDecl.range()).isEqualTo(new SourceRange(sourceFile, 0, 13));
    }

    @Test
    void parsesFunctionParameters() {
        SourceFile sourceFile = new SourceFile("add.mc", "int add(int left, int right) {}");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        FunctionDecl functionDecl = result.program().functions().getFirst();
        assertThat(functionDecl.name()).isEqualTo("add");
        assertThat(functionDecl.parameters())
                .extracting(parameter -> parameter.name())
                .containsExactly("left", "right");
        assertThat(functionDecl.parameters().get(0).range()).isEqualTo(new SourceRange(sourceFile, 8, 16));
        assertThat(functionDecl.parameters().get(1).range()).isEqualTo(new SourceRange(sourceFile, 18, 27));
    }

    @Test
    void reportsSyntaxErrorsAsDiagnostics() {
        SourceFile sourceFile = new SourceFile("invalid.mc", "main() {}");

        ParseResult result = parse(sourceFile);

        assertThat(result.program().functions()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst().code()).isEqualTo("PAR001");
        assertThat(result.diagnostics().getFirst().severity()).isEqualTo(DiagnosticSeverity.ERROR);
        assertThat(result.diagnostics().getFirst().range()).isEqualTo(new SourceRange(sourceFile, 0, 4));
    }

    private ParseResult parse(SourceFile sourceFile) {
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        return new Parser(lexResult.tokens()).parse();
    }
}
