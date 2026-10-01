package minic.cpp;

import minic.compiler.*;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static minic.cpp.CppReferenceTest.agree;

@Timeout(180)
final class CppPairPrerequisiteTest {
    @TempDir Path temporary;
    @Test void nestedConstructionsAreDirectInitializerArguments() throws Exception {
        agree(temporary,"nested-construction-args", """
                #include <stdio.h>
                struct Value{int n;Value():n(1){} explicit Value(int x):n(x){}};
                struct Pair{int sum;Pair(Value a,Value b):sum(a.n+b.n){}};
                int main(){Pair value(Value(10),Value(20));printf("%d\\n",value.sum);return 0;}
                ""","30\n");
    }
    @Test void laterArgumentCanDisambiguateTheWholeParameterClause() throws Exception {
        agree(temporary,"later-construction-argument", """
                #include <stdio.h>
                struct Value{int n;Value():n(1){} explicit Value(int x):n(x){}};
                struct Pair{int sum;Pair(Value a,Value b):sum(a.n+b.n){}};
                int main(){Pair first(Value(),Value(20));Pair second(Value((30)),Value{40});
                    printf("%d %d\\n",first.sum,second.sum);return 0;}
                ""","21 70\n");
    }
    @Test void syntacticallyValidFunctionDeclarationsStillWinAmbiguity() {
        var api=new CompilerApi(new SourceFile("vexing.cpp","""
                struct Value{int n;};
                Value function(Value());
                Value named(Value(argument));
                Value callback(Value (*fn)(int));
                int main(){return 0;}
                """),LanguageMode.CPP17_ALGORITHM);
        var parser=api.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        api.runThrough(parser);
        assertTrue(parser.succeeded(),parser.errors().toString());
        assertEquals(4,parser.result().program().functions().size());
        assertTrue(parser.result().program().functions().stream().anyMatch(f->f.name().equals("named")&&f.parameters().getFirst().name().equals("argument")));
    }
    @Test void deeperConstructionAndDefaultArgumentsDoNotLeakProbeState() throws Exception {
        agree(temporary,"parameter-probe-state", """
                #include <stdio.h>
                typedef int Number;
                struct Value{int n;explicit Value(int x):n(x){}};
                struct Pair{int sum;Pair(Value a,Value b):sum(a.n+b.n){}};
                int defaulted(int Number=7){return Number;}
                int main(){Pair value(Value(Number(10)),Value(int(20)));
                    Number following=3;printf("%d %d %d\\n",value.sum,defaulted(),following);return 0;}
                ""","30 7 3\n");
    }
    @Test void cvAppliedToFunctionTypedefsIsIgnored() throws Exception {
        agree(temporary,"function-cv", """
                #include <type_traits>
                #include <stdio.h>
                typedef int Function(int);
                typedef int Safe(int) noexcept;
                template<class T>struct AddCV{typedef const volatile T type;};
                static_assert(std::is_same<AddCV<Function>::type,Function>::value,"function has no top cv");
                static_assert(std::is_same<AddCV<Safe>::type,Safe>::value,"noexcept is retained");
                static_assert(std::is_function<Function>::value,"ordinary function");
                static_assert(!std::is_function<Function*>::value,"pointer is not function");
                int identity(int n){return n;}
                int main(){const Function* pointer=&identity;printf("%d\\n",pointer(7));return 0;}
                ""","7\n");
    }
    @Test void makePairDecaysArrayAndFunctionForwardingArguments() throws Exception {
        agree(temporary,"make-pair-decay", """
                #include <utility>
                #include <type_traits>
                #include <stdio.h>
                int identity(int n){return n;}
                int main(){int values[]={11,12};auto made=std::make_pair(values,identity);
                    static_assert(std::is_same<decltype(made),std::pair<int*,int(*)(int)>>::value,"both arguments decay");
                    printf("%d\\n",made.second(made.first[1]));return 0;}
                ""","12\n");
    }
}


