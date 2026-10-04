package craken.compiler.semantic.manager;

import craken.SourceRange;
import craken.compiler.lexer.token.TokenType;
import craken.compiler.parser.node.Expression;
import craken.compiler.type.CrakenType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class AstTemplateStructureTest {
    @Test void packsStillVisitBusinessComponentsInsideOrdinaryAstClasses() {
        var range = new SourceRange(1, 0, 1, 1);
        var parameter = new CrakenType.TemplateParameterType("Pack", 0);
        var pattern = new Expression.BinaryExpr(new Expression.TemplateValueExpr(parameter, CrakenType.INT, range),
                TokenType.PLUS, new Expression.NameExpr("arguments", range), range);
        assertFalse(pattern.getClass().isRecord(), "test must exercise ordinary AST traversal");
        assertEquals(Set.of(parameter), TemplatePacks.parameters(pattern));
        assertEquals(Set.of("arguments"), TemplatePacks.names(pattern));
        assertTrue(TemplatePacks.parameters(new Expression.PackExpansionExpr(pattern, range)).isEmpty(), "nested expansions own their patterns");
    }
}
