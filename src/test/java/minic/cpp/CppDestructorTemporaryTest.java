package minic.cpp;

import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** F07 follow-up: full expressions, extended references and parameter/result ownership. */
@Tag("cpp-differential")
@Timeout(90)
final class CppDestructorTemporaryTest {
    @TempDir Path temporary;
    private static final String TRACE = """
            #include <stdio.h>
            struct T {
                int id;
                T(int number):id(number){printf("C%d ",id);}
                ~T(){printf("D%d ",id);}
                int get()const{printf("G%d ",id);return id;}
                bool more()const{printf("Q%d ",id);return id<3;}
            };
            """;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("discarded-prvalue", TRACE + "int main(){T(1);printf(\"E \");return 0;}", "C1 D1 E "),
                Arguments.of("receiver-outlives-enclosing-call", TRACE + "int main(){printf(\"V%d \",T(1).get());printf(\"E \");return 0;}", "C1 G1 V1 D1 E "),
                Arguments.of("reference-argument-lives-through-call", TRACE + "void observe(const T &value){printf(\"R%d \",value.id);} int main(){observe(T(1));printf(\"E \");return 0;}", "C1 R1 D1 E "),
                Arguments.of("guaranteed-elision-into-local", TRACE + "int main(){T value=T(1);printf(\"B \");return 0;}", "C1 B D1 "),
                Arguments.of("local-reference-extends-temporary", TRACE + "int main(){const T &value=T(1);printf(\"B%d \",value.id);return 0;}", "C1 B1 D1 "),
                Arguments.of("subobject-reference-extends-complete-object", TRACE + "int main(){const int &value=T(1).id;printf(\"B%d \",value);return 0;}", "C1 B1 D1 "),
                Arguments.of("extended-reference-follows-declaration-order", TRACE + "int main(){T before(1);const T &value=T(2);T after(3);printf(\"B \");return 0;}", "C1 C2 C3 B D3 D2 D1 "),
                Arguments.of("conditional-reference-extends-only-selected-object", TRACE + "int main(){int flag=0;const T &value=flag?T(1):T(2);printf(\"B%d \",value.id);return 0;}", "C2 B2 D2 "),
                Arguments.of("loop-condition-ends-before-body", TRACE + "int main(){int i=0;while(T(++i).more()){printf(\"B \");}printf(\"E \");return 0;}", "C1 Q1 D1 B C2 Q2 D2 B C3 Q3 D3 E "),
                Arguments.of("short-circuit-does-not-register-unentered-temporaries", TRACE + "int main(){if(false&&T(1).more())printf(\"X \");if(true||T(2).more())printf(\"B \");return 0;}", "B "),
                Arguments.of("conditional-value-snapshot-before-cleanup", TRACE + "int main(){int flag=0;int value=flag?T(1).id:T(2).id;printf(\"V%d \",value);return 0;}", "C2 D2 V2 "),
                Arguments.of("comma-temporaries-clean-in-reverse", TRACE + "int main(){T(1),T(2);printf(\"E \");return 0;}", "C1 C2 D2 D1 E "),
                Arguments.of("nested-construction-temporaries-complete-before-cleanup", TRACE + "int main(){printf(\"V%d \",T(T(1).id+1).get());return 0;}", "C1 C2 G2 V2 D2 D1 "),
                Arguments.of("return-full-expression-cleans-before-local-scope", TRACE + "int run(){T local(1);return T(2).id;} int main(){printf(\"V%d \",run());return 0;}", "C1 C2 D2 D1 V2 "),
                Arguments.of("returned-reference-does-not-shorten-argument-lifetime", TRACE + "const T &identity(const T &value){return value;} int main(){printf(\"V%d \",identity(T(1)).id);return 0;}", "C1 V1 D1 "),
                Arguments.of("sizeof-does-not-construct-or-clean", TRACE + "int main(){printf(\"%d \",(int)sizeof(T(1)));return 0;}", "4 "),
                Arguments.of("for-init-reference-outlives-loop-body", TRACE + "int main(){for(const T &value=T(1);value.id<3;){printf(\"B%d \",value.id);break;}printf(\"E \");return 0;}", "C1 B1 D1 E "),
                Arguments.of("by-value-parameter-owns-direct-prvalue", TRACE + "void take(T value){printf(\"F%d \",value.id);} int main(){take(T(1));printf(\"E \");return 0;}", "C1 F1 D1 E "),
                Arguments.of("by-value-parameter-copy-is-independent", TRACE + "void take(T value){value.id=2;printf(\"F%d \",value.id);} int main(){T value(1);take(value);printf(\"B%d \",value.id);return 0;}", "C1 F2 D2 B1 D1 "),
                Arguments.of("parameter-destruction-at-caller-full-expression", TRACE + "int take(T value){printf(\"F%d \",value.id);return value.id;} int main(){printf(\"R%d \",take(T(1)));return 0;}", "C1 F1 R1 D1 "),
                Arguments.of("record-prvalue-return-keeps-result-ownership", TRACE + "T make(){return T(1);} int main(){T value=make();printf(\"B%d \",value.id);return 0;}", "C1 B1 D1 "),
                Arguments.of("record-lvalue-return-copies-before-local-cleanup", TRACE + "T make(int select){T first(1);T second(2);return select?first:second;} int main(){T value=make(1);printf(\"B%d \",value.id);return 0;}", "C1 C2 D2 D1 B1 D1 ")
        );
    }

    @Test void classResultAndParameterKeepTheirFinalAddress() throws Exception {
        agree(temporary,"nontrivial-final-address", """
                #include <stdio.h>
                struct Self{Self *address;Self():address(this){} ~Self(){printf("D%d ",address==this);}};
                Self make(){return Self();}
                void take(Self value){printf("P%d ",value.address==&value);}
                int main(){Self value=make();printf("L%d ",value.address==&value);take(make());return 0;}
                """, "L1 P1 D1 D1 ");
    }

    @Test void constructorMemberInitializerHasItsOwnFullExpression() throws Exception {
        agree(temporary,"member-initializer-full-expression",TRACE+"""
                struct Owner{int first;int second;Owner():first(T(1).id),second(T(2).id){printf("B ");}};
                int main(){Owner value;printf("%d %d ",value.first,value.second);return 0;}
                ""","C1 D1 C2 D2 B 1 2 ");
    }

    @Test void anUnusedParameterDefinitionDoesNotRequireCallerDestructorAccess() throws Exception {
        agree(temporary,"unused-parameter-destructor","""
                class Hidden{~Hidden(){} public:Hidden(){}};
                void take(Hidden value){}
                int main(){return 0;}
                """, "");
    }

    @Test void anElidedReturnStillRequiresAnAccessibleDestructor() throws Exception {
        reject(temporary,"inaccessible-elided-return","""
                class Hidden{~Hidden(){} public:Hidden(){}};
                Hidden make(){return Hidden();} // bad
                int main(){return 0;}
                """);
    }

    @Test void extendedTemporaryHistoryReplaysTheSameDestruction() {
        assertDebugHistory(TRACE+"int main(){const T &value=T(1);printf(\"B%d \",value.id);return 0;}",
                "C1 B1 D1 ","T::~T","this");
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={
            "class Hidden{~Hidden(){} public:Hidden(){}}; int main(){return sizeof(Hidden{}); // bad\n}",
            "class Hidden{~Hidden(){} public:Hidden(){}}; Hidden make(); int main(){return sizeof(make()); // bad\n}",
            "class Hidden{~Hidden(){}}; int take(Hidden); int inspect(Hidden &value){return sizeof(take(value)); // bad\n} int main(){return 0;}"
    })
    void unevaluatedPrvaluesStillCheckPotentialDestructorAccess(String source) throws Exception {
        reject(temporary,"unevaluated-destructor-access",source);
    }

    @Test void anUnevaluatedLvalueDoesNotRequireDestructorAccess() throws Exception {
        agree(temporary,"unevaluated-lvalue-destructor","""
                #include <stdio.h>
                class Hidden{~Hidden(){}};
                int main(){Hidden *value=nullptr;printf("%d ",(int)sizeof(*value));return 0;}
                ""","1 ");
    }

    @Test @Timeout(150)
    void independentGxxOracleAcceptsEveryLifetimeProgram() throws Exception {
        for (var arguments : programs().toList()) {
            Object[] fixture=arguments.get();
            Path source=temporary.resolve(fixture[0]+".cpp");
            Path executable=temporary.resolve(fixture[0]+".exe");
            Files.writeString(source,(String)fixture[1]);
            var compile=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                    "-std=c++17","-pedantic-errors",source.toString(),"-o",executable.toString()),
                    temporary,"",Duration.ofSeconds(20),65536);
            assertFalse(compile.timedOut());assertFalse(compile.outputExceeded());
            assertEquals(0,compile.exitCode(),fixture[0]+": "+compile.stderr());
            var run=BoundedProcess.run(List.of(executable.toString()),temporary,"",Duration.ofSeconds(5),65536);
            assertFalse(run.timedOut());assertEquals(0,run.exitCode(),run::stderr);
            assertEquals(fixture[2],run.stdout().replace("\r\n","\n"),fixture[0].toString());
        }
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void temporaryAndParameterLifetimesAgree(String name,String source,String expected) throws Exception {
        var api=compiler(source);var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        agree(temporary,name,source,expected);
    }
}
