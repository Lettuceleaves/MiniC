package craken.compiler.parser.node;

import craken.SourceRange;
import craken.compiler.semantic.manager.TemplateSubstitution;
import craken.compiler.type.CrakenType;
import craken.visualization.api.ViewLocation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class AstStatementMigrationTest {
    private static final SourceRange RANGE = new SourceRange(1, 0, 1, 1);

    @Test void allStatementClassesKeepTheirOriginalPublicContracts() throws Exception {
        AstContractAssertions.assertMigrated(type -> type.getEnclosingClass() == Statement.class
                && Statement.class.isAssignableFrom(type), 15);
    }

    @Test void positionsDoNotChangeNullOperandsEqualityHashOrMapLookup() {
        AstNode node = new Statement.ReturnStmt(null, RANGE);
        assertInstanceOf(AbstractAstNode.class, node, "statement carries slots itself");
        var slots = ((AbstractAstNode) node).visualSlots();
        var equal = new Statement.ReturnStmt(null, RANGE);
        var set = new HashSet<AstNode>(); set.add(node);
        slots.setPre(new ViewLocation(1, 2, 3)); slots.setNxt(new ViewLocation(4, 5, 6));
        assertEquals(equal, node); assertEquals(RANGE.hashCode(), node.hashCode());
        assertEquals("ReturnStmt[expression=null, range=" + RANGE + "]", node.toString());
        assertTrue(set.contains(equal)); assertTrue(((Statement.ReturnStmt) node).expressionOptional().isEmpty());
    }

    @Test void statementConstructorsKeepCopiesOptionalValuesAndValidation() {
        var statements = new ArrayList<Statement>(); statements.add(new Statement.BreakStmt(RANGE));
        var block = new Statement.BlockStmt(statements, RANGE); statements.clear();
        assertEquals(1, block.statements().size()); assertThrows(UnsupportedOperationException.class, () -> block.statements().clear());
        var loop = new Statement.ForStmt(null, null, null, block, RANGE);
        assertTrue(loop.initializerOptional().isEmpty()); assertTrue(loop.conditionOptional().isEmpty()); assertTrue(loop.stepOptional().isEmpty());
        var variable = new Statement.VarDeclStmt("value", CrakenType.INT, null, RANGE);
        assertFalse(variable.staticStorage()); assertFalse(variable.constexprSpecifier());
        assertThrows(IllegalArgumentException.class, () -> new Statement.DeclGroupStmt(List.of(), RANGE));
        assertThrows(IllegalArgumentException.class, () -> new Statement.VarDeclStmt(" ", CrakenType.INT, null, RANGE));
    }

    @Test void templateCopyTraversesMigratedControlFlowWithoutSharingItsSlots() {
        var body = new Statement.BlockStmt(List.of(new Statement.ReturnStmt(new Expression.IntegerLiteralExpr(3, "3", RANGE), RANGE)), RANGE);
        AstNode originalBody = body;
        assertInstanceOf(AbstractAstNode.class, originalBody);
        ((AbstractAstNode) originalBody).visualSlots().setNxt(new ViewLocation(1, 1, 1));
        var function = new Declaration.FunctionDecl("f", CrakenType.INT, List.of(), false, body, false, RANGE);
        var substitution = new TemplateSubstitution(Map.of(), "Box", "Box<int>");
        var copy = substitution.instantiate(function);
        assertEquals(function, copy); assertNotSame(body, copy.body());
        assertSame(body, substitution.origins().get(copy.body()));
        assertNull(((AbstractAstNode) (AstNode) copy.body()).visualSlots().nxt());
        assertEquals(body.statements(), copy.body().statements());
    }
}
