package minic.compiler.ast.expr;

import minic.compiler.lexer.TokenKind;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionAstTest {
    @Test
    void storesExpressionFields() {
        SourceFile sourceFile = new SourceFile("expr.mc", "x = add(1, 2)");
        NameExpr nameExpr = new NameExpr("x", new SourceRange(sourceFile, 0, 1));
        IntegerLiteralExpr one = new IntegerLiteralExpr(1, "1", new SourceRange(sourceFile, 8, 9));
        IntegerLiteralExpr two = new IntegerLiteralExpr(2, "2", new SourceRange(sourceFile, 11, 12));
        BinaryExpr binaryExpr = new BinaryExpr(one, TokenKind.PLUS, two, new SourceRange(sourceFile, 8, 12));
        GroupingExpr groupingExpr = new GroupingExpr(binaryExpr, new SourceRange(sourceFile, 7, 13));
        CallExpr callExpr = new CallExpr("add", new ArrayList<>(), new SourceRange(sourceFile, 4, 13));
        AssignmentExpr assignmentExpr = new AssignmentExpr("x", callExpr, new SourceRange(sourceFile, 0, 13));

        assertThat(nameExpr.name()).isEqualTo("x");
        assertThat(one.value()).isEqualTo(1);
        assertThat(one.lexeme()).isEqualTo("1");
        assertThat(binaryExpr.operator()).isEqualTo(TokenKind.PLUS);
        assertThat(groupingExpr.expression()).isSameAs(binaryExpr);
        assertThat(callExpr.arguments()).isEmpty();
        assertThat(assignmentExpr.value()).isSameAs(callExpr);
    }

    @Test
    void callDefensivelyCopiesArguments() {
        SourceFile sourceFile = new SourceFile("call.mc", "f(1)");
        ArrayList<Expression> arguments = new ArrayList<>();
        IntegerLiteralExpr argument = new IntegerLiteralExpr(1, "1", new SourceRange(sourceFile, 2, 3));

        CallExpr callExpr = new CallExpr("f", arguments, new SourceRange(sourceFile, 0, 4));
        arguments.add(argument);

        assertThat(callExpr.arguments()).isEmpty();
        assertThatThrownBy(() -> callExpr.arguments().add(argument))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
