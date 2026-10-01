package minic.cpp;

import minic.compiler.semantic.SemanticAnalyzer;
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
import static org.junit.jupiter.api.Assertions.*;

/** F08 special-member assignment; unrelated overloaded operators belong to F10. */
@Tag("cpp-differential") @Execution(ExecutionMode.SAME_THREAD) @Timeout(90)
final class CppCopyAssignmentTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
            Arguments.of("assignment-return-reference-and-chaining","""
                    #include <stdio.h>
                    int assignments;struct Box{int value;Box(int n):value(n){}
                    Box&operator=(const Box&other){++assignments;value=other.value;return *this;}};
                    int main(){Box a(1);Box b(2);Box c(3);Box&r=(a=b=c);printf("%d %d %d %d\\n",a.value,b.value,assignments,&r==&a);return 0;}
                    ""","3 3 2 1\n"),
            Arguments.of("self-assignment-is-not-optimized-away","""
                    #include <stdio.h>
                    int assignments;struct Box{int value;Box(int n):value(n){}
                    Box&operator=(const Box&other){++assignments;if(this!=&other)value=other.value;return *this;}};
                    int main(){Box a(4);a=a;printf("%d %d\\n",a.value,assignments);return 0;}
                    ""","4 1\n"),
            Arguments.of("implicit-assignment-invokes-member-assignment","""
                    #include <stdio.h>
                    int assignments;struct Box{int value;Box(int n):value(n){}
                    Box&operator=(const Box&other){++assignments;value=other.value+1;return *this;}};
                    struct Outer{Box item;Outer(int n):item(n){}};
                    int main(){Outer a(2);Outer b(7);a=b;printf("%d %d %d\\n",a.item.value,b.item.value,assignments);return 0;}
                    ""","8 7 1\n"),
            Arguments.of("target-and-source-evaluated-once","""
                    #include <stdio.h>
                    int targets;int sources;int trace;struct Box{int value;Box(int n):value(n){}
                    Box&operator=(const Box&other){trace=trace*10+3;value=other.value;return *this;}};
                    Box&target(Box&x){++targets;trace=trace*10+1;return x;}const Box&source(const Box&x){++sources;trace=trace*10+2;return x;}
                    int main(){Box a(1);Box b(8);target(a)=source(b);printf("%d %d %d %d\\n",a.value,targets,sources,trace);return 0;}
                    ""","8 1 1 213\n"),
            Arguments.of("deep-copy-assignment-and-self-assignment","""
                    #include <stdio.h>
                    #include <stdlib.h>
                    int assignments;struct Owner{int*data;Owner(int n):data((int*)malloc(sizeof(int))){*data=n;}
                    Owner&operator=(const Owner&other){++assignments;if(this!=&other){int*next=(int*)malloc(sizeof(int));*next=*other.data;free(data);data=next;}return *this;}};
                    int main(){Owner a(3);Owner b(9);a=b;a=a;*a.data=5;printf("%d %d %d %d\\n",*a.data,*b.data,a.data!=b.data,assignments);free(a.data);free(b.data);return 0;}
                    ""","5 9 1 2\n"));}

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void allBackendsAgree(String name,String source,String expected)throws Exception{agree(temporary,name,source,expected);}
    @Test void allAssignmentSourcesAreValidCpp17()throws Exception{
        for(var argument:programs().toList()){var values=argument.get();CppCopyConstructionTest.oracle(temporary,(String)values[0],(String)values[1]);}
    }
    static Stream<Arguments> invalidAssignments(){return Stream.of(
            Arguments.of("const-member-deletes-implicit-assignment","struct Box{const int n;Box(int x):n(x){}};int main(){Box a(1);Box b(2);a=b; // bad\nreturn 0;}"),
            Arguments.of("reference-member-deletes-implicit-assignment","struct Box{int&n;Box(int&x):n(x){}};int main(){int x=1;int y=2;Box a(x);Box b(y);a=b; // bad\nreturn 0;}"));}
    @ParameterizedTest(name="{0}") @MethodSource("invalidAssignments")
    void deletedImplicitAssignmentsAreLanguageErrors(String name,String source)throws Exception{
        reject(temporary,name,source);var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP004")),()->semantic.errors().toString());
    }
}
