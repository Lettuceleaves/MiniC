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

/** Explicit first initialization of existing subobjects; legacy C declarations keep their path. */
@Timeout(60)
final class CppAggregateObjectInitializationTest {
    private static final SourceRange R = new SourceRange(1,0,1,12);
    private static final MiniType BOX = MiniType.struct("Box");
    @TempDir Path temporary;

    @ParameterizedTest @ValueSource(booleans={false,true})
    void arraysInitializeSpecifiedElementsAndThenZeroTheirRemainder(boolean isVolatile) throws Exception {
        MiniType type = MiniType.INT.arrayOf(4);
        if(isVolatile) type = MiniType.qualified(type,Set.of(MiniType.TypeQualifier.VOLATILE));
        var ir = lower(program(List.of(),List.of(new VarDeclStmt("items",type,null,R),
                init(name("items"),list(integer(3),integer(7))),
                new ReturnStmt(sum(sum(product(index(name("items"),0),integer(10)),index(name("items"),1)),
                        sum(index(name("items"),2),index(name("items"),3))),R))));
        var stores = ir.functions().getFirst().blocks().stream().flatMap(b->b.instructions().stream())
                .filter(IrStorePointerInstruction.class::isInstance).map(IrStorePointerInstruction.class::cast).toList();
        assertEquals(4,stores.size());assertTrue(stores.stream().allMatch(s->s.volatileAccess()==isVolatile));agree(ir,37);
    }

    @Test void nestedArraysAndBracedScalarsUseTheirDeclaredWidths() throws Exception {
        var type = MiniType.SHORT.arrayOf(2).arrayOf(2);
        var ir = lower(program(List.of(),List.of(new VarDeclStmt("matrix",type,null,R),
                init(name("matrix"),list(list(list(integer(3))),list(integer(4),integer(5)))),
                new ReturnStmt(sum(product(index(index(name("matrix"),0),0),integer(10)),
                        sum(index(index(name("matrix"),0),1),sum(index(index(name("matrix"),1),0),index(index(name("matrix"),1),1)))),R))));
        agree(ir,39);
    }

    @Test void nestedRecordAndArraySubobjectsAreConstructedAtTheirFinalAddresses() throws Exception {
        var outerType = MiniType.struct("Outer");
        var outer = new StructDecl("Outer",List.of(new StructField("first",BOX,R),new StructField("rest",BOX.arrayOf(2),R)),R);
        var first = field(name("object"),"first");
        var last = index(field(name("object"),"rest"),1);
        var ir = lower(program(List.of(box(),outer),List.of(new VarDeclStmt("object",outerType,null,R),
                init(name("object"),list(construct(integer(3)),list(construct(integer(4)),construct(integer(5))))),
                new ReturnStmt(sum(sum(field(first,"value"),field(last,"value")),product(integer(100),
                        equals(field(last,"self"),address(last)))),R))));
        assertTrue(ir.functions().getFirst().blocks().stream().flatMap(b->b.instructions().stream())
                .noneMatch(IrMemCopyInstruction.class::isInstance));agree(ir,108);
    }

    @Test void initializersRunInMemberOrderAndSeeOnlyAlreadyInitializedMembers() throws Exception {
        var type=MiniType.struct("Sequence");
        var record=new StructDecl("Sequence",List.of(new StructField("first",MiniType.INT,R),new StructField("next",MiniType.INT,R)),R);
        var ir=lower(program(List.of(record),List.of(new VarDeclStmt("count",MiniType.INT,integer(0),R),
                new VarDeclStmt("object",type,null,R),init(name("object"),list(
                        new UnaryExpr(TokenType.PLUS_PLUS,name("count"),R),sum(field(name("object"),"first"),name("count")))),
                new ReturnStmt(sum(product(field(name("object"),"first"),integer(10)),field(name("object"),"next")),R))));
        agree(ir,12);
    }

    @Test void constructorUnwrittenFieldsAreNotSilentlyZeroInitializedByTheirContainingList() {
        var ir=lower(program(List.of(box()),List.of(new VarDeclStmt("objects",BOX.arrayOf(1),null,R),
                init(name("objects"),list(construct(integer(3)))),
                new ReturnStmt(field(index(name("objects"),0),"untouched"),R))));
        var debug=DebugApi.fromIr(new SourceFile("aggregate-init.mc","int main(){return 0;}"),ir,"");
        for(int i=0;debug.canNext()&&i<1000;i++)debug.next();
        assertEquals(Debugger.Status.FAILED,debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("uninitialized"),debug.current().stop()::error);
    }

    @Test void constFieldsPermitFirstInitializationAndOmittedFieldsReceiveTypedZero() throws Exception {
        var type=MiniType.struct("Mixed");
        var constant=MiniType.qualified(MiniType.INT,Set.of(MiniType.TypeQualifier.CONST));
        var record=new StructDecl("Mixed",List.of(new StructField("value",constant,R),new StructField("fraction",MiniType.DOUBLE,R),
                new StructField("pointer",MiniType.INT.pointerTo(),R)),R);
        var result=sum(field(name("object"),"value"),sum(new CastExpr(MiniType.INT,field(name("object"),"fraction"),R),
                new CastExpr(MiniType.BOOL,field(name("object"),"pointer"),R)));
        agree(lower(program(List.of(record),List.of(new VarDeclStmt("object",type,null,R),
                init(name("object"),list(integer(9))),new ReturnStmt(result,R)))),9);
    }

