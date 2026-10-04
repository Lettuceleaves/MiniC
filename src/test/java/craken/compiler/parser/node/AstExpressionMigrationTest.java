package craken.compiler.parser.node;

import craken.SourceRange;
import craken.compiler.lexer.token.TokenType;
import craken.compiler.semantic.manager.TemplateSubstitution;
import craken.visualization.api.ViewLocation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class AstExpressionMigrationTest {
    private static final SourceRange RANGE = new SourceRange(1, 0, 1, 1);

    @Test void allExpressionClassesKeepTheirOriginalPublicContracts() throws Exception {
        AstContractAssertions.assertMigrated(Expression.class::isAssignableFrom, 49);
    }

    @Test void slotsRoundTripWithoutChangingBusinessIdentityOrText() throws Exception {
        var node = new Expression.NameExpr("item", RANGE);
        var equal = new Expression.NameExpr("item", RANGE);
        var map = new HashMap<Expression, String>(); map.put(node, "found");
        int hash = node.hashCode(); String text = node.toString();
        assertFalse(node.getClass().isRecord(), "visual slots need an ordinary AST class");
        Object slots = node.getClass().getMethod("visualSlots").invoke(node);
        var pre = new ViewLocation(1, 2, 3); var nxt = new ViewLocation(4, 5, 6);
        slots.getClass().getMethod("setPre", ViewLocation.class).invoke(slots, pre);
        slots.getClass().getMethod("setNxt", ViewLocation.class).invoke(slots, nxt);
        Object pair = slots.getClass().getMethod("snapshot").invoke(slots);
        assertTrue(pair.getClass().isRecord());
        slots.getClass().getMethod("clear").invoke(slots);
        assertNull(slots.getClass().getMethod("pre").invoke(slots));
        assertNull(slots.getClass().getMethod("nxt").invoke(slots));
        slots.getClass().getMethod("restore", pair.getClass()).invoke(slots, pair);
        assertEquals(pre, slots.getClass().getMethod("pre").invoke(slots));
        assertEquals(nxt, slots.getClass().getMethod("nxt").invoke(slots));
        assertEquals(equal, node); assertEquals(hash, node.hashCode()); assertEquals(text, node.toString());
        assertEquals("found", map.get(equal));
        assertEquals(31 * "item".hashCode() + RANGE.hashCode(), hash);
        assertEquals("NameExpr[name=item, range=" + RANGE + "]", text);
    }

    @Test void floatingValuesKeepRecordNaNAndSignedZeroSemantics() {
        assertEquals(new Expression.DoubleLiteralExpr(Double.NaN, "nan", RANGE),
                new Expression.DoubleLiteralExpr(Double.longBitsToDouble(0x7ff0000000000001L), "nan", RANGE));
        assertNotEquals(new Expression.DoubleLiteralExpr(0.0, "0", RANGE), new Expression.DoubleLiteralExpr(-0.0, "0", RANGE));
        assertEquals(new Expression.FloatLiteralExpr(Float.NaN, "nan", RANGE),
                new Expression.FloatLiteralExpr(Float.intBitsToFloat(0x7f800001), "nan", RANGE));
        assertNotEquals(new Expression.FloatLiteralExpr(0.0f, "0", RANGE), new Expression.FloatLiteralExpr(-0.0f, "0", RANGE));
    }

    @Test void constructorsStillCopyCollectionsAndValidateEvaluationOrder() {
        var arguments = new ArrayList<Expression>(); arguments.add(new Expression.IntegerLiteralExpr(7, "7", RANGE));
        var call = new Expression.CallExpr(new Expression.NameExpr("f", RANGE), arguments, RANGE);
        arguments.clear(); assertEquals(1, call.arguments().size()); assertEquals(List.of(0), call.argumentEvaluationOrder());
        assertThrows(UnsupportedOperationException.class, () -> call.arguments().clear());
        assertThrows(IllegalArgumentException.class, () -> new Expression.CallExpr(call.callee(), call.arguments(), List.of(1), RANGE));
        assertThrows(NullPointerException.class, () -> new Expression.NameExpr(null, RANGE));
    }

    @Test void templateCopiesOrdinaryExpressionClassesAndStartsWithEmptySlots() throws Exception {
        var source = new Expression.BinaryExpr(new Expression.NameExpr("value", RANGE), TokenType.PLUS,
                new Expression.IntegerLiteralExpr(3, "3", RANGE), RANGE);
        if (!source.getClass().isRecord()) {
            Object slots = source.getClass().getMethod("visualSlots").invoke(source);
            slots.getClass().getMethod("setNxt", ViewLocation.class).invoke(slots, new ViewLocation(1, 1, 1));
        }
        var substitution = new TemplateSubstitution(Map.of(), "Box", "Box<int>");
        var copy = substitution.expression(source);
        assertEquals(source, copy); assertNotSame(source, copy); assertSame(source, substitution.origins().get(copy));
        assertFalse(copy.getClass().isRecord(), "instantiated expression must be migrated too");
        Object slots = copy.getClass().getMethod("visualSlots").invoke(copy);
        assertNull(slots.getClass().getMethod("pre").invoke(slots));
        assertNull(slots.getClass().getMethod("nxt").invoke(slots));
    }
}
