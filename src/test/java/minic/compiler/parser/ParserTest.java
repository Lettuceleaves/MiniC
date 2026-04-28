package minic.compiler.parser;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.lexer.TokenKind;
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
        assertThat(varDeclStmt.initializerOptional()).isPresent();
        assertThat(varDeclStmt.initializerOptional().get().range()).isEqualTo(new SourceRange(sourceFile, 21, 22));
        assertThat(varDeclStmt.range()).isEqualTo(new SourceRange(sourceFile, 13, 23));

        ReturnStmt returnStmt = (ReturnStmt) body.statements().get(1);
        assertThat(returnStmt.expressionOptional()).isPresent();
        assertThat(returnStmt.expressionOptional().get().range()).isEqualTo(new SourceRange(sourceFile, 31, 32));
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
        assertThat(exprStmt.expression().range()).isEqualTo(new SourceRange(sourceFile, 15, 20));
        assertThat(exprStmt.range()).isEqualTo(new SourceRange(sourceFile, 15, 21));
    }

    @Test
    void parsesBinaryPrecedence() {
        SourceFile sourceFile = new SourceFile("precedence.mc", "int main() { return 1 + 2 * 3; }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        ReturnStmt returnStmt = (ReturnStmt) result.program().functions().getFirst().body().statements().getFirst();
        BinaryExpr plus = (BinaryExpr) returnStmt.expressionOptional().orElseThrow();
        BinaryExpr multiply = (BinaryExpr) plus.right();

        assertThat(plus.operator()).isEqualTo(TokenKind.PLUS);
        assertThat(((IntegerLiteralExpr) plus.left()).value()).isEqualTo(1);
        assertThat(multiply.operator()).isEqualTo(TokenKind.STAR);
        assertThat(((IntegerLiteralExpr) multiply.left()).value()).isEqualTo(2);
        assertThat(((IntegerLiteralExpr) multiply.right()).value()).isEqualTo(3);
    }

    @Test
    void parsesRightAssociativeAssignment() {
        SourceFile sourceFile = new SourceFile("assignment.mc", "int main() { a = b = 1; }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        ExprStmt exprStmt = (ExprStmt) result.program().functions().getFirst().body().statements().getFirst();
        AssignmentExpr leftAssignment = (AssignmentExpr) exprStmt.expression();
        AssignmentExpr rightAssignment = (AssignmentExpr) leftAssignment.value();

        assertThat(leftAssignment.targetName()).isEqualTo("a");
        assertThat(rightAssignment.targetName()).isEqualTo("b");
        assertThat(((IntegerLiteralExpr) rightAssignment.value()).value()).isEqualTo(1);
    }

    @Test
    void parsesGroupingAndFunctionCallArguments() {
        SourceFile sourceFile = new SourceFile("call.mc", "int main() { return add(1, (x + 2)); }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        ReturnStmt returnStmt = (ReturnStmt) result.program().functions().getFirst().body().statements().getFirst();
        CallExpr callExpr = (CallExpr) returnStmt.expressionOptional().orElseThrow();
        GroupingExpr groupingExpr = (GroupingExpr) callExpr.arguments().get(1);
        BinaryExpr groupedBinary = (BinaryExpr) groupingExpr.expression();

        assertThat(callExpr.calleeName()).isEqualTo("add");
        assertThat(callExpr.arguments()).hasSize(2);
        assertThat(((IntegerLiteralExpr) callExpr.arguments().getFirst()).value()).isEqualTo(1);
        assertThat(((NameExpr) groupedBinary.left()).name()).isEqualTo("x");
        assertThat(groupedBinary.operator()).isEqualTo(TokenKind.PLUS);
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