    @Test void destinationAddressIsEvaluatedExactlyOnceBeforeTheElementInitializers() throws Exception {
        var type=MiniType.INT.arrayOf(2);
        var slot=new UnaryExpr(TokenType.STAR,new CommaExpr(List.of(
                new UnaryExpr(TokenType.PLUS_PLUS,name("count"),R),address(name("items"))),R),R);
        agree(lower(program(List.of(),List.of(new VarDeclStmt("count",MiniType.INT,integer(0),R),new VarDeclStmt("items",type,null,R),
                init(slot,list(new UnaryExpr(TokenType.PLUS_PLUS,name("count"),R),name("count"))),
                new ReturnStmt(sum(product(name("count"),integer(100)),sum(product(index(name("items"),0),integer(10)),index(name("items"),1))),R)))),222);
    }

    @Test void invalidElementCountsTypesAndArrayCopiesRemainRejected() {
        for(Expression value:List.of(list(integer(1),integer(2),integer(3)),list(name("other")),name("items"))) {
            var source=program(List.of(box()),List.of(new VarDeclStmt("items",MiniType.INT.arrayOf(2),null,R),
                    new VarDeclStmt("other",BOX,null,R),init(name("items"),value),new ReturnStmt(integer(0),R)));
            assertFalse(analyze(source).succeeded());
        }
    }

    @Test void positionalFirstInitializationRejectsUnionSelectionAndDesignatedPaths() {
        var union=new StructDecl("Overlay",List.of(new StructField("number",MiniType.INT,R),
                new StructField("other",MiniType.DOUBLE,R)),true,true,null,R);
        assertFalse(analyze(program(List.of(union),List.of(new VarDeclStmt("object",MiniType.struct("Overlay"),null,R),
                init(name("object"),list(integer(3))),new ReturnStmt(integer(0),R)))).succeeded());
        var designated=new DesignatedInitExpr(List.of(new Designator.Index(1,R)),integer(3),R);
        assertFalse(analyze(program(List.of(),List.of(new VarDeclStmt("items",MiniType.INT.arrayOf(2),null,R),
                init(name("items"),list(designated)),new ReturnStmt(integer(0),R)))).succeeded());
        assertFalse(analyze(program(List.of(),List.of(new VarDeclStmt("items",MiniType.INT.arrayOf(1),null,R),
                init(name("items"),list(list(integer(1),integer(2)))),new ReturnStmt(integer(0),R)))).succeeded());
    }

    private static StructDecl box() {
        return new StructDecl("Box",List.of(new StructField("value",MiniType.INT,R),
                new StructField("self",BOX.pointerTo(),R),new StructField("untouched",MiniType.INT,R)),R);
    }
    private static ObjectInitExpr construct(Expression value) {
        return new ObjectInitExpr(BOX,"destination",new CommaExpr(List.of(
                new InitializeExpr(new FieldAccessExpr(name("destination"),"value",true,R),value,R),
                new InitializeExpr(new FieldAccessExpr(name("destination"),"self",true,R),name("destination"),R)),R),R);
    }
    private static Program program(List<StructDecl> records,List<Statement> statements) {
        return new Program(records,List.of(new FunctionDecl("main",MiniType.INT,List.of(),false,new BlockStmt(statements,R),false,R)),R);
    }
    private static SemanticAnalyzer analyze(Program program) { var analyzer=new SemanticAnalyzer(program);analyzer.analyze();return analyzer; }
    private static IrResult lower(Program program) {
        var semantic=analyze(program);assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var lowerer=new IrLowerer(program,semantic.semanticResult());var result=lowerer.lower();
        assertTrue(lowerer.succeeded(),()->lowerer.errors().toString());IrVerifier.verify(result);return result;
    }
    private void agree(IrResult ir,int expected) throws Exception {
        var source=new SourceFile("aggregate-init.mc","int main(){return 0;}");
        var debug=DebugApi.fromIr(source,ir,"");var history=new ArrayList<Debugger.Context>();history.add(debug.current());
        for(int i=0;debug.canNext()&&i<1000;i++)history.add(debug.next());
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(expected,debug.current().runtime().termination().status());
        for(int i=history.size()-2;i>=0;i--)assertSame(history.get(i),debug.previous());
        for(int i=1;i<history.size();i++)assertSame(history.get(i),debug.next());
        for(var level:OptimizationLevel.values()) {
            var directory=temporary.resolve(level.name());var asm=new Assembler(ir,level);
            var object=new ObjBuilder(source,asm,directory,"program");var linker=new Linker(source,object,directory,"program");
            new CompilerApi(List.of(asm,object,linker)).runThrough(linker);
            assertTrue(linker.succeeded(),()->asm.errors()+" / "+object.errors()+" / "+linker.errors());
            var output=BoundedProcess.run(List.of(directory.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(10),65536);
            assertFalse(output.timedOut());assertFalse(output.outputExceeded());assertEquals(expected,output.exitCode(),output::stderr);
        }
    }
    private static ExprStmt init(Expression target,Expression value){return new ExprStmt(new InitializeExpr(target,value,R),R);}
    private static AggregateInitExpr list(Expression... values){return new AggregateInitExpr(List.of(values),R);}
    private static NameExpr name(String value){return new NameExpr(value,R);}
    private static IntegerLiteralExpr integer(int value){return new IntegerLiteralExpr(value,Integer.toString(value),R);}
    private static IndexExpr index(Expression value,int index){return new IndexExpr(value,integer(index),R);}
    private static FieldAccessExpr field(Expression value,String field){return new FieldAccessExpr(value,field,false,R);}
    private static UnaryExpr address(Expression value){return new UnaryExpr(TokenType.AMPERSAND,value,R);}
    private static BinaryExpr sum(Expression a,Expression b){return new BinaryExpr(a,TokenType.PLUS,b,R);}
    private static BinaryExpr product(Expression a,Expression b){return new BinaryExpr(a,TokenType.STAR,b,R);}
    private static BinaryExpr equals(Expression a,Expression b){return new BinaryExpr(a,TokenType.EQUAL_EQUAL,b,R);}
}
