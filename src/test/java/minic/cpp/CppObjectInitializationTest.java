package minic.cpp;

import minic.SourceRange;
import minic.compiler.*;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.optimize.*;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.debug.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Core construction primitive. Source constructor binding is a separate frontend step. */
@Timeout(60)
final class CppObjectInitializationTest {
    private static final SourceRange R = new SourceRange(1,0,1,12);
    private static final MiniType BOX = MiniType.struct("Box");
    @TempDir Path temporary;

    @Test void metadataTraversesTheBodyButDoesNotExposeTheCaptureAsASourceVariable() {
        var init = initialize(7);
        assertEquals(List.of(init.body()), AstChildren.of(init));
        assertNull(AstChildren.firstCppSyntax(init));
        var reference = new ObjectInitExpr(BOX.referenceTo(), "destination", init.body(), R);
        assertSame(reference, AstChildren.firstReferenceSyntax(reference));
        assertThrows(IllegalArgumentException.class, () -> new ObjectInitExpr(BOX, "", init.body(), R));
        var semantic = analyze(program(List.of(new VarDeclStmt("object", BOX, init, R), new ReturnStmt(integer(0), R))));
        assertTrue(semantic.succeeded(), () -> semantic.errors().toString());
        assertTrue(semantic.semanticResult().globalScope().children().stream()
                .flatMap(scope -> scope.symbols().stream()).noneMatch(symbol -> symbol.name().equals("destination")));
    }

    @Test void namedObjectIsTheConstructorDestinationWithoutACopy() throws Exception {
        var init = initialize(7);
        var ir = lower(program(List.of(new VarDeclStmt("object", BOX, init, R),
                new ReturnStmt(sum(field("object", "value", false), product(integer(100),
                        equals(field("object", "self", false), address(name("object"))))), R))));
        assertEquals(1, declarations(ir));
        assertNoCopies(ir);
        agree(ir,107);
    }

    @Test void materializationPassesItsOwnAddressToConstruction() throws Exception {
        var init = initialize(9);
        var materialized = new MaterializeExpr(BOX, new GroupingExpr(init,R),
                new TemporaryLifetime(TemporaryLifetime.Kind.FULL_EXPRESSION,init),R);
        var ir = lower(program(List.of(new VarDeclStmt("pointer",BOX.pointerTo(),materialized,R),
                new ReturnStmt(sum(field("pointer","value",true),product(integer(100),
                        equals(field("pointer","self",true),name("pointer")))),R))));
        assertEquals(2,declarations(ir));
        assertNoCopies(ir);
        agree(ir,109);
    }

    @Test void aConstObjectCanBeConstructedAtItsFinalAddress() throws Exception {
        MiniType constant = MiniType.qualified(BOX,Set.of(MiniType.TypeQualifier.CONST));
        var init = new ObjectInitExpr(constant,"destination",initialize(11).body(),R);
        var ir = lower(program(List.of(new VarDeclStmt("object",constant,init,R),
                new ReturnStmt(sum(field("object","value",false),product(integer(100),
                        equals(field("object","self",false),address(name("object"))))),R))));
        assertEquals(1,declarations(ir));assertNoCopies(ir);agree(ir,111);
        assertFalse(analyze(program(List.of(new VarDeclStmt("object",constant,init,R),
                new ExprStmt(new AssignmentExpr(field("object","value",false),TokenType.EQUAL,integer(12),R),R),
                new ReturnStmt(integer(0),R)))).succeeded());
    }

    @Test void twoObjectsHaveDistinctStableAddresses() throws Exception {
        var ir = lower(program(List.of(new VarDeclStmt("first",BOX,initialize(3),R),
                new VarDeclStmt("second",BOX,new GroupingExpr(initialize(5),R),R),
                new ReturnStmt(sum(sum(field("first","value",false),field("second","value",false)),
                        product(integer(100),sum(equals(field("first","self",false),address(name("first"))),
                                equals(field("second","self",false),address(name("second")))))),R))));
        assertEquals(2,declarations(ir)); assertNoCopies(ir); agree(ir,208);
    }

    @Test void sizeofDoesNotRunConstructionOrAllocateStorage() throws Exception {
        var ir = lower(program(List.of(new ReturnStmt(new SizeofExpr(initialize(7),null,R),R))));
        assertEquals(0,declarations(ir)); assertNoCopies(ir); agree(ir,16);
    }

