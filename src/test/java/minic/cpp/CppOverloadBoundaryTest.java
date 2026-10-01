package minic.cpp;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

/** Contextual callback selection and source-type boundaries around overload lookup. */
@Timeout(90)
class CppOverloadBoundaryTest {
    @TempDir Path temporary;

    @Test void selectedOverloadsKeepSourceFramesAndReversibleDebugHistory() {
        CppReferenceTest.assertDebugHistory("""
                #include <stdio.h>
                namespace N {
                    int pick(int n){n+=1;return n;}
                    int pick(double n){return 8;}
                }
                int main(){int (*pointer)(int)=(&N::pick);printf("%d %d\\n",pointer(3),N::pick(2.5));return 0;}
                """, "4 8\n", "N::pick", "n");
    }

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("string-array-and-const-pointer", """
                        int pick(char *p){return 1;} int pick(const char *p){return 2;}
                        int length(const char (&s)[4]){return 3;} int length(const char (&s)[5]){return 4;}
                        int main(){printf("%d %d %d\\n",pick("abc"),length("abc"),length("abcd"));return 0;}
                        """, "2 3 4\n"),
                Arguments.of("overloaded-callback-uses-each-candidate-target", """
                        int choose(int n){return n+1;} int choose(double n){return 8;}
                        int apply(int (*f)(int),int n){return f(n);}
                        int apply(int (*f)(double),double n){return f(n);}
                        int main(){printf("%d %d\\n",apply(choose,4),apply(choose,4.5));return 0;}
                        """, "5 8\n"),
                Arguments.of("callback-address-can-select-reference-candidate", """
                        int choose(int n){return n+1;} int choose(double n){return 8;}
                        int apply(int (&f)(int),int n){return f(n);} int apply(int (*f)(int),double n){return 90;}
                        int main(){int (&reference)(int)=&choose;printf("%d %d\\n",apply(&choose,4),reference(6));return 0;}
                        """, "5 7\n"),
                Arguments.of("source-reference-callback-signature", """
                        int &choose(int &n){return n;} int *choose(int *n){return n;}
                        int main(){int n=3;int &(*function)(int &)=choose;function(n)=9;
                            printf("%d\\n",n);return 0;}
                        """, "9\n"),
                Arguments.of("member-receiver-and-arguments-evaluated-once", """
                        int calls; struct Box {int n;int read(int x){return n+x;}int read(double x){return 90;}};
                        Box *next(Box *p){calls+=1;return p;}int argument(){calls+=1;return 4;}
                        int main(){Box box={3};int result=next(&box)->read(argument());printf("%d %d\\n",result,calls);return 0;}
                        """, "7 2\n"),
                Arguments.of("single-and-overloaded-pointer-to-bool", """
                        int single(bool value){return value;}int overloaded(bool value){return value;}int overloaded(double value){return 9;}
                        int main(){int value=3;printf("%d %d\\n",single(&value),overloaded(&value));return 0;}
                        """, "1 1\n"),
                Arguments.of("callback-parameter-cv-is-one-signature", """
                        int apply(int (*callback)(const int));
                        int apply(int (*callback)(int)){return callback(3);}int apply(double n){return 9;}
                        int next(int n){return n+1;}
                        int main(){printf("%d\\n",apply(next));return 0;}
                        """, "4\n"),
                Arguments.of("target-callback-parameter-cv", """
                        int choose(int (*callback)(const int)){return callback(2);}int choose(double n){return 9;}
                        int next(int n){return n+2;}
                        int main(){int (*function)(int (*)(int))=choose;printf("%d\\n",function(next));return 0;}
                        """, "4\n"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void preservesSourceSelectionAndEvaluation(String name, String source, String expected) throws Exception {
        CppReferenceTest.agree(temporary, name, "#include <stdio.h>\n" + source, expected);
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("unknown-qualified-call", "int main(){return Unknown::run();}// bad\n"),
                Arguments.of("missing-qualified-call", "namespace N{}\nint main(){return N::missing();}// bad\n"),
                Arguments.of("callback-reference-pointer-ambiguous", """
                        int choose(int n){return n;}int choose(double n){return 2;}
                        int apply(int (&f)(int)){return 1;}int apply(int (*f)(int)){return 2;}
                        int main(){return apply(choose);}// bad
                        """),
                Arguments.of("callback-address-reference-pointer-ambiguous", """
                        int choose(int n){return n;}int choose(double n){return 2;}
                        int apply(int (&f)(int)){return 1;}int apply(int (*f)(int)){return 2;}
                        int main(){return apply(&choose);}// bad
                        """),
                Arguments.of("callback-target-has-two-entities", """
                        namespace A{int choose(int n){return n;}}namespace B{int choose(int n){return n;}}
                        using namespace A;using namespace B;
                        int main(){int (*p)(int)=choose;return 0;}// bad
                        """),
                Arguments.of("main-cannot-overload", "int main(){return 0;}\nint main(int n){return n;}// bad\n"),
                Arguments.of("single-function-void-pointer-rejected", "int single(int *p){return 1;}\nint main(){void *p=nullptr;return single(p);}// bad\n"),
                Arguments.of("single-function-nullptr-to-bool-rejected", "int single(bool p){return p;}\nint main(){return single(nullptr);}// bad\n"),
                Arguments.of("duplicate-callback-parameter-cv", "int same(int (*f)(const int)){return 1;}\nint same(int (*f)(int)){return 2;}// bad\nint main(){return 0;}\n"),
                Arguments.of("same-member-parameters-return-conflict", "struct Box{int read(int n); double read(int n);};// bad\nint main(){return 0;}\n"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("invalid")
    void diagnosesInvalidCallsWithoutThrowing(String name, String source) throws Exception {
        CppReferenceTest.reject(temporary, name, source);
    }
}
