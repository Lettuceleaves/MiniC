package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.DestructorMember;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** F07 named automatic-object destruction. Arrays, global objects and temporary lifetimes are separate slices. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppDestructorExecutionTest {
    @TempDir Path temporary;

    private static final String TRACE = """
            #include <stdio.h>
            struct T {
                int id;
                T(int number):id(number){printf("C%d ",id);}
                ~T(){printf("D%d ",id);}
            };
            """;
    private static final String DEBUG_SOURCE = """
            #include <stdio.h>
            namespace Scope {
                struct Guard {
                    int id;
                    Guard(int number):id(number){printf("C%d ",id);}
                    ~Guard();
                };
            }
            Scope::Guard::~Guard(){
                printf("D%d ",this->id);
            }
            int main(){Scope::Guard guard(4);return 0;}
            """;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("nested-normal-exit-reverses-construction", TRACE + """
                        int main(){T first(1);{T second(2);T third(3);}printf("B ");return 0;}
                        """, "C1 C2 C3 D3 D2 B D1 "),
                Arguments.of("return-expression-before-both-cleanups", TRACE + """
                        int result(){printf("R ");return 7;}
                        int run(){T first(1);{T second(2);return result();}}
                        int main(){printf("V%d ",run());return 0;}
                        """, "C1 C2 R D2 D1 V7 "),
                Arguments.of("return-value-snapshotted-before-mutation", """
                        #include <stdio.h>
                        struct Change {int &value;Change(int &target):value(target){} ~Change(){value=9;}};
                        int run(){int value=3;Change guard(value);return value;}
                        int main(){printf("%d\\n",run());return 0;}
                        """, "3\n"),
                Arguments.of("reference-return-keeps-object-identity", """
                        #include <stdio.h>
                        int value=3;
                        struct Change {~Change(){value=9;}};
                        int &run(){Change guard;return value;}
                        int main(){int &alias=run();printf("%d %d\\n",alias,&alias==&value);return 0;}
                        """, "9 1\n"),
                Arguments.of("void-return-and-normal-fallthrough", TRACE + """
                        void run(int early){T first(1);if(early){T second(2);return;}printf("B ");}
                        int main(){run(1);run(0);return 0;}
                        """, "C1 C2 D2 D1 C1 B D1 "),
                Arguments.of("continue-and-break-leave-body-once", TRACE + """
                        int main(){for(int i=0;i<4;++i){T item(i+1);if(i==0)continue;if(i==2)break;printf("B%d ",i);}printf("E ");return 0;}
                        """, "C1 D1 C2 B1 D2 C3 D3 E "),
                Arguments.of("for-initializer-outlives-continue-and-dies-on-break", TRACE + """
                        int main(){for(T anchor(1);anchor.id<3;++anchor.id){T inner(10+anchor.id);if(anchor.id==1)continue;break;}printf("E ");return 0;}
                        """, "C1 C11 D11 C12 D12 D2 E "),
                Arguments.of("for-body-cleans-before-step-expression", TRACE + """
                        int step(int i){printf("S%d ",i);return i+1;}
                        int main(){for(int i=0;i<2;i=step(i)){T item(i+1);continue;}return 0;}
                        """, "C1 D1 S0 C2 D2 S1 "),
                Arguments.of("switch-break-keeps-outer-object-alive", TRACE + """
                        int main(){T outer(1);switch(1){case 1:{T inner(2);break;}default:break;}printf("B%d ",outer.id);return 0;}
                        """, "C1 C2 D2 B1 D1 "),
                Arguments.of("continue-crosses-switch-to-loop", TRACE + """
                        int main(){for(int i=0;i<2;++i){T outer(i+1);switch(i){case 0:{T inner(8);continue;}default:break;}printf("B ");}return 0;}
                        """, "C1 C8 D8 D1 C2 B D2 "),
                Arguments.of("unentered-scope-and-dead-declaration-have-no-cleanup", TRACE + """
                        void run(){if(0){T skipped(8);}return;T dead(9);}
                        int main(){run();printf("E ");return 0;}
                        """, "E "),
                Arguments.of("loop-reentry-constructs-and-destroys-fresh-object", TRACE + """
                        int main(){int i=0;while(i<3){T item(++i);}return 0;}
                        """, "C1 D1 C2 D2 C3 D3 "),
                Arguments.of("shadowed-object-names-keep-distinct-cleanups", TRACE + """
                        int main(){T object(1);{T object(2);}return 0;}
                        """, "C1 C2 D2 D1 "),
                Arguments.of("unbraced-substatement-has-its-own-lifetime", TRACE + """
                        int main(){if(1)T object(1);printf("B ");for(int i=0;i<2;++i)T object(i+2);return 0;}
                        """, "C1 D1 B C2 D2 C3 D3 "),
                Arguments.of("const-object-destruction-uses-mutable-this", """
                        #include <stdio.h>
                        class Box {int value;void finish(){value+=3;}
                        public:Box():value(4){} ~Box(){finish();printf("%d\\n",value);}};
                        int main(){const Box box;return 0;}
                        """, "7\n"),
                Arguments.of("destructor-body-locals-before-reversed-members", TRACE + """
                        struct Owner {T first;T second;Owner():second(2),first(1){} ~Owner(){printf("O ");T local(3);return;}};
                        int main(){Owner owner;return 0;}
                        """, "C1 C2 O C3 D3 D2 D1 "),
                Arguments.of("implicit-owner-destructor-cleans-members", TRACE + """
                        struct Owner {T first;T second;Owner():first(1),second(2){}};
                        int main(){Owner owner;return 0;}
                        """, "C1 C2 D2 D1 "),
                Arguments.of("qualified-out-of-line-destructor", DEBUG_SOURCE, "C4 D4 "));
    }

    @Test @Timeout(120)
    void independentGxxOracleAcceptsEveryProgramAndExpectedOrder() throws Exception {
        for (var arguments : programs().toList()) {
            Object[] fixture=arguments.get();
            Path source=temporary.resolve(fixture[0]+".cpp");
            Path executable=temporary.resolve(fixture[0]+".exe");
            Files.writeString(source,(String)fixture[1]);
            var compile=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                    "-std=c++17","-pedantic-errors",source.toString(),"-o",executable.toString()),
                    temporary,"",Duration.ofSeconds(20),65536);
            assertFalse(compile.timedOut(),compile::stderr);
            assertFalse(compile.outputExceeded());
            assertEquals(0,compile.exitCode(),fixture[0]+": "+compile.stderr());
            var run=BoundedProcess.run(List.of(executable.toString()),temporary,"",Duration.ofSeconds(5),65536);
            assertFalse(run.timedOut()); assertFalse(run.outputExceeded());
            assertEquals(0,run.exitCode(),run::stderr);
            assertEquals(fixture[2],run.stdout().replace("\r\n","\n"),fixture[0].toString());
        }
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void namedObjectDestructionAgreesAcrossThreeBackends(String name,String source,String expected) throws Exception {
        var api=compiler(source);
        var parser=stage(api,Parser.class);
        var semantic=stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        agree(temporary,name,source,expected);
    }

    static Stream<Arguments> invalidPrograms() {
        return Stream.of(
                Arguments.of("private-destructor", "class Box{~Box(){} };\nint main(){Box object; // bad\nreturn 0;}"),
                Arguments.of("protected-destructor", "struct Box{protected:~Box(){} };\nint main(){Box object; // bad\nreturn 0;}"),
                Arguments.of("undefined-used-destructor", "struct Box{~Box();};\nint main(){Box object; // bad\nreturn 0;}"),
                Arguments.of("duplicate-destructor-definition", "struct Box{~Box(){} };\nBox::~Box(){} // bad\nint main(){return 0;}"),
                Arguments.of("undeclared-out-of-line-destructor", "struct Box{};\nBox::~Box(){} // bad\nint main(){return 0;}"),
                Arguments.of("destructor-returning-value", "struct Box{~Box(){\nreturn 1; // bad\n}};int main(){return 0;}"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void illegalOrUndefinedDestructionHasSpecificSourceDiagnostics(String name,String source) throws Exception {
        Path file=temporary.resolve(name+".cpp"); Files.writeString(file,source);
        // An undefined used destructor is a link error, so this oracle deliberately links too.
        var reference=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                "-std=c++17","-pedantic-errors",file.toString(),"-o",temporary.resolve(name+".exe").toString()),
                temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(reference.timedOut()); assertFalse(reference.outputExceeded());
        assertNotEquals(0,reference.exitCode(),"G++ must reject this destruction operation");
        var api=compiler(source); var parser=stage(api,Parser.class); var semantic=stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        assertFalse(semantic.succeeded());
        int line=(int)source.substring(0,source.indexOf("// bad")).chars().filter(c->c=='\n').count()+1;
        assertTrue(semantic.errors().stream().anyMatch(d->d.range().startLine()==line && !d.code().equals("CPP005")),()->semantic.errors().toString());
    }

    @Test void anonymousRecordStillSynthesizesMemberDestruction() throws Exception {
        agree(temporary,"anonymous-record-destruction","""
                #include <stdio.h>
                struct T{int id;~T(){printf("D%d ",id);}};
                typedef struct {T member;} Owner;
                int main(){Owner owner={{7}};return 0;}
                ""","D7 ");
    }

    @Test void nontrivialUnionDoesNotSilentlySkipMemberDestruction() {
        var api=compiler("struct T{~T(){}}; union U{T member;}; int main(){U value;return 0;}");
        var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertFalse(semantic.succeeded(),"Nontrivial union active-member lifetime must be explicitly diagnosed until supported");
    }

    @Test void deletedImplicitDestructorIsDiagnosedAtObjectUse() {
        String source="class Hidden{~Hidden(){}}; struct Owner{Hidden member;};\nint main(){Owner value;return 0;}";
        var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.errors().stream().anyMatch(d->d.code().equals("CPP004")&&d.range().startLine()==2),()->semantic.errors().toString());
    }

    @Test void unusedPrivateAndUndefinedDestructorsRemainLegal() {
        var api=compiler("class Hidden{~Hidden();}; struct Owner{Hidden member;}; int main(){return 0;}");
        var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={
            "struct T{~T(){}}; T global; int main(){return 0;}",
            "struct T{~T(){}}; int main(){T array[2];return 0;}",
            "struct T{~T(){}}; void take(T value){} int main(){return 0;}",
            "struct T{~T(){}}; T make(); int main(){return 0;}",
            "struct T{~T(){}}; int main(){T{};return 0;}",
            "struct T{~T(){}}; int main(){const T &value=T{};return 0;}"
    })
    void deferredLifetimeFormsCannotSilentlyOmitDestruction(String source) {
        var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.errors().stream().anyMatch(d->d.code().equals("CPP005")),()->semantic.errors().toString());
    }

    @Test void destructorSourceIdentityMapsToOneMutableReceiverAndVoidCoreFunction() {
        var api=compiler(DEBUG_SOURCE); var parser=stage(api,Parser.class); var semantic=stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic); assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var definition=nodes(parser.result().program()).stream().filter(DestructorMember.class::isInstance)
                .map(DestructorMember.class::cast).filter(d->d.body()!=null).findFirst().orElseThrow();
        var core=assertInstanceOf(FunctionDecl.class,semantic.semanticResult().sourceToCore().get(definition));
        assertEquals(definition.range(),core.range());
        assertEquals(MiniType.VOID,core.returnType()); assertEquals(1,core.parameters().size());
        assertTrue(core.parameters().getFirst().type().isPointer());
        assertFalse(core.parameters().getFirst().type().pointee().isConstQualified());
        assertTrue(semantic.semanticResult().displayNames().containsValue("Scope::Guard::~Guard"));
        assertNotNull(semantic.semanticResult().sourceToCore().get(definition.body()));
    }

    @Test void debugDestructorSourceFramesAndHistoryRetainTheirOriginalIdentity() {
        var file=new SourceFile("destruction.cpp",DEBUG_SOURCE);
        var debug=new DebugApi(file,"",LanguageMode.CPP17_ALGORITHM);
        var history=new ArrayList<Debugger.Context>(); history.add(debug.current());
        for(int step=0;debug.canNext()&&step<2000;step++)history.add(debug.next());
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals("C4 D4 ",debug.current().runtime().stdout().replace("\r\n","\n"));
        assertTrue(history.stream().allMatch(c->c.runtime().heap().isEmpty()));
        var active=history.stream().filter(c->c.runtime().stack().stream().anyMatch(f->f.function().equals("Scope::Guard::~Guard")))
                .findFirst().orElseThrow(()->new AssertionError("Destructor frame lost its source name"));
        var frame=active.runtime().stack().stream().filter(f->f.function().equals("Scope::Guard::~Guard")).findFirst().orElseThrow();
        assertNotNull(frame.parameters().get("this"));
        int line=(int)DEBUG_SOURCE.substring(0,DEBUG_SOURCE.indexOf("printf(\"D%d \",this->id);")).chars().filter(c->c=='\n').count()+1;
        assertTrue(active.program().line(line).stream().anyMatch(location->location.function().equals("Scope::Guard::~Guard")
                && file.text(location.instruction().range()).contains("printf(\"D%d \"")));
        for(int i=history.size()-2;i>=0;i--)assertSame(history.get(i),debug.previous());
        assertFalse(debug.canPrevious());
        for(int i=1;i<history.size();i++)assertSame(history.get(i),debug.next());
        assertEquals("C4 D4 ",debug.current().runtime().stdout().replace("\r\n","\n"));
    }
}
