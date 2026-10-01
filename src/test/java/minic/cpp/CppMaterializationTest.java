package minic.cpp;

import minic.SourceRange;
import minic.compiler.SourceFile;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.MemoryInstruction.IrDeclareLocalInstruction;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.compiler.lexer.token.TokenType;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Compiler-only stack materialization, independent of C++ reference binding. */
final class CppMaterializationTest {
    private static final SourceRange RANGE = new SourceRange(1, 0, 1, 10);

    @Test void lifetimeOwnersRetainSourceIdentityWithoutBecomingExecutableChildren() {
        Expression initializer = integer(7);
        var sourceReference = new VarDeclStmt("alias", constant(MiniType.INT).referenceTo(), initializer, RANGE);
        var lifetime = new TemporaryLifetime(TemporaryLifetime.Kind.REFERENCE_SCOPE, sourceReference);
        var temporary = new MaterializeExpr(constant(MiniType.INT), initializer, lifetime, RANGE);
        assertSame(sourceReference, temporary.lifetime().sourceOwner());
        assertSame(initializer, temporary.initializer());
        assertSame(RANGE, temporary.range());
        assertEquals(List.of(initializer), AstChildren.of(temporary));
        assertNull(AstChildren.firstReferenceSyntax(temporary), "A source lifetime owner is metadata, not executable core syntax");
        assertThrows(NullPointerException.class, () -> new TemporaryLifetime(null, initializer));
        assertThrows(NullPointerException.class, () -> new MaterializeExpr(MiniType.INT, initializer, null, RANGE));
        var invalidType = new MaterializeExpr(MiniType.INT.referenceTo(), initializer, lifetime, RANGE);
        assertSame(invalidType, AstChildren.firstReferenceSyntax(invalidType));
        assertThrows(IllegalArgumentException.class, () -> new IrLowerer(program(List.of(new ReturnStmt(invalidType, RANGE))), Map.of(), Map.of()));
    }

    @Test void scalarMaterializationConvertsBeforeInitializingItsStackObject() {
        var value = new DoubleLiteralExpr(3.75, "3.75", RANGE);
        var temporary = materialize(MiniType.INT, value);
        var program = program(List.of(new VarDeclStmt("pointer", MiniType.INT.pointerTo(), temporary, RANGE),
                new ReturnStmt(deref("pointer"), RANGE)));
        var ir = lower(program);
        assertEquals(2, declarations(ir));
        assertEquals(3, execute(ir));
        assertTrue(ir.externalFunctionNames().isEmpty());
    }

    @Test void differentMaterializationsCreateIndependentObjectsAndRemainOnTheStack() {
        var program = program(List.of(
                new VarDeclStmt("first", MiniType.INT.pointerTo(), materialize(MiniType.INT, integer(7)), RANGE),
                new VarDeclStmt("second", MiniType.INT.pointerTo(), materialize(MiniType.INT, integer(7)), RANGE),
                new ExprStmt(new AssignmentExpr(deref("first"), TokenType.EQUAL, integer(9), RANGE), RANGE),
                new ReturnStmt(new BinaryExpr(new BinaryExpr(deref("first"), TokenType.PLUS, deref("second"), RANGE),
                        TokenType.PLUS, new BinaryExpr(name("first"), TokenType.BANG_EQUAL, name("second"), RANGE), RANGE), RANGE)));
        var ir = lower(program);
        assertEquals(4, declarations(ir));
        assertEquals(17, execute(ir));
    }

    @Test void aMaterializationInAnUnselectedBranchDoesNotEvaluateItsInitializer() {
        var invalid = new BinaryExpr(integer(1), TokenType.SLASH, integer(0), RANGE);
        var selected = new ConditionalExpr(integer(1), materialize(MiniType.INT, integer(4)), materialize(MiniType.INT, invalid), RANGE);
        assertEquals(4, execute(lower(program(List.of(new ReturnStmt(new UnaryExpr(TokenType.STAR, selected, RANGE), RANGE))))));
    }