    @Test void constructionInAnUnselectedBranchDoesNotRun() throws Exception {
        var init = initialize(new BinaryExpr(integer(1),TokenType.SLASH,integer(0),R));
        var expression = new ConditionalExpr(integer(0), new FieldAccessExpr(init,"value",false,R), integer(12),R);
        agree(lower(program(List.of(new ReturnStmt(expression,R)))),12);
    }

    @Test void destinationCaptureCannotBeReassignedOrHaveItsAddressTaken() {
        for (Expression body : List.of(new AssignmentExpr(name("destination"),TokenType.EQUAL,new NullLiteralExpr("NULL",R),R),
                address(name("destination")))) {
            var initializer = new ObjectInitExpr(BOX,"destination",new CastExpr(MiniType.VOID,body,R),R);
            assertFalse(analyze(program(List.of(new VarDeclStmt("object",BOX,initializer,R),new ReturnStmt(integer(0),R)))).succeeded());
        }
    }

    @Test void constructionRequiresACompleteRecordAndVoidAction() {
        for (ObjectInitExpr init : List.of(new ObjectInitExpr(MiniType.INT,"destination",new CastExpr(MiniType.VOID,integer(1),R),R),
                new ObjectInitExpr(MiniType.struct("Missing"),"destination",new CastExpr(MiniType.VOID,integer(1),R),R),
                new ObjectInitExpr(BOX,"destination",integer(1),R))) {
            var semantic = analyze(program(List.of(new ExprStmt(init,R),new ReturnStmt(integer(0),R))));
            assertFalse(semantic.succeeded());
        }
    }

    @Test void loopConstructionReusesStorageButEvaluatesArgumentsOnceEachIteration() throws Exception {
        var loop = new BlockStmt(List.of(new VarDeclStmt("object",BOX,
                initialize(new UnaryExpr(TokenType.PLUS_PLUS,name("count"),R)),R),
                new ExprStmt(new AssignmentExpr(name("total"),TokenType.EQUAL,
                        sum(name("total"),field("object","value",false)),R),R)),R);
        var ir = lower(program(List.of(new VarDeclStmt("count",MiniType.INT,integer(0),R),
                new VarDeclStmt("total",MiniType.INT,integer(0),R),
                new WhileStmt(new BinaryExpr(name("count"),TokenType.LESS,integer(3),R),loop,R),
                new ReturnStmt(name("total"),R))));
        assertEquals(3,declarations(ir)); assertNoCopies(ir); agree(ir,6);
    }

    @Test void nestedConstructionRestoresTheOuterDestinationCapture() throws Exception {
        var inner = new CastExpr(MiniType.VOID,initialize(2),R);
        var outer = new ObjectInitExpr(BOX,"destination",new CommaExpr(List.of(inner,initialize(7).body()),R),R);
        var ir = lower(program(List.of(new VarDeclStmt("object",BOX,outer,R),new ReturnStmt(sum(field("object","value",false),
                product(integer(100),equals(field("object","self",false),address(name("object"))))),R))));
        assertEquals(2,declarations(ir)); assertNoCopies(ir); agree(ir,107);
    }

    @Test void constructionDoesNotSilentlyZeroInitializeUntouchedMembers() {
        var init = new ObjectInitExpr(BOX,"destination",new CastExpr(MiniType.VOID,integer(0),R),R);
        var ir = lower(program(List.of(new VarDeclStmt("object",BOX,init,R),new ReturnStmt(field("object","value",false),R))));
        var debug = DebugApi.fromIr(new SourceFile("uninitialized.mc","int main(){return 0;}"),ir,"");
        for(int i=0;debug.canNext()&&i<1000;i++)debug.next();
        assertEquals(Debugger.Status.FAILED,debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("uninitialized"),debug.current().stop()::error);
    }

    @Test void aggregateListConstructionWaitsForSubobjectInitializationRules() {
        // The legacy C aggregate path zeroes the entire aggregate before writing its values.
        // It must not pre-initialize members which a C++ constructor deliberately leaves untouched.
        var outer = new StructDecl("Outer",List.of(new StructField("member",BOX,R)),R);
        var object = new ObjectInitExpr(BOX,"destination",new CastExpr(MiniType.VOID,integer(0),R),R);
        for (Expression initial : List.of(new GroupingExpr(object,R),new CommaExpr(List.of(integer(0),object),R),
                new ConditionalExpr(integer(1),object,object,R))) {
        var value = program(List.of(new VarDeclStmt("object",MiniType.struct("Outer"),
                new AggregateInitExpr(List.of(initial),R),R),new ReturnStmt(integer(0),R)));
        var records = new ArrayList<>(value.structs());records.add(outer);
        var result = analyze(new Program(records,value.functions(),R));
        assertFalse(result.succeeded());
        assertTrue(result.errors().stream().anyMatch(error->error.message().contains("构造")),()->result.errors().toString());
        }
    }

