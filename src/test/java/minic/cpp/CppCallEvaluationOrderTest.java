package minic.cpp;
import minic.SourceRange;
import minic.compiler.*;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.*;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class CppCallEvaluationOrderTest {
    static final SourceRange R=new SourceRange(1,0,1,10);
    @TempDir Path temporary;
    static CallExpr ordered(Expression callee,List<Expression> arguments,List<Integer> order){
        try{return CallExpr.class.getConstructor(Expression.class,List.class,List.class,SourceRange.class).newInstance(callee,arguments,order,R);}
        catch(java.lang.reflect.InvocationTargetException e){throw (RuntimeException)e.getCause();}
        catch(ReflectiveOperationException e){throw new AssertionError("CallExpr must retain a validated argument evaluation order",e);}
    }
    @Test void metadataIsImmutableAndOldConstructorKeepsArgumentOrder()throws Exception{
        var order=new ArrayList<>(List.of(1,0));var call=ordered(n("take"),List.of(i(1),i(2)),order);order.clear();
        var getter=CallExpr.class.getMethod("argumentEvaluationOrder");
        assertEquals(List.of(1,0),getter.invoke(call));
        assertEquals(List.of(0,1),getter.invoke(new CallExpr(n("take"),List.of(i(1),i(2)),R)));
        assertThrows(UnsupportedOperationException.class,()->((List<?>)getter.invoke(call)).clear());
        assertEquals(List.of(call.callee(),call.arguments().get(0),call.arguments().get(1)),AstChildren.of(call));
    }
    @Test void rejectsMissingRepeatedNegativeAndOutOfRangeIndices(){
        for(var order:List.of(List.of(0),List.of(0,0),List.of(-1,0),List.of(0,2)))
            assertThrows(IllegalArgumentException.class,()->ordered(n("take"),List.of(i(1),i(2)),order));
    }
    @Test void reverseEvaluationPreservesTheOriginalArgumentSlots()throws Exception{execute(false,2112);}
    @Test void indirectCalleeIsCapturedBeforeReorderedArguments()throws Exception{execute(true,92112);}
    void execute(boolean indirect,int expected)throws Exception{
        var trace=new GlobalVarDecl("trace",MiniType.INT,i(0),false,List.of(),R);
        var mark=new FunctionDecl("mark",MiniType.INT,List.of(new Parameter("n",MiniType.INT,R)),false,
            new BlockStmt(List.of(new ExprStmt(new AssignmentExpr(n("trace"),TokenType.EQUAL,
                add(mul(n("trace"),i(10)),n("n")),R),R),new ReturnStmt(n("n"),R)),R),false,R);
        var take=new FunctionDecl("take",MiniType.INT,List.of(new Parameter("a",MiniType.INT,R),new Parameter("b",MiniType.INT,R)),false,
            new BlockStmt(List.of(new ReturnStmt(add(mul(n("trace"),i(100)),add(mul(n("a"),i(10)),n("b"))),R)),R),false,R);
        var pointer=MiniType.function(MiniType.INT,List.of(MiniType.INT,MiniType.INT)).pointerTo();
        var get=new FunctionDecl("get",pointer,List.of(),false,new BlockStmt(List.of(
            new ExprStmt(new AssignmentExpr(n("trace"),TokenType.EQUAL,i(9),R),R),new ReturnStmt(n("take"),R)),R),false,R);
        Expression callee=indirect?new CallExpr(n("get"),List.of(),R):n("take");
        var call=ordered(callee,List.of(new CallExpr(n("mark"),List.of(i(1)),R),new CallExpr(n("mark"),List.of(i(2)),R)),List.of(1,0));
        var main=new FunctionDecl("main",MiniType.INT,List.of(),false,new BlockStmt(List.of(new ReturnStmt(call,R)),R),false,R);
        var program=new Program(List.of(),List.of(),List.of(),List.of(trace),List.of(mark,take,get,main),R);
        var semantic=new SemanticAnalyzer(program);semantic.analyze();assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var lower=new IrLowerer(program,semantic.semanticResult());var ir=lower.lower();assertTrue(lower.succeeded(),()->lower.errors().toString());IrVerifier.verify(ir);
        var source=new SourceFile("call-order.mc","int main(){return 0;}");var debug=DebugApi.fromIr(source,ir,"");
        for(int j=0;debug.canNext()&&j<1000;j++)debug.next();
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(expected,debug.current().runtime().termination().status());
        for(var level:OptimizationLevel.values()){
            var dir=temporary.resolve(level.name());var asm=new Assembler(ir,level);var obj=new ObjBuilder(source,asm,dir,"program");var link=new Linker(source,obj,dir,"program");
            new CompilerApi(List.of(asm,obj,link)).runThrough(link);assertTrue(link.succeeded(),()->asm.errors()+" / "+obj.errors()+" / "+link.errors());
            var run=BoundedProcess.run(List.of(dir.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(10),65536);
            assertEquals(expected,run.exitCode(),run::stderr);
        }
    }
    static NameExpr n(String name){return new NameExpr(name,R);}static IntegerLiteralExpr i(int value){return new IntegerLiteralExpr(value,""+value,R);}
    static BinaryExpr add(Expression a,Expression b){return new BinaryExpr(a,TokenType.PLUS,b,R);}static BinaryExpr mul(Expression a,Expression b){return new BinaryExpr(a,TokenType.STAR,b,R);}
}
