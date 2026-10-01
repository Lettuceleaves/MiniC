package minic.cpp;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static minic.cpp.CppReferenceTest.*;

/** C++ functional notation uses C++ explicit-conversion rules, not unrestricted core C casts. */
@Tag("cpp-differential") @Execution(ExecutionMode.SAME_THREAD) @Timeout(90)
final class CppFunctionalConversionTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
            Arguments.of("single-token-arithmetic-types","""
                    #include <stdio.h>
                    int main(){printf("%d %d %d %d %d\\n",int(3.8),int(long(4.8)),int(unsigned(5)),int(short(6)),int(signed(7)));return 0;}
                    ""","3 4 5 6 7\n"),
            Arguments.of("void-conversion-evaluates-operand-once","""
                    #include <stdio.h>
                    int calls;int value(){return ++calls;}int main(){(void(value()));void();printf("%d\\n",calls);return 0;}
                    ""","1\n"),
            Arguments.of("const-and-volatile-alias-prvalues","""
                    #include <stdio.h>
                    int main(){typedef const int I;typedef volatile long L;const int&r=I(3.8);printf("%d %d\\n",r,int(L(4.8)));return 0;}
                    ""","3 4\n"),
            Arguments.of("explicit-pointer-bool-and-wide-integer-roundtrip","""
                    #include <stdio.h>
                    int main(){typedef unsigned long long U;typedef int*P;int value=9;P p=&value;U address=U(p);P q=P(address);
                    printf("%d %d %d %d\\n",bool(p),bool(P(0)),q==p,U(nullptr)==0);return 0;}
                    ""","1 0 1 1\n"),
            Arguments.of("cstyle-and-functional-pointer-cv-conversions","""
                    #include <stdio.h>
                    int main(){typedef int*P;int value=2;const int*view=&value;P a=P(view);int*b=(int*)view;
                    *a=4;*b=5;printf("%d %d\\n",value,a==b);return 0;}
                    ""","5 1\n"),
            Arguments.of("small-integer-to-pointer-is-permitted","""
                    #include <stdio.h>
                    int main(){typedef int*P;typedef unsigned long long U;P a=P(1);P b=(int*)1;
                    printf("%d %d\\n",a==b,U(a)==1);return 0;}
                    ""","1 1\n"));}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void allBackendsAgree(String name,String source,String expected)throws Exception{agree(temporary,name,source,expected);}

    static Stream<Arguments> invalidConversions(){return Stream.of(
            Arguments.of("pointer-to-small-int","int main(){int n=1;int*p=&n;int value=int(p); // bad\nreturn 0;}"),
            Arguments.of("pointer-to-windows-long","int main(){int n=1;int*p=&n;long value=long(p); // bad\nreturn 0;}"),
            Arguments.of("pointer-to-floating","int main(){int n=1;int*p=&n;double value=double(p); // bad\nreturn 0;}"),
            Arguments.of("floating-to-pointer","int main(){typedef int*P;P value=P(1.5); // bad\nreturn 0;}"),
            Arguments.of("nullptr-to-small-integer","int main(){int value=int(nullptr); // bad\nreturn 0;}"),
            Arguments.of("function-pointer-to-small-integer","int f(){return 1;}int main(){int value=int(f); // bad\nreturn 0;}"));}
    @ParameterizedTest(name="{0}") @MethodSource("invalidConversions")
    void cppDisallowsInvalidFunctionalConversions(String name,String source)throws Exception{reject(temporary,name,source);}

    static Stream<Arguments> invalidCStyleConversions(){return invalidConversions().map(argument->{
        Object[] values=argument.get();String source=(String)values[1];
        source=source.replace("int(p)","(int)p").replace("long(p)","(long)p").replace("double(p)","(double)p")
                .replace("P(1.5)","(P)1.5").replace("int(nullptr)","(int)nullptr").replace("int(f)","(int)f");
        return Arguments.of("cstyle-"+values[0],source);
    });}
    @ParameterizedTest(name="{0}") @MethodSource("invalidCStyleConversions")
    void cppDisallowsTheSameInvalidCStyleConversions(String name,String source)throws Exception{reject(temporary,name,source);}

    @Test void implicitConversionCannotCastAwayPointerConst()throws Exception{
        reject(temporary,"implicit-cv","int main(){int n=1;const int*p=&n;int*q=p; // bad\nreturn 0;}");
    }

    @Test void pointerToBoolListStillRequiresNonNarrowingConversion(){
        // GCC 8's known pointer-to-bool list-narrowing defect is not a valid rejection oracle.
        var api=compiler("int main(){int n=1;int*p=&n;bool value=bool{p};return 0;}");
        var semantic=stage(api,minic.compiler.semantic.SemanticAnalyzer.class);api.runThrough(semantic);
        org.junit.jupiter.api.Assertions.assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP004")));
    }
}