    @ParameterizedTest @ValueSource(strings={"comma","then","else","materialized"})
    void resultPathsConstructInTheFinalDestination(String form) throws Exception {
        Expression invalid = new BinaryExpr(integer(1),TokenType.SLASH,integer(0),R);
        Expression expression = switch(form) {
            case "comma" -> new CommaExpr(List.of(integer(0),initialize(7)),R);
            case "then" -> new ConditionalExpr(integer(1),initialize(7),initialize(invalid),R);
            default -> new ConditionalExpr(integer(0),initialize(invalid),initialize(7),R);
        };
        IrResult ir;
        if (form.equals("materialized")) {
            var materialized = new MaterializeExpr(BOX,expression,new TemporaryLifetime(TemporaryLifetime.Kind.FULL_EXPRESSION,expression),R);
            ir = lower(program(List.of(new VarDeclStmt("pointer",BOX.pointerTo(),materialized,R),
                    new ReturnStmt(sum(field("pointer","value",true),product(integer(100),
                            equals(field("pointer","self",true),name("pointer")))),R))));
            assertEquals(2,declarations(ir));
        } else {
            ir = lower(program(List.of(new VarDeclStmt("object",BOX,expression,R),
                    new ReturnStmt(sum(field("object","value",false),product(integer(100),
                            equals(field("object","self",false),address(name("object"))))),R))));
            assertEquals(1,declarations(ir));
        }
        assertNoCopies(ir);agree(ir,107);
    }

    @ParameterizedTest @ValueSource(ints={0,1})
    void mixedResultPathCopiesExistingObjectsButConstructsNewObjectsInPlace(int condition) throws Exception {
        var expression = new ConditionalExpr(integer(condition),initialize(7),name("original"),R);
        var expectedAddress = condition == 0 ? address(name("original")) : address(name("object"));
        var ir = lower(program(List.of(new VarDeclStmt("original",BOX,initialize(3),R),
                new VarDeclStmt("object",BOX,expression,R),
                new ReturnStmt(sum(field("object","value",false),product(integer(100),
                        equals(field("object","self",false),expectedAddress))),R))));
        assertEquals(2,declarations(ir));
        assertEquals(1,ir.findFunction("main").orElseThrow().blocks().stream()
                .flatMap(block->block.instructions().stream()).filter(IrMemCopyInstruction.class::isInstance).count());
        agree(ir,condition == 0 ? 103 : 107);
    }

    @Test void commaPrefixIsEvaluatedOnceBeforeConstruction() throws Exception {
        var expression = new CommaExpr(List.of(new UnaryExpr(TokenType.PLUS_PLUS,name("count"),R),
                initialize(name("count"))),R);
        var ir = lower(program(List.of(new VarDeclStmt("count",MiniType.INT,integer(0),R),
                new VarDeclStmt("object",BOX,expression,R),
                new ReturnStmt(sum(sum(field("object","value",false),name("count")),product(integer(100),
                        equals(field("object","self",false),address(name("object"))))),R))));
        assertEquals(2,declarations(ir));assertNoCopies(ir);agree(ir,102);
    }

    @ParameterizedTest @ValueSource(strings={"direct","group","comma","conditional"})
    void returnedConstructionUsesTheCallersReturnStorage(String form) throws Exception {
        Expression invalid = new BinaryExpr(integer(1),TokenType.SLASH,integer(0),R);
        Expression expression = switch(form) {
            case "group" -> new GroupingExpr(initialize(7),R);
            case "comma" -> new CommaExpr(List.of(integer(0),initialize(7)),R);
            case "conditional" -> new ConditionalExpr(integer(0),initialize(invalid),initialize(7),R);
            default -> initialize(7);
        };
        var returned = new FunctionDecl("make",BOX,List.of(),false,
                new BlockStmt(List.of(new ReturnStmt(expression,R)),R),false,R);
        var self = new FieldAccessExpr(new CallExpr(name("make"),List.of(),R),"self",false,R);
        var base = program(List.of(new ReturnStmt(new FieldAccessExpr(self,"value",true,R),R)));
        var functions = new ArrayList<>(base.functions());functions.add(1,returned);
        var ir = lower(new Program(base.structs(),functions,R));
        // Returning a newly constructed value does not create a callee-local object
        // whose embedded pointers would dangle after the return.
        assertNoCopies(ir);
        assertTrue(ir.findFunction("make").orElseThrow().blocks().stream().flatMap(block->block.instructions().stream())
                .noneMatch(IrDeclareLocalInstruction.class::isInstance));
        agree(ir,7);
    }

