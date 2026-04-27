package minic.compiler.parser;

import minic.compiler.ast.FunctionDecl;
import minic.compiler.ast.BlockStmt;
import minic.compiler.ast.ExprStmt;
import minic.compiler.ast.ReturnStmt;
import minic.compiler.ast.VarDeclStmt;
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
        assertThat(functionDecl.body().statements()).isEmpty();
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
    void parsesVariableDeclarationAndReturnStatements() {
        SourceFile sourceFile = new SourceFile("statements.mc", "int main() { int x = 1; return x; }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        BlockStmt body = result.program().functions().getFirst().body();
        assertThat(body.range()).isEqualTo(new SourceRange(sourceFile, 11, 35));
        assertThat(body.statements()).hasSize(2);

        VarDeclStmt varDeclStmt = (VarDeclStmt) body.statements().get(0);
        assertThat(varDeclStmt.name()).isEqualTo("x");
        assertThat(varDeclStmt.initializerRangeOptional())
                .contains(new SourceRange(sourceFile, 21, 22));
        assertThat(varDeclStmt.range()).isEqualTo(new SourceRange(sourceFile, 13, 23));

        ReturnStmt returnStmt = (ReturnStmt) body.statements().get(1);
        assertThat(returnStmt.expressionRangeOptional())
                .contains(new SourceRange(sourceFile, 31, 32));
        assertThat(returnStmt.range()).isEqualTo(new SourceRange(sourceFile, 24, 33));
    }

    @Test
    void parsesNestedBlockAndExpressionStatement() {
        SourceFile sourceFile = new SourceFile("nested.mc", "int main() { { value; } }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        BlockStmt outerBlock = result.program().functions().getFirst().body();
        BlockStmt innerBlock = (BlockStmt) outerBlock.statements().getFirst();
        ExprStmt exprStmt = (ExprStmt) innerBlock.statements().getFirst();

        assertThat(innerBlock.range()).isEqualTo(new SourceRange(sourceFile, 13, 23));
        assertThat(exprStmt.expressionRange()).isEqualTo(new SourceRange(sourceFile, 15, 20));
        assertThat(exprStmt.range()).isEqualTo(new SourceRange(sourceFile, 15, 21));
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
