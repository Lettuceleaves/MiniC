package minic.cpp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

/** Source-level acceptance: the identical program must resolve and execute like C++17. */
@Tag("cpp-differential") @Timeout(90)
final class CppOverloadTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("exact-and-promotions", """
                        int select(int value){return 1;} int select(long value){return 2;}
                        int select(double value){return 3;}
                        int main(){short s=4;unsigned char c=5;float f=1.5f;
                            printf("%d %d %d %d %d\\n",select(s),select(c),select(f),select(7L),select(8));return 0;}
                        """, "1 1 3 2 1\n"),
                Arguments.of("reference-cv-and-temporary", """
                        int select(int &value){value+=1;return 1;}
                        int select(const int &value){return 2;}
                        int main(){int a=4;const int b=8;int x=select(a);int y=select(b);int z=select(7);
                            printf("%d %d %d %d\\n",x,y,z,a);return 0;}
                        """, "1 2 2 5\n"),
                Arguments.of("pointer-qualification-and-null-literal", """
                        int select(int *p){return 1;} int select(const int *p){return 2;} int select(int value){return 3;}
                        int truth(void *p){return 4;} int truth(bool value){return 5;}
                        int main(){int a=7;const int b=8;printf("%d %d %d %d\\n",select(&a),select(&b),select(0),truth(&a));return 0;}
                        """, "1 2 3 4\n"),
                Arguments.of("all-arguments-dominate", """
                        int choose(int a,double b){return 1;} int choose(long a,double b){return 2;}
                        int main(){short a=2;float b=3;printf("%d\\n",choose(a,b));return 0;}
                        """, "1\n"),
                Arguments.of("variadic-fallback", """
                        int choose(int value){return 1;} int choose(double value,...){return 2;}
                        int main(){printf("%d %d %d\\n",choose(1),choose(1.5),choose(1.5,4));return 0;}
                        """, "1 2 2\n"),
                Arguments.of("member-const-and-parameter-overloads", """
                        struct Box {int value;
                            int read(){return value+1;} int read() const{return value+2;}
                            int read(int add){return value+add;} int read(double add){return value+10;}};
                        int main(){Box a={3};const Box b={5};printf("%d %d %d %d\\n",a.read(),b.read(),a.read(4),a.read(4.5));return 0;}
                        """, "4 7 7 13\n"),
                Arguments.of("out-of-line-signature-selection", """
                        struct Box {int value;int read(int x);int read(double x);int read() const;};
                        int Box::read(int x){return value+x;} int Box::read(double x){return value+10;}
                        int Box::read() const{return value;}
                        int main(){Box a={3};printf("%d %d %d\\n",a.read(4),a.read(4.5),a.read());return 0;}
                        """, "7 13 3\n"),
                Arguments.of("namespace-using-overload-set", """
                        namespace A{int choose(int x){return 1;}} namespace B{int choose(double x){return 2;}}
                        using A::choose;using B::choose;
                        int main(){printf("%d %d %d\\n",choose(1),choose(1.5),A::choose(1.5));return 0;}
                        """, "1 2 1\n"),
                Arguments.of("declaration-cv-and-source-order", """
                        int choose(const int value);int choose(double value){return 2;}
                        int before(){return choose(3);}int choose(int value){return value+1;}
                        int main(){printf("%d %d\\n",before(),choose(1.5));return 0;}
                        """, "4 2\n"),
                Arguments.of("contextual-function-address", """
                        int choose(int value){return value+1;} int choose(double value){return 8;}
                        int apply(int (*function)(int),int value){return function(value);}
                        int main(){int (*pointer)(int)=choose;int (&reference)(double)=choose;
                            printf("%d %d %d\\n",pointer(2),reference(2.0),apply(choose,4));return 0;}
                        """, "3 8 5\n"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void resolvesAndExecutesLikeCpp(String name,String source,String expected)throws Exception{
        CppReferenceTest.agree(temporary,name,"#include <stdio.h>\n"+source,expected);
    }

    static Stream<Arguments> invalidPrograms(){
        return Stream.of(
                Arguments.of("equal-standard-conversions","int choose(long x){return 1;}int choose(double x){return 2;}\nint main(){return choose(1);}// bad\n"),
                Arguments.of("cross-argument-no-winner","int choose(int x,long y){return 1;}int choose(long x,int y){return 2;}\nint main(){return choose(1,1);}// bad\n"),
                Arguments.of("value-reference-ambiguity","int choose(int x){return 1;}int choose(const int &x){return 2;}\nint main(){int x=3;return choose(x);}// bad\n"),
                Arguments.of("return-type-is-not-overload","int choose(int x){return 1;}\ndouble choose(int x){return 2;}// bad\nint main(){return 0;}"),
                Arguments.of("const-object-has-no-viable-method","struct Box{int choose(int x){return 1;}int choose(double x){return 2;}};\nint main(){const Box box={};return box.choose(1);}// bad\n"),
                Arguments.of("pointer-cv-not-ordered","int choose(const int *x){return 1;}int choose(volatile int *x){return 2;}\nint main(){int x=3;return choose(&x);}// bad\n"),
                Arguments.of("overloaded-name-without-target","int choose(int x){return 1;}int choose(double x){return 2;}\nint main(){return sizeof(choose);}// bad\n"),
                Arguments.of("block-variable-hides-overloads","int choose(int x){return 1;}int choose(double x){return 2;}\nint main(){int choose=3;return choose(1);}// bad\n"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void rejectsIllFormedOverloadsAtTheSourceLocation(String name,String source)throws Exception{
        CppReferenceTest.reject(temporary,name,source);
    }
}
