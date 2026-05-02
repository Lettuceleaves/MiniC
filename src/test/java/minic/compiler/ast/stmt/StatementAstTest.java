package minic.compiler.ast.stmt;

import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementAstTest {
    @Test
    void blockStatementsAreDefensivelyCopiedAndImmutable() {
        SourceRange range = range("block.mc", "{ return; }");
        ReturnStmt returnStmt = new ReturnStmt(null, range);
        ArrayList<Statement> statements = new ArrayList<>();

        BlockStmt blockStmt = new BlockStmt(statements, range);
        statements.add(returnStmt);

        assertThat(blockStmt.statements()).isEmpty();
        assertThatThrownBy(() -> blockStmt.statements().add(returnStmt))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void variableNamesMustNotBeBlank() {
        SourceRange range = range("var.mc", "int x = 1;");
        IntegerLiteralExpr initializer = new IntegerLiteralExpr(1, "1", range);

        assertThatThrownBy(() -> new VarDeclStmt(" ", initializer, range))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SourceRange range(String path, String content) {
        SourceFile sourceFile = new SourceFile(path, content);
        return new SourceRange(sourceFile, 0, content.length());
    }
}
