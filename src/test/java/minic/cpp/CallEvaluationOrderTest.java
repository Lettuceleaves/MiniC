package minic.cpp;

import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import minic.cpp.support.CppDifferentialHarness.Backend;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** C++17 sequences the callee before arguments; argument initializations may use either order. */
@Tag("cpp-differential")
@Timeout(60)
final class CallEvaluationOrderTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("callee-side-effect-before-argument", """
                        #include <stdio.h>
                        typedef int (*Callback)(int);
                        int trace = 0;
                        int target(int value){return value;}
                        Callback receiver(){trace=trace*10+1;return target;}
                        int argument(){trace=trace*10+2;return 7;}
                        int main(){int result=receiver()(argument());printf("%d %d\\n",trace,result);return 0;}
                        """, "12 7\n"),
                Arguments.of("global-callee-selected-before-argument", """
                        #include <stdio.h>
                        typedef int (*Callback)(int);
                        int first(int value){return value+100;}
                        int second(int value){return value+200;}
                        Callback selected=0;
                        int change(){selected=second;return 7;}
                        int main(){selected=first;int result=selected(change());printf("%d\\n",result);return 0;}
                        """, "107\n"),
                Arguments.of("parameter-callee-selected-before-argument", """
                        #include <stdio.h>
                        typedef int (*Callback)(int);
                        int first(int value){return value+100;}
                        int second(int value){return value+200;}
                        int change(Callback *slot){*slot=second;return 7;}
                        int run(Callback selected){return selected(change(&selected));}
                        int main(){int result=run(first);printf("%d\\n",result);return 0;}
                        """, "107\n"),
                Arguments.of("scalar-argument-value-matches-its-evaluation-order", """
                        #include <stdio.h>
                        int trace=0;
                        int pair(int first,int second){return first*10+second;}
                        int run(int value){
                            int result=pair((trace=1,value),(trace=2,++value));
                            return (trace==2 && result==12) || (trace==1 && result==22);
                        }
                        int main(){int valid=run(1);printf("%d\\n",valid);return 0;}
                        """, "1\n"),
                Arguments.of("hidden-receiver-value-matches-its-evaluation-order", """
                        #include <stdio.h>
                        struct Box {int value;};
                        struct Box left={10}; struct Box right={20};
                        int trace=0;
                        int replace(struct Box **slot){*slot=&right;return 1;}
                        int method(struct Box *self,int amount){return self->value+amount;}
                        int run(struct Box *pointer){
                            int result=method((trace=1,pointer),(trace=2,replace(&pointer)));
                            return (trace==2 && result==11) || (trace==1 && result==21);
                        }
                        int main(){int valid=run(&left);printf("%d\\n",valid);return 0;}
                        """, "1\n"),
                Arguments.of("callback-argument-value-matches-its-evaluation-order", """
                        #include <stdio.h>
                        typedef int (*Callback)(int);
                        int trace=0;
                        int first(int value){return value+100;}
                        int second(int value){return value+200;}
                        int change(Callback *slot){*slot=second;return 7;}
                        int apply(Callback callback,int argument){return callback(argument);}
                        int run(Callback selected){
                            int result=apply((trace=1,selected),(trace=2,change(&selected)));
                            return (trace==2 && result==107) || (trace==1 && result==207);
                        }
                        int main(){int valid=run(first);printf("%d\\n",valid);return 0;}
                        """, "1\n"),
                Arguments.of("six-arguments-retain-their-values-across-nested-calls", """
                        #include <stdio.h>
                        int count=0;
                        int argument(int value){count=count+1;return value;}
                        int pack(int a,int b,int c,int d,int e,int f){return a+10*b+100*c+1000*d+10000*e+100000*f;}
                        int main(){
                            int result=pack(argument(1),argument(2),argument(3),argument(4),argument(5),argument(6));
                            printf("%d %d\\n",result,count);return 0;
                        }
                        """, "654321 6\n"));
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("programs")
    void calleeAndArgumentValuesRespectCpp17Sequencing(String name, String source, String expected) throws Exception {
        // The trace predicates admit either argument order. These cases deliberately use
        // C++17 indeterminate sequencing and are not used as a C-mode reference oracle.
        var report = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(),
                LanguageMode.CPP17_ALGORITHM).run(name, source, "");
        var reference = report.outcomes().get(Backend.GXX);
        assertEquals(CppDifferentialHarness.Status.OK, reference.status(), report::describe);
        assertEquals(expected, reference.stdout().replace("\r\n", "\n"), report::describe);
        assertTrue(report.passed(), report::describe);
    }
}
