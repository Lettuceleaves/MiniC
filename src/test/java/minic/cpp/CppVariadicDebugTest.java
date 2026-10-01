package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.DebugRuntime;
import minic.debug.Debugger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The debugger models Windows x64 promoted variadic arguments as independent eight-byte slots. */
@Timeout(90)
final class CppVariadicDebugTest {
    @TempDir Path temporary;
    private static final String HEADERS="#include <stdio.h>\n#include <stdarg.h>\n";
    private static final String RECURSIVE="""
            int sum(int depth,...){
                va_list arguments;
                va_start(arguments,depth);
                int nested=0;
                if(depth>0)nested=sum(depth-1,depth+10);
                int own=va_arg(arguments,int);
                va_end(arguments);
                return own+nested;
            }
            int main(){printf("%d\\n",sum(2,7));return 0;}
            """;

    static Stream<Arguments> programs(){
        return Stream.of(LanguageMode.values()).flatMap(mode->Stream.of(
                Arguments.of(mode,"promoted-scalars","""
                        double sum(int ignored,...){va_list a;va_start(a,ignored);
                        int narrow=va_arg(a,int);int other=va_arg(a,int);double real=va_arg(a,double);
                        va_end(a);return narrow+other+real;}
                        int main(){unsigned char one=250;short two=-2;float real=1.25f;
                        printf("%.2f\\n",sum(0,one,two,real));return 0;}
                        ""","249.25\n"),
                Arguments.of(mode,"beyond-register-arguments","""
                        long long read(int a,int b,int c,int d,...){va_list args;va_start(args,d);
                        long long wide=va_arg(args,long long);int *pointer=va_arg(args,int*);
                        double real=va_arg(args,double);va_end(args);return wide+*pointer+(int)real+a+b+c+d;}
                        int main(){int value=9;printf("%lld\\n",read(1,2,3,4,4294967296LL,&value,3.75));return 0;}
                        ""","4294967318\n"),
                Arguments.of(mode,"copy-and-restart","""
                        int inspect(int count,...){va_list a;va_list b;va_start(a,count);
                        int first=va_arg(a,int);va_copy(b,a);int second=va_arg(a,int);int copy=va_arg(b,int);
                        va_end(a);va_end(b);va_start(a,count);int again=va_arg(a,int);va_end(a);
                        return first*1000+second*100+copy*10+again;}
                        int main(){printf("%d\\n",inspect(2,3,4));return 0;}
                        ""","3443\n"),
                Arguments.of(mode,"recursive-frame-isolation",RECURSIVE,"30\n"),
                Arguments.of(mode,"zero-extra-arguments","""
                        int unchanged(int value,...){va_list a;va_start(a,value);va_end(a);return value;}
                        int main(){printf("%d\\n",unchanged(7));return 0;}
                        ""","7\n")));
    }

    @ParameterizedTest(name="{0}: {1}") @MethodSource("programs")
    void sourceDebuggerMatchesNativeAndGpp(LanguageMode mode,String name,String text,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),mode).run(name,HEADERS+text,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,outcome.stdout().replace("\r\n","\n"),report::describe));
    }

    @Test void memberReceiverAndHiddenStructResultDoNotShiftTheVariadicCursor()throws Exception{
        String text=HEADERS+"""
                struct Pair{int first;int second;};
                struct Box{int base;Pair read(int count,...){va_list a;va_start(a,count);
                    int one=va_arg(a,int);int two=va_arg(a,int);va_end(a);Pair value={base+one,two};return value;}};
                int main(){Box box={10};Pair result=box.read(2,3,4);printf("%d %d\\n",result.first,result.second);return 0;}
                """;
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run("member-sret",text,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals("13 4\n",outcome.stdout().replace("\r\n","\n"),report::describe));
    }

    @Test void variadicStorageIsReleasedAndHistoryRemainsExact(){
        var debug=new DebugApi(new SourceFile("variadic-history.c",HEADERS+RECURSIVE));
        var history=new ArrayList<Debugger.Context>();
        history.add(debug.current());
        for(int steps=0;debug.canNext()&&steps<3000;steps++)history.add(debug.next());
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals("30\n",debug.current().runtime().stdout().replace("\r\n","\n"));
        assertTrue(debug.current().runtime().stackMemory().isEmpty());
        assertTrue(history.stream().anyMatch(context->context.runtime().stack().size()==4));
        for(int i=history.size()-2;i>=0;i--)assertSame(history.get(i),debug.previous());
        for(int i=1;i<history.size();i++)assertSame(history.get(i),debug.next());
    }

    @Test void exitReleasesEveryVariadicCallFrame(){
        var debug=new DebugApi(new SourceFile("variadic-exit.c",HEADERS+"#include <stdlib.h>\n"+"""
                void stop(int depth,...){va_list args;va_start(args,depth);
                    int value=va_arg(args,int);if(depth>0)stop(depth-1,value+1);exit(value);}
                int main(){stop(2,5);return 99;}
                """));
        for(int steps=0;debug.canNext()&&steps<3000;steps++)debug.next();
        assertFalse(debug.canNext());
        assertEquals(DebugRuntime.TerminationKind.EXITED,debug.current().runtime().termination().kind());
        assertEquals(7,debug.current().runtime().termination().status());
        assertTrue(debug.current().runtime().stack().isEmpty());
        assertTrue(debug.current().runtime().stackMemory().isEmpty());
    }

    @Test void missingVariadicValueRemainsUninitialized(){
        // Reading a nonexistent argument is undefined C/C++; this checks the debugger's own diagnostic.
        var debug=new DebugApi(new SourceFile("variadic-overread.c",HEADERS+"""
                int read(int count,...){va_list args;va_start(args,count);return va_arg(args,int);}
                int main(){return read(0);}
                """));
        for(int steps=0;debug.canNext()&&steps<1000;steps++)debug.next();
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.FAILED,debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("uninitialized"),debug.current().stop()::error);
    }
}