    @Test void eachLoopEntryInitializesTheSameStaticStorageSlotAgain() {
        var body = new BlockStmt(List.of(
                new VarDeclStmt("pointer", MiniType.INT.pointerTo(), materialize(MiniType.INT,
                        new BinaryExpr(name("index"), TokenType.PLUS, integer(1), RANGE)), RANGE),
                new ExprStmt(new AssignmentExpr(name("sum"), TokenType.EQUAL,
                        new BinaryExpr(name("sum"), TokenType.PLUS, deref("pointer"), RANGE), RANGE), RANGE),
                new ExprStmt(new UnaryExpr(TokenType.PLUS_PLUS, name("index"), RANGE), RANGE)), RANGE);
        var program = program(List.of(new VarDeclStmt("index", MiniType.INT, integer(0), RANGE),
                new VarDeclStmt("sum", MiniType.INT, integer(0), RANGE),
                new WhileStmt(new BinaryExpr(name("index"), TokenType.LESS, integer(3), RANGE), body, RANGE),
                new ReturnStmt(name("sum"), RANGE)));
        var ir = lower(program);
        assertEquals(4, declarations(ir));
        assertEquals(6, execute(ir));
    }

    @Test void sizeofChecksMaterializationTypesWithoutAllocatingOrCalling() {
        var prototype = new FunctionDecl("declared", MiniType.INT, List.of(), false, null, false, RANGE);
        var call = new CallExpr(name("declared"), List.of(), RANGE);
        var operand = new UnaryExpr(TokenType.STAR, materialize(MiniType.INT, call), RANGE);
        var main = main(List.of(new ReturnStmt(new SizeofExpr(operand, null, RANGE), RANGE)));
        var ir = lower(new Program(List.of(), List.of(prototype, main), RANGE));
        assertEquals(0, declarations(ir));
        assertTrue(ir.externalFunctionNames().isEmpty());
        assertEquals(4, execute(ir));
    }

    @Test void aggregateMaterializationOwnsACopyOfTheWholeObject() {
        var type = MiniType.struct("Box");
        var record = new StructDecl("Box", List.of(new StructField("value", MiniType.INT, RANGE)), RANGE);
        var statements = List.<Statement>of(
                new VarDeclStmt("original", type, new AggregateInitExpr(List.of(integer(3)), RANGE), RANGE),
                new VarDeclStmt("pointer", type.pointerTo(), materialize(type, name("original")), RANGE),
                new ExprStmt(new AssignmentExpr(new FieldAccessExpr(name("original"), "value", false, RANGE),
                        TokenType.EQUAL, integer(9), RANGE), RANGE),
                new ReturnStmt(new FieldAccessExpr(name("pointer"), "value", true, RANGE), RANGE));
        var ir = lower(new Program(List.of(record), List.of(main(statements)), RANGE));
        assertEquals(3, declarations(ir));
        assertEquals(3, execute(ir));
    }

    @Test void bracedScalarMaterializationUsesItsExplicitTargetType() {
        var temporary=materialize(constant(MiniType.SHORT),new AggregateInitExpr(List.of(integer(7)),RANGE));
        assertEquals(7,execute(lower(program(List.of(new ReturnStmt(new UnaryExpr(TokenType.STAR,temporary,RANGE),RANGE))))));
    }

    @Test void bracedArrayMaterializationInitializesElementsAndZeroFillsOnlyTheRemainder() {
        var type=constant(MiniType.INT).arrayOf(3);
        var temporary=materialize(type,new AggregateInitExpr(List.of(integer(4),integer(6)),RANGE));
        var array=new UnaryExpr(TokenType.STAR,name("pointer"),RANGE);
        var first=new IndexExpr(array,integer(0),RANGE);
        var second=new IndexExpr(array,integer(1),RANGE);
        var last=new IndexExpr(array,integer(2),RANGE);
        var total=new BinaryExpr(new BinaryExpr(first,TokenType.PLUS,second,RANGE),TokenType.PLUS,last,RANGE);
        var ir=lower(program(List.of(new VarDeclStmt("pointer",type.pointerTo(),temporary,RANGE),new ReturnStmt(total,RANGE))));
        assertEquals(10,execute(ir));
        assertEquals(2,declarations(ir));
    }

