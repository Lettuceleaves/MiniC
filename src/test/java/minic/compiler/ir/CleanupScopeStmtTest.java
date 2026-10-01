package minic.compiler.ir;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.execute.ExecutableRunner;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.lexer.Lexer;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
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

/** Hand-built core lifetime regions; __cleanup is a test AST marker, never product syntax. */
@Timeout(60)
class CleanupScopeStmtTest {
    @TempDir Path temporary;
    private static final String PREFIX="int trace=0;void mark(int digit){trace=trace*10+digit;}void set(int*p,int n){*p=n;}";

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("reverse-normal", "int main(){if(__cleanup(mark(1))){mark(2);if(__cleanup(mark(3))){mark(4);}}return trace;}",2431),
                Arguments.of("nested-return", "int run(){if(__cleanup(mark(1))){if(__cleanup(mark(2))){return 3;}}}int main(){int result=run();return trace*10+result;}",213),
                Arguments.of("void-return", "void run(){if(__cleanup(mark(2))){mark(1);return;}}int main(){run();return trace;}",12),
                Arguments.of("parameter-return-snapshot", "int run(int value){if(__cleanup(set(&value,9))){return value;}}int main(){return run(3);}",3),
                Arguments.of("converted-return-before-cleanup", "char run(int value){if(__cleanup(set(&value,1))){return value;}}int main(){return run(300);}",44),
                Arguments.of("sret-before-cleanup", "struct P{int x;};struct P run(){struct P p={3};if(__cleanup(set(&p.x,9))){return p;}}int main(){struct P p=run();return p.x;}",3),
                Arguments.of("cleanup-local-before-shadow", "int main(){int x=3;if(__cleanup(set(&x,9))){double x=2.0;}return x;}",9),
                Arguments.of("cleanup-global-before-shadow", "int value=3;int main(){if(__cleanup(set(&value,9))){int value=2;}return value;}",9),
                Arguments.of("cleanup-parameter-before-shadow", "int run(int value){if(__cleanup(set(&value,9))){double value=2.0;}return value;}int main(){return run(3);}",9),
                Arguments.of("break-and-continue", "int main(){for(int i=0;i<3;++i){if(__cleanup(mark(i+1))){if(i==0)continue;break;}}return trace;}",12),
                Arguments.of("switch-break-keeps-outer-region", "int main(){if(__cleanup(mark(1))){switch(1){case 1:if(__cleanup(mark(2))){break;}default:break;}mark(3);}return trace;}",231),
                Arguments.of("switch-continue-unwinds-body", "int main(){for(int i=0;i<2;++i){if(__cleanup(mark(i+1))){switch(i){case 0:if(__cleanup(mark(3))){continue;}default:break;}}}return trace;}",312),
                Arguments.of("for-initializer-region", "int main(){if(__cleanup(mark(4))){for(int i=0;i<2;++i){if(__cleanup(mark(i+1))){continue;}}mark(3);}return trace;}",1234),
                Arguments.of("unentered-region", "int main(){if(0){if(__cleanup(mark(9))){mark(8);}}return trace;}",0),
                Arguments.of("loop-region-reentry", "int main(){int i=0;while(i<3){if(__cleanup(mark(i))){++i;}}return trace;}",123),
                Arguments.of("dead-tail-not-cleaned-twice", "void run(){if(__cleanup(mark(1))){return;mark(8);if(__cleanup(mark(9))){mark(7);}}}int main(){run();return trace;}",1),
                Arguments.of("conditional-return-and-fallthrough", "int run(int flag){if(__cleanup(mark(2))){if(flag)return 3;mark(1);}return 4;}int main(){int a=run(1);int b=run(0);return trace;}",212));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void cleanupOrderAndSnapshotsAgreeInDebugAndBothNativeModes(String name,String body,int expected) {
        var input=prepare(PREFIX+body);
        var semantic=new SemanticAnalyzer(input.program()); semantic.analyze();
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var ir=new IrLowerer(input.program(),semantic.semanticResult()).lower();
        var debug=DebugApi.fromIr(input.source(),ir,"");
        for(int step=0;debug.canNext()&&step<10000;step++)debug.next();
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(expected,debug.current().runtime().returnValue().integer());
        for(var level:OptimizationLevel.values()) {
            var assembler=new Assembler(ir,level);
            var obj=new ObjBuilder(input.source(),assembler,temporary,name+level);
            var linker=new Linker(input.source(),obj,temporary,name+level);
            new CompilerApi(List.of(assembler,obj,linker)).run();
            assertTrue(linker.succeeded(),()->obj.errors()+" "+linker.errors());
            var runner=new ExecutableRunner(Duration.ofSeconds(5));
            var result=runner.run(input.source(),linker.result().executableArtifactOptional().orElseThrow());
            assertTrue(runner.errors().isEmpty(),()->runner.errors().toString());
            assertEquals(expected,result.exitCode(),level.toString());
        }
    }

    @Test void cleanupCannotSeeNamesDeclaredOnlyInsideItsBody() {
        var input=prepare(PREFIX+"int main(){if(__cleanup(set(&inside,9))){int inside=1;}return 0;}");
        var semantic=new SemanticAnalyzer(input.program()); semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d->d.message().contains("inside")),()->semantic.errors().toString());
    }

    @Test void cleanupMustBeAVoidAction() {
        var input=prepare("int main(){if(__cleanup(3)){return 0;}}");
        var semantic=new SemanticAnalyzer(input.program()); semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d->d.message().contains("void")),()->semantic.errors().toString());
    }

    @Test void cleanupBodyNamesDoNotLeakAfterTheRegion() {
        var input=prepare(PREFIX+"int main(){if(__cleanup(mark(1))){int inside=3;}return inside;}");
        var semantic=new SemanticAnalyzer(input.program()); semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d->d.message().contains("inside")),()->semantic.errors().toString());
    }

    @Test void wrappedBreakPreventsMisclassifyingAnInfiniteLoopAsNoreturn() {
        var input=prepare(PREFIX+"_Noreturn void run(){while(1){if(__cleanup(mark(1))){break;}}}int main(){return 0;}");
        var semantic=new SemanticAnalyzer(input.program()); semantic.analyze();
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(d->d.message().contains("noreturn")),()->semantic.errors().toString());
    }

    @Test void wrappedEndlessBodyStillSatisfiesNoreturn() {
        var input=prepare(PREFIX+"_Noreturn void run(){if(__cleanup(mark(1))){while(1){}}}int main(){return 0;}");
        var semantic=new SemanticAnalyzer(input.program()); semantic.analyze();
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
    }

    @Test void astChildrenSourceRangesAndReplayPreserveTheCleanupAction() {
        var input=prepare("int trace=0;void mark(int n){trace=n;}int main(){\nif(__cleanup(mark(9))){\n trace=3;\n}\nreturn trace;}");
        var node=(CleanupScopeStmt)input.program().functions().getLast().body().statements().getFirst();
        assertEquals(List.of(node.body(),node.cleanup()),AstChildren.of(node));
        assertNull(AstChildren.firstCppSyntax(node),"The cleanup node is core IR input, not source-only C++ syntax");
        assertThrows(NullPointerException.class,()->new CleanupScopeStmt(null,node.cleanup(),node.range()));
        var semantic=new SemanticAnalyzer(input.program()); semantic.analyze();
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        assertTrue(semantic.semanticResult().typeOf(node.cleanup()).orElseThrow().isVoid());
        var ir=new IrLowerer(input.program(),semantic.semanticResult()).lower();
        assertTrue(ir.functions().stream().flatMap(f->f.blocks().stream()).flatMap(b->b.instructions().stream())
                .anyMatch(i->i.range().equals(node.cleanup().range())));
        var debug=DebugApi.fromIr(input.source(),ir,""); var history=new ArrayList<Debugger.Context>();history.add(debug.current());
        for(int step=0;debug.canNext()&&step<1000;step++)history.add(debug.next());
        assertFalse(debug.canNext());assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status());
        assertEquals(9,debug.current().runtime().returnValue().integer());
        for(int i=history.size()-2;i>=0;i--)assertSame(history.get(i),debug.previous());
        for(int i=1;i<history.size();i++)assertSame(history.get(i),debug.next());
        assertEquals(9,debug.current().runtime().returnValue().integer());
    }

    private record Input(SourceFile source,Program program) {}
    private static Input prepare(String text) {
        var source=new SourceFile("cleanup.c",text);var lexer=new Lexer(source);var parser=new Parser(lexer.lex().tokens());parser.parse();
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        var old=parser.result().program();
        var functions=old.functions().stream().map(f->new FunctionDecl(f.name(),f.returnType(),f.parameters(),f.variadic(),
                f.body()==null?null:(BlockStmt)transform(f.body()),f.external(),f.noReturn(),f.range())).toList();
        var declarations=old.declarations().stream().map(d->d instanceof FunctionDecl f?functions.get(old.functions().indexOf(f)):d).toList();
        return new Input(source,new Program(old.structs(),old.enums(),old.typedefs(),old.globals(),functions,declarations,old.languageMode(),old.range()));
    }
    private static Statement transform(Statement statement) {
        if(statement instanceof BlockStmt s)return new BlockStmt(s.statements().stream().map(CleanupScopeStmtTest::transform).toList(),s.range());
        if(statement instanceof IfStmt s) {
            if(s.condition() instanceof CallExpr call&&call.hasDirectCalleeName()&&call.calleeName().equals("__cleanup"))
                return new CleanupScopeStmt(transform(s.thenBranch()),call.arguments().getFirst(),s.range());
            return new IfStmt(s.condition(),transform(s.thenBranch()),s.elseBranch()==null?null:transform(s.elseBranch()),s.range());
        }
        if(statement instanceof WhileStmt s)return new WhileStmt(s.condition(),transform(s.body()),s.range());
        if(statement instanceof DoWhileStmt s)return new DoWhileStmt(transform(s.body()),s.condition(),s.range());
        if(statement instanceof ForStmt s)return new ForStmt(s.initializer()==null?null:transform(s.initializer()),s.condition(),s.step(),transform(s.body()),s.range());
        if(statement instanceof SwitchStmt s)return new SwitchStmt(s.selector(),s.cases().stream().map(c->new SwitchCase(c.value(),c.statements().stream().map(CleanupScopeStmtTest::transform).toList(),c.range())).toList(),s.range());
        return statement;
    }
}
