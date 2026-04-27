package minic.compiler.ast;

import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementAstTest {
    @Test
    void blockDefensivelyCopiesStatements() {
        SourceFile sourceFile = new SourceFile("block.mc", "{ return; }");
        SourceRange range = new SourceRange(sourceFile, 0, sourceFile.content().length());
        ReturnStmt returnStmt = new ReturnStmt(null, new SourceRange(sourceFile, 2, 9));
        ArrayList<Statement> statements = new ArrayList<>();

        BlockStmt blockStmt = new BlockStmt(statements, range);
        statements.add(returnStmt);

        assertThat(blockStmt.statements()).isEmpty();
        assertThat(blockStmt.range()).isSameAs(range);
        assertThatThrownBy(() -> blockStmt.statements().add(returnStmt))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void storesStatementFields() {
        SourceFile sourceFile = new SourceFile("statements.mc", "int x = 1; return x; x;");
        SourceRange initializerRange = new SourceRange(sourceFile, 8, 9);
        SourceRange varRange = new SourceRange(sourceFile, 0, 10);
        SourceRange returnExpressionRange = new SourceRange(sourceFile, 18, 19);
        SourceRange returnRange = new SourceRange(sourceFile, 11, 20);
        SourceRange exprRange = new SourceRange(sourceFile, 21, 22);
        SourceRange exprStmtRange = new SourceRange(sourceFile, 21, 23);

        VarDeclStmt varDeclStmt = new VarDeclStmt("x", initializerRange, varRange);
        ReturnStmt returnStmt = new ReturnStmt(returnExpressionRange, returnRange);
        ExprStmt exprStmt = new ExprStmt(exprRange, exprStmtRange);

        assertThat(varDeclStmt.name()).isEqualTo("x");
        assertThat(varDeclStmt.initializerRangeOptional()).contains(initializerRange);
        assertThat(varDeclStmt.range()).isSameAs(varRange);
        assertThat(returnStmt.expressionRangeOptional()).contains(returnExpressionRange);
        assertThat(returnStmt.range()).isSameAs(returnRange);
        assertThat(exprStmt.expressionRange()).isSameAs(exprRange);
        assertThat(exprStmt.range()).isSameAs(exprStmtRange);
    }
}