    @Test void transparentGroupingPreservesFirstInitializationTarget() {
        var type=MiniType.struct("Pair");
        var record=new StructDecl("Pair",List.of(new StructField("first",MiniType.INT,RANGE),new StructField("last",MiniType.INT,RANGE)),RANGE);
        var temporary=materialize(type,new GroupingExpr(new AggregateInitExpr(List.of(integer(9)),RANGE),RANGE));
        var body=List.<Statement>of(new VarDeclStmt("pointer",type.pointerTo(),temporary,RANGE),
                new ReturnStmt(new BinaryExpr(new FieldAccessExpr(name("pointer"),"first",true,RANGE),TokenType.PLUS,
                        new FieldAccessExpr(name("pointer"),"last",true,RANGE),RANGE),RANGE));
        assertEquals(9,execute(lower(new Program(List.of(record),List.of(main(body)),RANGE))));
    }

    @Test void excessBracedMaterializationElementsRemainErrors() {
        var temporary=materialize(MiniType.INT.arrayOf(1),new AggregateInitExpr(List.of(integer(1),integer(2)),RANGE));
        var semantic=new SemanticAnalyzer(program(List.of(new ExprStmt(temporary,RANGE),new ReturnStmt(integer(0),RANGE))));
        semantic.analyze();assertFalse(semantic.succeeded());
    }

    @Test void materializedConstStorageCannotBeModifiedAndInvalidConversionsAreRejected() {
        var type = constant(MiniType.INT);
        var program = program(List.of(new VarDeclStmt("pointer", type.pointerTo(), materialize(type, integer(3)), RANGE),
                new ExprStmt(new AssignmentExpr(deref("pointer"), TokenType.EQUAL, integer(4), RANGE), RANGE),
                new ReturnStmt(integer(0), RANGE)));
        var semantic = new SemanticAnalyzer(program);
        semantic.analyze();
        assertFalse(semantic.succeeded());
        var incompatible = program(List.of(new ReturnStmt(new UnaryExpr(TokenType.STAR,
                materialize(MiniType.INT.pointerTo(), integer(7)), RANGE), RANGE)));
        var bad = new SemanticAnalyzer(incompatible);
        bad.analyze();
        assertFalse(bad.succeeded());
    }

    private static MiniType constant(MiniType type) { return MiniType.qualified(type, Set.of(MiniType.TypeQualifier.CONST)); }
    private static MaterializeExpr materialize(MiniType type, Expression initializer) {
        return new MaterializeExpr(type, initializer,
                new TemporaryLifetime(TemporaryLifetime.Kind.FULL_EXPRESSION, initializer), RANGE);
    }
    private static NameExpr name(String name) { return new NameExpr(name, RANGE); }
    private static UnaryExpr deref(String name) { return new UnaryExpr(TokenType.STAR, name(name), RANGE); }
    private static IntegerLiteralExpr integer(int value) { return new IntegerLiteralExpr(value, Integer.toString(value), RANGE); }
    private static FunctionDecl main(List<Statement> statements) {
        return new FunctionDecl("main", MiniType.INT, List.of(), false, new BlockStmt(statements, RANGE), false, RANGE);
    }
    private static Program program(List<Statement> statements) { return new Program(List.of(), List.of(main(statements)), RANGE); }
    private static IrResult lower(Program program) {
        var semantic = new SemanticAnalyzer(program);
        semantic.analyze();
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        return new IrLowerer(program, semantic.semanticResult()).lower();
    }
    private static long declarations(IrResult ir) {
        return ir.findFunction("main").orElseThrow().blocks().stream().flatMap(block -> block.instructions().stream())
                .filter(IrDeclareLocalInstruction.class::isInstance).count();
    }
    private static long execute(IrResult ir) {
        var debug = DebugApi.fromIr(new SourceFile("materialization.mc", "int main(){return 0;}"), ir, "");
        for (int count=0; debug.canNext() && count<1000; count++) {
            debug.next();
            assertTrue(debug.current().runtime().heap().isEmpty());
        }
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        return debug.current().runtime().returnValue().integer();
    }
}
