package minic.compiler.ir;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.execute.ExecutableRunner;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.lexer.Lexer;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** __cleanup/__construct/__materialize/__capture are test AST markers, never source syntax. */
@Timeout(60)
class CleanupExprTest {
    @TempDir Path temporary;
    private static final MiniType BOX=MiniType.struct("Box");
    private static final String PREFIX="""
            int trace=0;
            void mark(int n){trace=trace*10+n;}
            void set(int*p,int n){*p=n;}
            struct Box {int value;struct Box*self;};
            void construct(struct Box*p,int n){p->value=n;p->self=p;}
            int inspect(struct Box object){return 100*(object.self==&object)+object.value;}
            """;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("value-then-cleanup", "int main(){int value=3;int result=__cleanup(value,set(&value,9));return result*10+value;}",39),
                Arguments.of("parameter-snapshot", "int run(int value){return __cleanup(value,set(&value,9));}int main(){return run(3);}",3),
                Arguments.of("pointer-snapshot", "int main(){int x=3;int*p=&x;int*q=__cleanup(p,(void)(p=0));return *q;}",3),
                Arguments.of("void-result", "int main(){__cleanup(mark(1),mark(2));return trace;}",12),
                Arguments.of("nested-cleanups-reverse", "int main(){int result=__cleanup(__cleanup(3,mark(2)),mark(1));return trace*10+result;}",213),
                Arguments.of("unselected-conditional", "int main(){int result=0?__cleanup(3,mark(9)):__cleanup(4,mark(2));return trace*10+result;}",24),
                Arguments.of("short-circuit-skips-cleanup", "int main(){int a=0&&__cleanup(3,mark(9));int b=1||__cleanup(4,mark(8));return trace+b;}",1),
                Arguments.of("sizeof-does-not-run-cleanup", "int main(){int n=sizeof(__cleanup(3,mark(9)));return trace+n;}",4),
                Arguments.of("loop-reentry", "int main(){int i=0;int sum=0;while(i<3){sum+=__cleanup(++i,mark(i));}return trace+sum;}",129),
                Arguments.of("outer-capture-restored-before-cleanup", "int run(){return __capture(5,__cleanup(__capture(3,capture),mark(capture)));}int main(){int result=run();return trace*10+result;}",53),
                Arguments.of("ordinary-record-value-snapshot", "int main(){struct Box original=__construct(3);struct Box copy=__cleanup(original,set(&original.value,9));return copy.value*100+original.value;}",309),
                Arguments.of("record-expression-value-snapshot", "int main(){struct Box original=__construct(3);return __cleanup(original,set(&original.value,9)).value;}",3),
                Arguments.of("named-object-final-destination", "int main(){struct Box object=__cleanup(__construct(7),mark(1));return 100*(object.self==&object)+object.value+10*trace;}",117),
                Arguments.of("argument-final-destination", "int main(){int value=inspect(__cleanup(__construct(7),mark(1)));return value+10*trace;}",117),
                Arguments.of("return-final-destination", "struct Box make(){return __cleanup(__construct(7),mark(1));}int main(){struct Box object=__construct_call(make());return 100*(object.self==&object)+object.value+10*trace;}",117),
                Arguments.of("materialized-final-destination", "int main(){struct Box*p=__materialize(__cleanup(__construct(7),mark(1)));return 100*(p->self==p)+p->value+10*trace;}",117),
                Arguments.of("let-result-retains-final-destination", "int main(){struct Box object=__capture(7,__cleanup(__construct(capture),mark(1)));return 100*(object.self==&object)+object.value+10*trace;}",117),
                Arguments.of("cleanup-around-constructor-call", "struct Box make(){return __construct(7);}int main(){struct Box object=__construct_call(__cleanup(make(),mark(1)));return 100*(object.self==&object)+object.value+10*trace;}",117),
                Arguments.of("conditional-construction-only-selected-cleanup", "int main(){struct Box object=0?__cleanup(__construct(3),mark(9)):__cleanup(__construct(7),mark(1));return 100*(object.self==&object)+object.value+10*trace;}",117),
                Arguments.of("existing-copy-remains-a-copy", "int main(){struct Box original=__construct(3);struct Box copy=__cleanup(0?__construct(7):original,mark(1));return 100*(copy.self==&original)+copy.value+10*trace;}",113),
                Arguments.of("volatile-destination-through-let-and-cleanup", "int main(){struct Box original=__construct(3);volatile struct Box copy;__initialize(copy,__capture(0,__cleanup(original,mark(1))));return copy.value+10*trace;}",13));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void cleanupResultsAgreeInDebugAndBothNativeModes(String name,String source,int expected) {
        var input=prepare(PREFIX+source);var semantic=analyze(input);
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var ir=new IrLowerer(input.program(),semantic.semanticResult()).lower();
        if(name.equals("volatile-destination-through-let-and-cleanup")) {
            var copies=ir.functions().stream().flatMap(f->f.blocks().stream()).flatMap(b->b.instructions().stream())
                    .filter(minic.compiler.ir.instruction.MemoryInstruction.IrMemCopyInstruction.class::isInstance)
                    .map(minic.compiler.ir.instruction.MemoryInstruction.IrMemCopyInstruction.class::cast).toList();
            assertFalse(copies.isEmpty());assertTrue(copies.stream().allMatch(c->c.volatileAccess()));
        }
        var debug=DebugApi.fromIr(input.source(),ir,"");
        for(int step=0;debug.canNext()&&step<10000;step++)debug.next();
        assertFalse(debug.canNext());assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(expected,debug.current().runtime().returnValue().integer());
        for(var level:OptimizationLevel.values()) {
            var assembler=new Assembler(ir,level);var obj=new ObjBuilder(input.source(),assembler,temporary,name+level);
            var linker=new Linker(input.source(),obj,temporary,name+level);new CompilerApi(List.of(assembler,obj,linker)).run();
            assertTrue(linker.succeeded(),()->obj.errors()+" "+linker.errors());
            var runner=new ExecutableRunner(Duration.ofSeconds(5));
            var result=runner.run(input.source(),linker.result().executableArtifactOptional().orElseThrow());
            assertTrue(runner.errors().isEmpty(),()->runner.errors().toString());assertEquals(expected,result.exitCode(),level.toString());
        }
    }

    @Test void cleanupMustBeVoidAndCannotCaptureNamesScopedOnlyInTheValue() {
        for(String expression:List.of("__cleanup(3,4)","__cleanup(__capture(3,capture),mark(capture))")) {
            var semantic=analyze(prepare(PREFIX+"int main(){return "+expression+";}"));
            assertFalse(semantic.succeeded());
        }
    }

    @Test void cleanupValueIsNotAnAssignmentOrAddressableTarget() {
        for(String expression:List.of("&__cleanup(3,mark(1))","__assign(__cleanup(3,mark(1)),9)")) {
            var input=prepare(PREFIX+"int main(){"+expression+";return 0;}");
            assertFalse(analyze(input).succeeded());
        }
    }

    @Test void resultMetadataAndReplayPreserveTheOriginalCoreExpression() {
        var input=prepare(PREFIX+"int main(){return __cleanup(3,mark(2));}");
        var cleanup=(CleanupExpr)((ReturnStmt)input.program().functions().getLast().body().statements().getFirst()).expression();
        assertEquals(List.of(cleanup.value(),cleanup.cleanup()),AstChildren.of(cleanup));
        assertNull(AstChildren.firstCppSyntax(cleanup));
        assertThrows(NullPointerException.class,()->new CleanupExpr(null,cleanup.cleanup(),cleanup.range()));
        var semantic=analyze(input);assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        assertEquals(MiniType.INT,semantic.semanticResult().typeOf(cleanup).orElseThrow());
        var ir=new IrLowerer(input.program(),semantic.semanticResult()).lower();
        assertTrue(ir.functions().getLast().blocks().stream().flatMap(b->b.instructions().stream()).anyMatch(i->i.range().equals(cleanup.cleanup().range())));
        var debug=DebugApi.fromIr(input.source(),ir,"");var history=new ArrayList<Debugger.Context>();history.add(debug.current());
        for(int step=0;debug.canNext()&&step<1000;step++)history.add(debug.next());
        assertFalse(debug.canNext());assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status());
        assertEquals(3,debug.current().runtime().returnValue().integer());
        for(int i=history.size()-2;i>=0;i--)assertSame(history.get(i),debug.previous());
        for(int i=1;i<history.size();i++)assertSame(history.get(i),debug.next());
        assertEquals(3,debug.current().runtime().returnValue().integer());
    }

    private record Input(SourceFile source,Program program) {}
    private static SemanticAnalyzer analyze(Input input){var semantic=new SemanticAnalyzer(input.program());semantic.analyze();return semantic;}
    private static Input prepare(String text) {
        var source=new SourceFile("cleanup-expression.c",text);var parser=new Parser(new Lexer(source).lex().tokens());parser.parse();
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        var old=parser.result().program();var functions=old.functions().stream().map(f->new FunctionDecl(f.name(),f.returnType(),f.parameters(),f.variadic(),
                f.body()==null?null:(BlockStmt)stmt(f.body()),f.external(),f.noReturn(),f.range())).toList();
        var declarations=old.declarations().stream().map(d->d instanceof FunctionDecl f?functions.get(old.functions().indexOf(f)):d).toList();
        return new Input(source,new Program(old.structs(),old.enums(),old.typedefs(),old.globals(),functions,declarations,old.languageMode(),old.range()));
    }
    private static Statement stmt(Statement statement) {
        if(statement instanceof BlockStmt s)return new BlockStmt(s.statements().stream().map(CleanupExprTest::stmt).toList(),s.range());
        if(statement instanceof ReturnStmt s)return new ReturnStmt(expr(s.expression()),s.range());
        if(statement instanceof ExprStmt s)return new ExprStmt(expr(s.expression()),s.range());
        if(statement instanceof VarDeclStmt s)return new VarDeclStmt(s.name(),s.type(),expr(s.initializer()),s.range());
        if(statement instanceof IfStmt s)return new IfStmt(expr(s.condition()),stmt(s.thenBranch()),s.elseBranch()==null?null:stmt(s.elseBranch()),s.range());
        if(statement instanceof WhileStmt s)return new WhileStmt(expr(s.condition()),stmt(s.body()),s.range());
        return statement;
    }
    private static Expression expr(Expression value) {
        if(value==null)return null;var r=value.range();
        if(value instanceof CallExpr call) {
            var arguments=call.arguments().stream().map(CleanupExprTest::expr).toList();
            if(call.hasDirectCalleeName())switch(call.calleeName()) {
                case "__cleanup":return new CleanupExpr(arguments.get(0),arguments.get(1),r);
                case "__assign":return new AssignmentExpr(arguments.get(0),TokenType.EQUAL,arguments.get(1),r);
                case "__initialize":return new InitializeExpr(arguments.get(0),arguments.get(1),r);
                case "__capture":return new LetExpr("capture",MiniType.INT,arguments.get(0),arguments.get(1),r);
                case "__construct":return new ObjectInitExpr(BOX,"destination",new CallExpr(new NameExpr("construct",r),List.of(new NameExpr("destination",r),arguments.getFirst()),r),r);
                case "__construct_call":return new ObjectInitExpr(BOX,"destination",new InitializeExpr(new UnaryExpr(TokenType.STAR,new NameExpr("destination",r),r),arguments.getFirst(),r),r);
                case "__materialize":return new MaterializeExpr(BOX,arguments.getFirst(),new TemporaryLifetime(TemporaryLifetime.Kind.FULL_EXPRESSION,value),r);
            }
            return new CallExpr(expr(call.callee()),arguments,r);
        }
        if(value instanceof GroupingExpr s)return new GroupingExpr(expr(s.expression()),r);
        if(value instanceof UnaryExpr s)return new UnaryExpr(s.operator(),expr(s.operand()),r);
        if(value instanceof BinaryExpr s)return new BinaryExpr(expr(s.left()),s.operator(),expr(s.right()),r);
        if(value instanceof AssignmentExpr s)return new AssignmentExpr(expr(s.target()),s.operator(),expr(s.value()),r);
        if(value instanceof ConditionalExpr s)return new ConditionalExpr(expr(s.condition()),expr(s.thenExpression()),expr(s.elseExpression()),r);
        if(value instanceof FieldAccessExpr s)return new FieldAccessExpr(expr(s.target()),s.fieldName(),s.viaPointer(),r);
        if(value instanceof CastExpr s)return new CastExpr(s.targetType(),expr(s.operand()),r);
        if(value instanceof SizeofExpr s)return new SizeofExpr(expr(s.expression()),s.queriedType(),r);
        if(value instanceof CommaExpr s)return new CommaExpr(s.expressions().stream().map(CleanupExprTest::expr).toList(),r);
        return value;
    }
}
