package minic.compiler.parser;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.BoolLiteralExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.expr.ConditionalExpr;
import minic.compiler.ast.expr.FieldAccessExpr;
import minic.compiler.ast.expr.FloatLiteralExpr;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IndexExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.expr.NullLiteralExpr;
import minic.compiler.ast.expr.SizeofExpr;
import minic.compiler.ast.expr.StringLiteralExpr;
import minic.compiler.ast.expr.UnaryExpr;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.BreakStmt;
import minic.compiler.ast.stmt.ContinueStmt;
import minic.compiler.ast.stmt.DoWhileStmt;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.ForStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ast.stmt.WhileStmt;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.lexer.TokenKind;
import minic.compiler.type.MiniType;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParserTest {
    @Test
    void parsesDeclarationsTypesAndFunctionPointersInOneProgram() {
        SourceFile sourceFile = new SourceFile(
                "declarations.mc",
                """
                        extern int puts(char *message);
                        struct Handler {
                          int value;
                          int (*operation)(int value, int *data);
                        };

                        double mix(bool flag, char tag, long count, float ratio, double score) {
                          return score;
                        }

                        int apply(int (*operation)(int, int *), struct Handler *handler) {
                          int values[3];
                          int *data = &values[0];
                          handler->value = operation(1, data);
                          return handler->value;
                        }
                        """
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.program().structs()).singleElement().satisfies(structDecl -> {
            assertThat(structDecl.name()).isEqualTo("Handler");
            assertThat(structDecl.fields()).extracting(field -> field.name())
                    .containsExactly("value", "operation");
            assertThat(structDecl.fields().get(1).type())
                    .isEqualTo(MiniType.function(MiniType.INT, List.of(MiniType.INT, MiniType.INT.pointerTo())).pointerTo());
        });
        assertThat(result.program().functions()).extracting(FunctionDecl::name)
                .containsExactly("puts", "mix", "apply");

        FunctionDecl puts = result.program().functions().get(0);
        assertThat(puts.external()).isTrue();
        assertThat(puts.hasBody()).isFalse();
        assertThat(puts.parameters().getFirst().type()).isEqualTo(MiniType.CHAR.pointerTo());

        FunctionDecl mix = result.program().functions().get(1);
        assertThat(mix.returnType()).isEqualTo(MiniType.DOUBLE);
        assertThat(mix.parameters()).extracting(parameter -> parameter.type())
                .containsExactly(MiniType.BOOL, MiniType.CHAR, MiniType.LONG, MiniType.FLOAT, MiniType.DOUBLE);

        FunctionDecl apply = result.program().functions().get(2);
        assertThat(apply.parameters()).extracting(parameter -> parameter.type())
                .containsExactly(
                        MiniType.function(MiniType.INT, List.of(MiniType.INT, MiniType.INT.pointerTo())).pointerTo(),
                        MiniType.struct("Handler").pointerTo()
                );
        BlockStmt body = apply.body();
        assertThat(((VarDeclStmt) body.statements().get(0)).type()).isEqualTo(MiniType.INT.arrayOf(3));
        VarDeclStmt pointerDecl = (VarDeclStmt) body.statements().get(1);
        assertThat(pointerDecl.type()).isEqualTo(MiniType.INT.pointerTo());
        assertThat(pointerDecl.initializerOptional().orElseThrow()).isInstanceOf(UnaryExpr.class);

        AssignmentExpr assignment = (AssignmentExpr) ((ExprStmt) body.statements().get(2)).expression();
        assertThat(assignment.target()).isInstanceOf(FieldAccessExpr.class);
        assertThat(assignment.value()).isInstanceOf(CallExpr.class);
        assertThat(((FieldAccessExpr) assignment.target()).viaPointer()).isTrue();

        ReturnStmt returnStmt = (ReturnStmt) body.statements().get(3);
        assertThat(returnStmt.expressionOptional().orElseThrow()).isInstanceOf(FieldAccessExpr.class);
    }

    @Test
    void parsesVariadicFunctionDeclarationAtParameterListEnd() {
        SourceFile sourceFile = new SourceFile(
                "variadic.mc",
                "extern int printf(char *format, ...);"
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        FunctionDecl printf = result.program().functions().getFirst();
        assertThat(printf.name()).isEqualTo("printf");
        assertThat(printf.external()).isTrue();
        assertThat(printf.variadic()).isTrue();
        assertThat(printf.parameters()).singleElement().satisfies(parameter ->
                assertThat(parameter.type()).isEqualTo(MiniType.CHAR.pointerTo()));
    }

    @Test
    void reportsVariadicMarkerBeforeTrailingParameters() {
        SourceFile sourceFile = new SourceFile(
                "bad-variadic.mc",
                "extern int bad(int first, ..., int after);"
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .contains("可变参数标记必须位于参数列表末尾");
    }

    @Test
    void parsesControlFlowAndLoopStatementsInOneProgram() {
        SourceFile sourceFile = new SourceFile(
                "control-flow.mc",
                """
                        int main() {
                          int total = 0;
                          for (int i = 0; i < 5; i++) {
                            if (i == 3) break;
                            else if (i == 1) continue;
                            total += i;
                          }
                          while (total < 10) total = total + 1;
                          for (;;) return total;
                        }
                        """
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        BlockStmt body = result.program().functions().getFirst().body();
        assertThat(body.statements()).hasSize(4);

        ForStmt countedFor = (ForStmt) body.statements().get(1);
        assertThat(countedFor.initializerOptional()).hasValueSatisfying(initializer ->
                assertThat(initializer).isInstanceOf(VarDeclStmt.class));
        assertThat(countedFor.conditionOptional()).hasValueSatisfying(condition ->
                assertThat(condition).isInstanceOf(BinaryExpr.class));
        assertThat(countedFor.stepOptional()).hasValueSatisfying(step ->
                assertThat(step).isInstanceOf(AssignmentExpr.class));
        AssignmentExpr increment = (AssignmentExpr) countedFor.stepOptional().orElseThrow();
        assertThat(increment.value()).isInstanceOf(BinaryExpr.class);

        BlockStmt forBody = (BlockStmt) countedFor.body();
        IfStmt ifStmt = (IfStmt) forBody.statements().getFirst();
        IfStmt elseIf = (IfStmt) ifStmt.elseBranchOptional().orElseThrow();
        assertThat(ifStmt.thenBranch()).isInstanceOf(BreakStmt.class);
        assertThat(elseIf.thenBranch()).isInstanceOf(ContinueStmt.class);
        ExprStmt compoundAssignment = (ExprStmt) forBody.statements().get(1);
        assertThat(compoundAssignment.expression()).isInstanceOf(AssignmentExpr.class);
        assertThat(((AssignmentExpr) compoundAssignment.expression()).operator()).isEqualTo(TokenKind.PLUS_EQUAL);

        WhileStmt whileStmt = (WhileStmt) body.statements().get(2);
        assertThat(whileStmt.condition()).isInstanceOf(BinaryExpr.class);
        assertThat(whileStmt.body()).isInstanceOf(ExprStmt.class);

        ForStmt infiniteFor = (ForStmt) body.statements().get(3);
        assertThat(infiniteFor.initializerOptional()).isEmpty();
        assertThat(infiniteFor.conditionOptional()).isEmpty();
        assertThat(infiniteFor.stepOptional()).isEmpty();
        assertThat(infiniteFor.body()).isInstanceOf(ReturnStmt.class);
    }

    @Test
    void parsesDoWhileStatement() {
        ParseResult result = parse(new SourceFile("do.mc", """
                int main() {
                  do {
                    continue;
                  } while (value < 3);
                  return 0;
                }
                """));

        assertThat(result.diagnostics()).isEmpty();
        BlockStmt body = result.program().functions().getFirst().body();
        assertThat(body.statements().getFirst()).isInstanceOf(DoWhileStmt.class);
        DoWhileStmt doWhileStmt = (DoWhileStmt) body.statements().getFirst();
        assertThat(doWhileStmt.body()).isInstanceOf(BlockStmt.class);
        assertThat(doWhileStmt.condition()).isInstanceOf(BinaryExpr.class);
    }

    @Test
    void parsesExpressionPrecedenceCallsLiteralsAndPostfixOperators() {
        SourceFile sourceFile = new SourceFile(
                "expressions.mc",
                """
                        int main() {
                          values[0] = add(1, (2 + 3) * 4);
                          point.x = values[0] >= 10 == true;
                          puts("hello");
                          ratio = 1.25f;
                          ptr = NULL;
                          return (operation)(values[0], &point.x);
                        }
                        """
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        BlockStmt body = result.program().functions().getFirst().body();

        AssignmentExpr indexAssignment = (AssignmentExpr) ((ExprStmt) body.statements().get(0)).expression();
        assertThat(indexAssignment.target()).isInstanceOf(IndexExpr.class);
        CallExpr addCall = (CallExpr) indexAssignment.value();
        assertThat(addCall.calleeName()).isEqualTo("add");
        BinaryExpr multiply = (BinaryExpr) addCall.arguments().get(1);
        assertThat(multiply.operator()).isEqualTo(TokenKind.STAR);
        assertThat(multiply.left()).isInstanceOf(GroupingExpr.class);

        AssignmentExpr fieldAssignment = (AssignmentExpr) ((ExprStmt) body.statements().get(1)).expression();
        BinaryExpr equality = (BinaryExpr) fieldAssignment.value();
        assertThat(fieldAssignment.target()).isInstanceOf(FieldAccessExpr.class);
        assertThat(equality.operator()).isEqualTo(TokenKind.EQUAL_EQUAL);
        assertThat(equality.left()).isInstanceOf(BinaryExpr.class);
        assertThat(equality.right()).isInstanceOf(BoolLiteralExpr.class);

        CallExpr putsCall = (CallExpr) ((ExprStmt) body.statements().get(2)).expression();
        assertThat(putsCall.arguments().getFirst()).isInstanceOf(StringLiteralExpr.class);

        AssignmentExpr floatAssignment = (AssignmentExpr) ((ExprStmt) body.statements().get(3)).expression();
        assertThat(floatAssignment.value()).isInstanceOf(FloatLiteralExpr.class);
        AssignmentExpr nullAssignment = (AssignmentExpr) ((ExprStmt) body.statements().get(4)).expression();
        assertThat(nullAssignment.value()).isInstanceOf(NullLiteralExpr.class);

        ReturnStmt returnStmt = (ReturnStmt) body.statements().get(5);
        CallExpr indirectCall = (CallExpr) returnStmt.expressionOptional().orElseThrow();
        assertThat(indirectCall.callee()).isInstanceOf(GroupingExpr.class);
        assertThat(indirectCall.arguments()).hasSize(2);
        assertThat(indirectCall.arguments().get(0)).isInstanceOf(IndexExpr.class);
        assertThat(indirectCall.arguments().get(1)).isInstanceOf(UnaryExpr.class);
    }

    @Test
    void parsesPhaseDExpressionPrecedenceAndRightAssociativity() {
        SourceFile sourceFile = new SourceFile(
                "phase-d-expr.mc",
                """
                        int main() {
                          value = a | b ^ c & d == e < f << g + h * i;
                          value = cond ? left : other ? middle : right;
                          target += value -= 1;
                          value++;
                          --value;
                          return sizeof value + sizeof(int);
                        }
                        """
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        BlockStmt body = result.program().functions().getFirst().body();

        AssignmentExpr firstAssignment = (AssignmentExpr) ((ExprStmt) body.statements().get(0)).expression();
        BinaryExpr bitwiseOr = (BinaryExpr) firstAssignment.value();
        assertThat(bitwiseOr.operator()).isEqualTo(TokenKind.PIPE);
        assertThat(((BinaryExpr) bitwiseOr.right()).operator()).isEqualTo(TokenKind.CARET);

        AssignmentExpr conditionalAssignment = (AssignmentExpr) ((ExprStmt) body.statements().get(1)).expression();
        ConditionalExpr conditional = (ConditionalExpr) conditionalAssignment.value();
        assertThat(conditional.elseExpression()).isInstanceOf(ConditionalExpr.class);

        AssignmentExpr compound = (AssignmentExpr) ((ExprStmt) body.statements().get(2)).expression();
        assertThat(compound.operator()).isEqualTo(TokenKind.PLUS_EQUAL);
        assertThat(compound.value()).isInstanceOf(AssignmentExpr.class);
        assertThat(((AssignmentExpr) compound.value()).operator()).isEqualTo(TokenKind.MINUS_EQUAL);

        assertThat(((ExprStmt) body.statements().get(3)).expression()).isInstanceOf(AssignmentExpr.class);
        assertThat(((ExprStmt) body.statements().get(4)).expression()).isInstanceOf(UnaryExpr.class);

        BinaryExpr sizeofSum = (BinaryExpr) ((ReturnStmt) body.statements().get(5)).expressionOptional().orElseThrow();
        assertThat(sizeofSum.left()).isInstanceOf(SizeofExpr.class);
        assertThat(sizeofSum.right()).isInstanceOf(SizeofExpr.class);
        assertThat(((SizeofExpr) sizeofSum.left()).expressionOptional()).isPresent();
        assertThat(((SizeofExpr) sizeofSum.right()).queriedTypeOptional()).contains(MiniType.INT);
    }

    @Test
    void reportsRepresentativeSyntaxErrorsAndRecovers() {
        SourceFile sourceFile = new SourceFile(
                "invalid.mc",
                "main() {} int (*factory())(int); int main() { return 0; }"
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly("PAR001", "PAR001");
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.severity())
                .containsOnly(DiagnosticSeverity.ERROR);
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .contains("暂不支持函数指针返回值");
        assertThat(result.program().functions())
                .extracting(FunctionDecl::name)
                .containsExactly("main");
    }

    @Test
    void parsesEmptyProgram() {
        SourceFile sourceFile = new SourceFile("empty.mc", "");

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.program().functions()).isEmpty();
        assertThat(result.program().structs()).isEmpty();
    }

    @Test
    void reportsUnclosedBlockAtOpeningBrace() {
        SourceFile sourceFile = new SourceFile(
                "unclosed.mc",
                """
                        int main() {
                            {
                                return 0;
                        """
        );

        ParseResult result = parse(sourceFile);

        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> {
                    assertThat(diagnostic.message()).contains("未闭合");
                    assertThat(sourceFile.content().charAt(diagnostic.range().startOffset())).isEqualTo('{');
                });
    }

    private ParseResult parse(SourceFile sourceFile) {
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        return new Parser(lexResult.tokens()).parse();
    }
}
