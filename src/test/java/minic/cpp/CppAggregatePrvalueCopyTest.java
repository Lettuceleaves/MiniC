package minic.cpp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static minic.cpp.CppReferenceTest.agree;
import static minic.cpp.CppReferenceTest.reject;

/** A same-type aggregate list expression constructs an independent prvalue object. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppAggregatePrvalueCopyTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("const-reference", """
                        struct Pair{int value;};
                        int main(){Pair source={4};const Pair&copy=Pair{source};source.value=9;
                            printf("%d %d %d\\n",copy.value,source.value,&copy!=&source);return 0;}
                        """, "4 9 1\n"),
                Arguments.of("rvalue-category", """
                        struct Pair{int value;};int choose(Pair&){return 1;}int choose(Pair&&){return 2;}
                        int main(){Pair source={4};Pair&&copy=Pair{source};copy.value=8;
                            printf("%d %d %d %d\\n",source.value,copy.value,choose(Pair{source}),&copy!=&source);return 0;}
                        """, "4 8 2 1\n"),
                Arguments.of("member-reference", """
                        struct Pair{int value;};
                        int main(){Pair source={5};const int&value=Pair{source}.value;source.value=9;
                            printf("%d %d\\n",value,source.value);return 0;}
                        """, "5 9\n"),
                Arguments.of("array-copy-once", """
                        struct Pair{int values[2];};Pair source={{6,7}};int calls;
                        Pair&get(){++calls;return source;}
                        int main(){const Pair&copy=Pair{get()};source.values[0]=9;
                            printf("%d %d %d %d\\n",copy.values[0],copy.values[1],calls,&copy!=&source);return 0;}
                        """, "6 7 1 1\n"),
                Arguments.of("reference-member-copy", """
                        struct Alias{int&value;};
                        int main(){int value=3;Alias source={value};const Alias&copy=Alias{source};value=8;
                            printf("%d %d %d\\n",copy.value,&copy.value==&value,&copy!=&source);return 0;}
                        """, "8 1 1\n"),
                Arguments.of("nontrivial-member-control", """
                        int copies;struct Field{int value;Field(int n):value(n){}Field(const Field&other):value(other.value){++copies;}};
                        struct Outer{Field field;};
                        int main(){Outer source={Field(6)};const Outer&copy=Outer{source};source.field.value=9;
                            printf("%d %d %d\\n",copy.field.value,copies,&copy!=&source);return 0;}
                        """, "6 1 1\n"));
    }

    @ParameterizedTest @MethodSource("programs")
    void aggregateListCopyHasPrvalueIdentity(String name,String body,String expected)throws Exception{
        agree(temporary,name,"#include <stdio.h>\n"+body,expected);
    }

    @Test void aggregateCopyPrvalueCannotBindMutableLvalueReference()throws Exception{
        reject(temporary,"mutable-reference-to-copy","""
                struct Pair{int value;};int main(){Pair source={4};Pair&copy=Pair{source}; // bad
                    return copy.value;}
                """);
    }
}
