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
final class AstDeclarationMigrationTest {
    private static final SourceRange RANGE = new SourceRange(1, 0, 1, 1);

    @Test void allActualAstClassesKeepTheOriginalRecordContracts() throws Exception {
        AstContractAssertions.assertMigrated(AstNode.class::isAssignableFrom, 105);
    }

    @Test void unifiedAccessorCoversEveryActualAstImplementation() throws Exception {
        assertTrue(Arrays.stream(AstNode.class.getMethods()).anyMatch(m -> m.getName().equals("visualSlots")), "uniform AST slot accessor");
        var method = AstNode.class.getMethod("visualSlots");
        assertEquals(AstVisualSlots.class, method.getReturnType());
        for (var node : List.<AstNode>of(new QualifiedName(false, List.of("name"), RANGE),
                new Declaration.BaseSpecifier(CrakenType.INT, Declaration.Access.PUBLIC, false, RANGE),
                new Expression.TypeQueryExpr.TypeArgument(CrakenType.INT, RANGE),
                new Statement.SwitchCase(null, List.of(), RANGE))) {
            var slots = (AstVisualSlots) method.invoke(node);
            assertSame(slots, method.invoke(node)); assertNull(slots.pre()); assertNull(slots.nxt());
        }
    }

    @Test void structuralSchemaKeepsOriginalBusinessOrderAndNeverIncludesSlots() throws Exception {
        var stream = getClass().getResourceAsStream("/craken/ast-record-contracts.tsv"); assertNotNull(stream);
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
            for (var line : reader.lines().toList()) {
                var parts = line.split("\\t", -1);
                var type = Class.forName(parts[0]); var shape = AstNodeComponents.describe(type);
                var expected = Arrays.stream(parts[1].split(";")).map(p -> p.split("=", 2)[0]).toList();
                assertEquals(expected, shape.components().stream().map(AstNodeComponents.Component::name).toList(), type.getName());
                assertEquals(expected.size(), shape.constructor().getParameterCount());
                assertFalse(shape.components().stream().anyMatch(c -> c.name().equals("visualSlots")));
            }
        }
    }

    @Test void auxiliaryAndMultipleInterfaceNodesHaveIndependentSlotsAndStableValues() {
        AstNode name = new QualifiedName(false, List.of("scope", "name"), RANGE);
        assertInstanceOf(AbstractAstNode.class, name);
        var equal = new QualifiedName(false, List.of("scope", "name"), RANGE);
        int hash = name.hashCode(); String text = name.toString();
        var slots = ((AbstractAstNode) name).visualSlots(); slots.setNxt(new ViewLocation(1, 1, 1));
        assertEquals(equal, name); assertEquals(hash, name.hashCode()); assertEquals(text, name.toString());
        var using = new Declaration.UsingDecl((QualifiedName) name, false, RANGE);
        assertInstanceOf(Statement.class, using); assertInstanceOf(Declaration.class, using);
        assertInstanceOf(AbstractAstNode.class, using);
        var assertion = new Declaration.StaticAssertDecl(new Expression.BoolLiteralExpr(true, "true", RANGE), null, RANGE);
        assertInstanceOf(Statement.class, assertion); assertInstanceOf(Declaration.class, assertion);
        assertInstanceOf(Declaration.RecordMember.class, assertion);
        assertNotSame(slots, ((AbstractAstNode) (AstNode) using).visualSlots());
    }

    @Test void existingChildrenTraversalKeepsAuxiliaryIdentityAndSourceOrder() {
        var argument = new Expression.TypeQueryExpr.TypeArgument(CrakenType.INT, RANGE);
        var query = new Expression.TypeQueryExpr(Expression.TypeQueryExpr.Kind.CONSTRUCTIBLE, List.of(argument), RANGE, RANGE);
        assertSame(argument, AstChildren.of(query).getFirst());
        assertInstanceOf(AbstractAstNode.class, argument);
        var capture = new Expression.LambdaExpr.Capture("value", Expression.LambdaExpr.CaptureKind.COPY, null, RANGE);
        var lambda = new Expression.LambdaExpr(Expression.LambdaExpr.CaptureDefault.NONE, List.of(capture), List.of(), false, false,
                CrakenType.INT, new Statement.BlockStmt(List.of(), RANGE), RANGE);
        assertSame(capture, AstChildren.of(lambda).getFirst()); assertInstanceOf(AbstractAstNode.class, capture);
        var clause = new Statement.SwitchCase(null, List.of(), RANGE);
        var selector = new Expression.IntegerLiteralExpr(1, "1", RANGE);
        var switchNode = new Statement.SwitchStmt(selector, List.of(clause), RANGE);
        assertEquals(List.of(selector, clause), AstChildren.of(switchNode)); assertInstanceOf(AbstractAstNode.class, clause);
    }

    @Test void helpersOutsideAstNodeKeepTheirRecordRepresentation() {
        for (var type : List.of(Expression.TemporaryLifetime.class, Expression.Designator.Field.class, Expression.Designator.Index.class,
                Declaration.RecordInfo.class, OperatorName.class, ConversionName.class)) {
            assertTrue(type.isRecord(), type.getName()); assertFalse(AstNode.class.isAssignableFrom(type));
        }
    }

    @Test void templateDeclarationCopiesKeepRenamingOriginsAndEmptySlots() {
        AstNode original = new Declaration.StructDecl("Box", List.of(), true, false, RANGE);
        assertInstanceOf(AbstractAstNode.class, original);
        ((AbstractAstNode) original).visualSlots().setNxt(new ViewLocation(1, 1, 1));
        var substitution = new TemplateSubstitution(Map.of(), "Box", "Box<int>");
        var copy = substitution.instantiate((Declaration.StructDecl) original);
        assertEquals("Box<int>", copy.name()); assertNotSame(original, copy);
        assertSame(original, substitution.origins().get(copy));
        assertNull(((AbstractAstNode) (AstNode) copy).visualSlots().nxt());
        assertEquals(RANGE, copy.range());
    }
}
