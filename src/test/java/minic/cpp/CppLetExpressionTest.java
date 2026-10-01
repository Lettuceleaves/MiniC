package minic.cpp;

import minic.SourceRange;
import minic.compiler.ir.IrLowerer;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.compiler.lexer.token.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Internal value captures are expression-scoped values, not addressable stack objects. */
final class CppLetExpressionTest {
    private static final SourceRange RANGE = new SourceRange(1, 0, 1, 30);

    @Test void childrenRetainSourceIdentityAndSourceReferenceGuardsVisitTheBindingType() {
        var initializer = integer(4);
        var body = new NameExpr("capture", RANGE);
        var value = new LetExpr("capture", MiniType.INT, initializer, body, RANGE);
        assertSame(RANGE, value.range());
        assertEquals(List.of(initializer, body), AstChildren.of(value));
        assertThrows(IllegalArgumentException.class, () -> new LetExpr(" ", MiniType.INT, initializer, body, RANGE));
        assertThrows(NullPointerException.class, () -> new LetExpr("capture", MiniType.INT, null, body, RANGE));
        var reference = new LetExpr("capture", MiniType.INT.referenceTo(), initializer, body, RANGE);
        assertSame(reference, AstChildren.firstReferenceSyntax(reference));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer(program(reference), Map.of(), Map.of()));
    }

    @Test void capturesAreLexicallyScopedAndCannotAcquireAnAddress() {
        var address = new LetExpr("capture", MiniType.INT, integer(4),
                new UnaryExpr(TokenType.AMPERSAND, new NameExpr("capture", RANGE), RANGE), RANGE);
        var semantic = analyze(address);
        assertFalse(semantic.succeeded());
        var outside = new CommaExpr(List.of(new LetExpr("capture", MiniType.INT, integer(4),
                new NameExpr("capture", RANGE), RANGE), new NameExpr("capture", RANGE)), RANGE);
        assertFalse(analyze(outside).succeeded());
    }

    @Test void aTypeQueryValidatesTheCaptureWithoutProducingRuntimeInstructions() {
        var capture = new LetExpr("capture", MiniType.INT, integer(4), new NameExpr("capture", RANGE), RANGE);
        var query = new SizeofExpr(capture, null, RANGE);
        var program = program(query);
        var semantic = new SemanticAnalyzer(program);
        semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        var ir = new IrLowerer(program, semantic.semanticResult()).lower();
        var instructions = ir.findFunction("main").orElseThrow().blocks().stream()
                .flatMap(block -> block.instructions().stream()).toList();
        assertTrue(instructions.stream().noneMatch(
                minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction.class::isInstance));
        assertTrue(ir.externalFunctionNames().isEmpty());
    }

    private static IntegerLiteralExpr integer(int value) { return new IntegerLiteralExpr(value, Integer.toString(value), RANGE); }
    private static Program program(minic.compiler.parser.node.Expression expression) {
        var main = new FunctionDecl("main", MiniType.INT, List.of(), false,
                new BlockStmt(List.of(new ReturnStmt(expression, RANGE)), RANGE), false, RANGE);
        return new Program(List.of(), List.of(main), RANGE);
    }
    private static SemanticAnalyzer analyze(minic.compiler.parser.node.Expression expression) {
        var semantic = new SemanticAnalyzer(program(expression));
        semantic.analyze();
        return semantic;
    }
}
