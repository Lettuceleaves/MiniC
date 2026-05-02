package minic.compiler.ast.expr;

import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionAstTest {
    @Test
    void callArgumentsAreDefensivelyCopiedAndImmutable() {
        SourceRange range = range("call.mc", "f(1)");
        ArrayList<Expression> arguments = new ArrayList<>();
        IntegerLiteralExpr argument = new IntegerLiteralExpr(1, "1", range);

        CallExpr callExpr = new CallExpr("f", arguments, range);
        arguments.add(argument);

        assertThat(callExpr.arguments()).isEmpty();
        assertThatThrownBy(() -> callExpr.arguments().add(argument))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void namedExpressionHelpersRejectInvalidTargets() {
        SourceRange range = range("expr.mc", "f = 1");
        IntegerLiteralExpr literal = new IntegerLiteralExpr(1, "1", range);
        CallExpr indirectCall = new CallExpr(literal, List.of(), range);
        AssignmentExpr nonNameAssignment = new AssignmentExpr(literal, literal, range);

        assertThat(indirectCall.hasDirectCalleeName()).isFalse();
        assertThatThrownBy(indirectCall::calleeName)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(nonNameAssignment::targetName)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CallExpr("", List.of(), range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NameExpr(" ", range))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldAccessExpr(literal, "", false, range))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SourceRange range(String path, String content) {
        SourceFile sourceFile = new SourceFile(path, content);
        return new SourceRange(sourceFile, 0, content.length());
    }
}