    private static ObjectInitExpr initialize(int value) { return initialize(integer(value)); }
    private static ObjectInitExpr initialize(Expression value) {
        return new ObjectInitExpr(BOX,"destination",new CallExpr(name("construct"),List.of(name("destination"),value),R),R);
    }
    private static Program program(List<Statement> statements) {
        var record = new StructDecl("Box",List.of(new StructField("value",MiniType.INT,R),new StructField("self",BOX.pointerTo(),R)),R);
        var constructor = new FunctionDecl("construct",MiniType.VOID,List.of(new Parameter("pointer",BOX.pointerTo(),R),
                new Parameter("value",MiniType.INT,R)),false,new BlockStmt(List.of(
                new ExprStmt(new AssignmentExpr(field("pointer","value",true),TokenType.EQUAL,name("value"),R),R),
                new ExprStmt(new AssignmentExpr(field("pointer","self",true),TokenType.EQUAL,name("pointer"),R),R),new ReturnStmt(null,R)),R),false,R);
        var main = new FunctionDecl("main",MiniType.INT,List.of(),false,new BlockStmt(statements,R),false,R);
        return new Program(List.of(record),List.of(constructor,main),R);
    }
    private static SemanticAnalyzer analyze(Program program) {
        var semantic = new SemanticAnalyzer(program); semantic.analyze(); return semantic;
    }
    private static IrResult lower(Program program) {
        var semantic = analyze(program); assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var lowerer = new IrLowerer(program,semantic.semanticResult()); var ir = lowerer.lower();
        assertTrue(lowerer.succeeded(),()->lowerer.errors().toString()); IrVerifier.verify(ir); return ir;
    }
    private void agree(IrResult ir,int expected) throws Exception {
        var source = new SourceFile("object-init.mc","int main(){return 0;}");
        var debug = DebugApi.fromIr(source,ir,""); var history = new ArrayList<Debugger.Context>(); history.add(debug.current());
        for(int i=0;debug.canNext()&&i<1000;i++) { history.add(debug.next()); assertTrue(debug.current().runtime().heap().isEmpty()); }
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(expected,debug.current().runtime().termination().status());
        for(int i=history.size()-2;i>=0;i--)assertSame(history.get(i),debug.previous());
        for(int i=1;i<history.size();i++)assertSame(history.get(i),debug.next());
        for(var level:OptimizationLevel.values()) {
            var directory = temporary.resolve(level.name()); var asm = new Assembler(ir,level);
            var object = new ObjBuilder(source,asm,directory,"program"); var linker = new Linker(source,object,directory,"program");
            new CompilerApi(List.of(asm,object,linker)).runThrough(linker);
            assertTrue(linker.succeeded(),()->asm.errors()+" / "+object.errors()+" / "+linker.errors());
            var result = BoundedProcess.run(List.of(directory.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(10),65536);
            assertFalse(result.timedOut());assertFalse(result.outputExceeded());assertEquals(expected,result.exitCode(),result::stderr);
        }
    }
    private static long declarations(IrResult ir) {
        return ir.findFunction("main").orElseThrow().blocks().stream().flatMap(block->block.instructions().stream())
                .filter(IrDeclareLocalInstruction.class::isInstance).count();
    }
    private static void assertNoCopies(IrResult ir) {
        assertTrue(ir.functions().stream().flatMap(function->function.blocks().stream()).flatMap(block->block.instructions().stream())
                .noneMatch(IrMemCopyInstruction.class::isInstance));
    }
    private static NameExpr name(String name) { return new NameExpr(name,R); }
    private static IntegerLiteralExpr integer(int value) { return new IntegerLiteralExpr(value,Integer.toString(value),R); }
    private static UnaryExpr address(Expression expression) { return new UnaryExpr(TokenType.AMPERSAND,expression,R); }
    private static FieldAccessExpr field(String object,String member,boolean pointer) { return new FieldAccessExpr(name(object),member,pointer,R); }
    private static BinaryExpr sum(Expression a,Expression b) { return new BinaryExpr(a,TokenType.PLUS,b,R); }
    private static BinaryExpr product(Expression a,Expression b) { return new BinaryExpr(a,TokenType.STAR,b,R); }
    private static BinaryExpr equals(Expression a,Expression b) { return new BinaryExpr(a,TokenType.EQUAL_EQUAL,b,R); }
}
