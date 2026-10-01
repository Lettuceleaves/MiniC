package minic.cpp;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static minic.cpp.CppReferenceTest.agree;

/** Integration of overloaded calls with source temporary and returned-object lifetime rules. */
final class CppOperatorLifetimeTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
            Arguments.of("operator-return-uses-final-address","""
                    #include <stdio.h>
                    struct T{T*self;int n;T(int x):self(this),n(x){printf("C%d ",n);}~T(){printf("D%d ",n);}
                        T operator+(int x)const{return T(n+x);}};
                    int main(){T a(1);T b=a+2;printf("R%d ",b.self==&b);return 0;}
                    ""","C1 C3 R1 D3 D1 "),
            Arguments.of("functor-temporary-last-through-full-expression","""
                    #include <stdio.h>
                    struct T{int n;T(int x):n(x){printf("C%d ",n);}~T(){printf("D%d ",n);}
                        int operator()(int x)const{return n+x;}};
                    int main(){printf("R%d ",T(2)(3));return 0;}
                    ""","C2 R5 D2 "),
            Arguments.of("free-operator-value-argument-destruction","""
                    #include <stdio.h>
                    struct T{int n;T(int x):n(x){printf("C%d ",n);}~T(){printf("D%d ",n);}};
                    int operator+(T left,int n){printf("B ");return left.n+n;}
                    int main(){T a(1);printf("R%d ",a+2);return 0;}
                    ""","C1 B R3 D1 D1 "));}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void respectsTemporaryAndResultLifetimes(String name,String source,String expected)throws Exception{
        agree(temporary,name,source,expected);
    }
}
