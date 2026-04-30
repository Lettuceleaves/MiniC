package minic.compiler.parser;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.BreakStmt;
import minic.compiler.ast.stmt.ContinueStmt;
import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.expr.StringLiteralExpr;
import minic.compiler.ast.expr.UnaryExpr;
import minic.compiler.ast.stmt.ForStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ast.stmt.WhileStmt;
import minic.compiler.lexer.TokenKind;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.type.MiniType;
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
        assertThat(functionDecl.hasBody()).isTrue();
        assertThat(functionDecl.body().statements()).isEmpty();
        assertThat(functionDecl.range()).isEqualTo(new SourceRange(sourceFile, 0, 13));
    }

    @Test
    void parsesFunctionDeclarationWithoutBody() {
        SourceFile sourceFile = new SourceFile("decl.mc", "int add(int left, int right); int main() { return 0; }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.program().functions()).hasSize(2);
        FunctionDecl declaration = result.program().functions().getFirst();
        assertThat(declaration.name()).isEqualTo("add");
        assertThat(declaration.parameters())
                .extracting(parameter -> parameter.name())
                .containsExactly("left", "right");
        assertThat(declaration.hasBody()).isFalse();
        assertThat(declaration.bodyOptional()).isEmpty();
        assertThat(declaration.range()).isEqualTo(new SourceRange(sourceFile, 0, 29));
    }

    @Test
    void parsesExternalFunctionDeclaration() {
        SourceFile sourceFile = new SourceFile("extern.mc", "extern int puts(int value); int main() { return 0; }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        FunctionDecl declaration = result.program().functions().getFirst();
        assertThat(declaration.name()).isEqualTo("puts");
        assertThat(declaration.external()).isTrue();
        assertThat(declaration.hasBody()).isFalse();
        assertThat(declaration.parameters())
                .extracting(parameter -> parameter.name())
                .containsExactly("value");
        assertThat(declaration.range()).isEqualTo(new SourceRange(sourceFile, 0, 27));
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
    void parsesIfElseStatement() {
        SourceFile sourceFile = new SourceFile("if.mc", "int main() { if (1 < 2) return 3; else return 4; }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        IfStmt ifStmt = (IfStmt) result.program().functions().getFirst().body().statements().getFirst();
        BinaryExpr condition = (BinaryExpr) ifStmt.condition();

        assertThat(condition.operator()).isEqualTo(TokenKind.LESS);
        assertThat(ifStmt.thenBranch()).isInstanceOf(ReturnStmt.class);
        assertThat(ifStmt.elseBranchOptional()).hasValueSatisfying(elseBranch ->
                assertThat(elseBranch).isInstanceOf(ReturnStmt.class));
        assertThat(ifStmt.range()).isEqualTo(new SourceRange(sourceFile, 13, 48));
    }

    @Test
    void parsesIfWithoutElseAndNestedIf() {
        SourceFile sourceFile = new SourceFile("nested-if.mc", "int main() { if (1) if (0) return 1; return 2; }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        IfStmt outerIf = (IfStmt) result.program().functions().getFirst().body().statements().getFirst();
        IfStmt innerIf = (IfStmt) outerIf.thenBranch();

        assertThat(outerIf.elseBranchOptional()).isEmpty();
        assertThat(innerIf.elseBranchOptional()).isEmpty();
        assertThat(innerIf.thenBranch()).isInstanceOf(ReturnStmt.class);
    }

    @Test
    void parsesElseIfAsNestedElseBranch() {
        SourceFile sourceFile = new SourceFile(
                "else-if.mc",
                "int main() { if (0) return 1; else if (1) return 2; else return 3; }"
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        IfStmt outerIf = (IfStmt) result.program().functions().getFirst().body().statements().getFirst();
        IfStmt elseIf = (IfStmt) outerIf.elseBranchOptional().orElseThrow();

        assertThat(outerIf.thenBranch()).isInstanceOf(ReturnStmt.class);
        assertThat(elseIf.thenBranch()).isInstanceOf(ReturnStmt.class);
        assertThat(elseIf.elseBranchOptional()).hasValueSatisfying(elseBranch ->
                assertThat(elseBranch).isInstanceOf(ReturnStmt.class));
    }

    @Test
    void bindsElseToNearestIfInElseIfChain() {
        SourceFile sourceFile = new SourceFile(
                "dangling-else-if.mc",
                "int main() { if (1) if (0) return 1; else if (1) return 2; }"
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        IfStmt outerIf = (IfStmt) result.program().functions().getFirst().body().statements().getFirst();
        IfStmt innerIf = (IfStmt) outerIf.thenBranch();
        IfStmt elseIf = (IfStmt) innerIf.elseBranchOptional().orElseThrow();

        assertThat(outerIf.elseBranchOptional()).isEmpty();
        assertThat(elseIf.thenBranch()).isInstanceOf(ReturnStmt.class);
    }

    @Test
    void parsesWhileStatement() {
        SourceFile sourceFile = new SourceFile("while.mc", "int main() { while (x < 3) x = x + 1; return x; }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        WhileStmt whileStmt = (WhileStmt) result.program().functions().getFirst().body().statements().getFirst();
        BinaryExpr condition = (BinaryExpr) whileStmt.condition();

        assertThat(condition.operator()).isEqualTo(TokenKind.LESS);
        assertThat(whileStmt.body()).isInstanceOf(ExprStmt.class);
        assertThat(whileStmt.range()).isEqualTo(new SourceRange(sourceFile, 13, 37));
    }

    @Test
    void parsesForStatement() {
        SourceFile sourceFile = new SourceFile(
                "for.mc",
                "int main() { for (int i = 0; i < 3; i = i + 1) x = x + i; return x; }"
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        ForStmt forStmt = (ForStmt) result.program().functions().getFirst().body().statements().getFirst();
        VarDeclStmt initializer = (VarDeclStmt) forStmt.initializerOptional().orElseThrow();
        BinaryExpr condition = (BinaryExpr) forStmt.conditionOptional().orElseThrow();
        AssignmentExpr step = (AssignmentExpr) forStmt.stepOptional().orElseThrow();

        assertThat(initializer.name()).isEqualTo("i");
        assertThat(condition.operator()).isEqualTo(TokenKind.LESS);
        assertThat(step.targetName()).isEqualTo("i");
        assertThat(forStmt.body()).isInstanceOf(ExprStmt.class);
        assertThat(forStmt.range()).isEqualTo(new SourceRange(sourceFile, 13, sourceFile.content().indexOf(" return")));
    }

    @Test
    void parsesForStatementWithOmittedClauses() {
        SourceFile sourceFile = new SourceFile("for-omitted.mc", "int main() { for (;;) return 1; }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        ForStmt forStmt = (ForStmt) result.program().functions().getFirst().body().statements().getFirst();
        assertThat(forStmt.initializerOptional()).isEmpty();
        assertThat(forStmt.conditionOptional()).isEmpty();
        assertThat(forStmt.stepOptional()).isEmpty();
        assertThat(forStmt.body()).isInstanceOf(ReturnStmt.class);
    }

    @Test
    void parsesBreakAndContinueStatements() {
        SourceFile sourceFile = new SourceFile("loop-control.mc", "int main() { while (1) { break; continue; } }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        WhileStmt whileStmt = (WhileStmt) result.program().functions().getFirst().body().statements().getFirst();
        BlockStmt body = (BlockStmt) whileStmt.body();

        assertThat(body.statements().get(0)).isInstanceOf(BreakStmt.class);
        assertThat(body.statements().get(0).range()).isEqualTo(new SourceRange(sourceFile, 25, 31));
        assertThat(body.statements().get(1)).isInstanceOf(ContinueStmt.class);
        assertThat(body.statements().get(1).range()).isEqualTo(new SourceRange(sourceFile, 32, 41));
    }

    @Test
    void parsesPointerDeclarationsAddressOfAndDereferenceAssignment() {
        SourceFile sourceFile = new SourceFile(
                "pointer.mc",
                "int set(int *p) { *p = 3; return *p; } int main() { int x = 0; int *p = &x; return set(p); }"
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        FunctionDecl set = result.program().functions().getFirst();
        assertThat(set.parameters().getFirst().type()).isEqualTo(MiniType.INT.pointerTo());
        ExprStmt assignmentStatement = (ExprStmt) set.body().statements().getFirst();
        AssignmentExpr assignment = (AssignmentExpr) assignmentStatement.expression();
        UnaryExpr target = (UnaryExpr) assignment.target();
        ReturnStmt returnStmt = (ReturnStmt) set.body().statements().get(1);
        UnaryExpr returned = (UnaryExpr) returnStmt.expressionOptional().orElseThrow();
        VarDeclStmt pointerDecl = (VarDeclStmt) result.program().functions().get(1).body().statements().get(1);
        UnaryExpr addressOf = (UnaryExpr) pointerDecl.initializerOptional().orElseThrow();

        assertThat(target.operator()).isEqualTo(TokenKind.STAR);
        assertThat(returned.operator()).isEqualTo(TokenKind.STAR);
        assertThat(pointerDecl.type()).isEqualTo(MiniType.INT.pointerTo());
        assertThat(addressOf.operator()).isEqualTo(TokenKind.AMPERSAND);
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
    void parsesComparisonPrecedence() {
        SourceFile sourceFile = new SourceFile("comparison.mc", "int main() { return 1 + 2 < 4 == 0; }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        ReturnStmt returnStmt = (ReturnStmt) result.program().functions().getFirst().body().statements().getFirst();
        BinaryExpr equality = (BinaryExpr) returnStmt.expressionOptional().orElseThrow();
        BinaryExpr less = (BinaryExpr) equality.left();
        BinaryExpr plus = (BinaryExpr) less.left();

        assertThat(equality.operator()).isEqualTo(TokenKind.EQUAL_EQUAL);
        assertThat(less.operator()).isEqualTo(TokenKind.LESS);
        assertThat(plus.operator()).isEqualTo(TokenKind.PLUS);
        assertThat(((IntegerLiteralExpr) less.right()).value()).isEqualTo(4);
        assertThat(((IntegerLiteralExpr) equality.right()).value()).isEqualTo(0);
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
    void parsesStringLiteralAsCallArgument() {
        SourceFile sourceFile = new SourceFile("string-call.mc", "int main() { return puts(\"hello\"); }");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        ReturnStmt returnStmt = (ReturnStmt) result.program().functions().getFirst().body().statements().getFirst();
        CallExpr callExpr = (CallExpr) returnStmt.expressionOptional().orElseThrow();
        StringLiteralExpr stringLiteralExpr = (StringLiteralExpr) callExpr.arguments().getFirst();

        assertThat(callExpr.calleeName()).isEqualTo("puts");
        assertThat(stringLiteralExpr.value()).isEqualTo("hello");
        assertThat(stringLiteralExpr.lexeme()).isEqualTo("\"hello\"");
        assertThat(stringLiteralExpr.range()).isEqualTo(new SourceRange(sourceFile, 25, 32));
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
