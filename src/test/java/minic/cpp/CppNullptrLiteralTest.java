package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
final class CppNullptrLiteralTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs(){
        return Stream.of(
                Arguments.of("pointer-contexts","""
                        #include <stdio.h>
                        int *global=nullptr;
                        int *make(){return nullptr;}int empty(const int *p){return p==nullptr;}
                        int main(){int value=7;int *p=&value;p=nullptr;int *q=true?make():&value;
                            printf("%d %d %d %d\\n",global==nullptr,p==nullptr,q==nullptr,empty(nullptr));return 0;}
                        ""","1 1 1 1\n"),
                Arguments.of("truth-and-layout","""
                        #include <stdio.h>
                        int main(){int result=0;if(nullptr)result=99;else result=2;
                            printf("%d %d %d %d %d\\n",result,!((int*)nullptr),(bool)nullptr,(int)sizeof(nullptr),(int)alignof(int*));return 0;}
                        ""","2 1 0 8 8\n"),
                Arguments.of("function-pointers","""
                        #include <stdio.h>
                        int target(int x){return x+1;}
                        int main(){int (*function)(int)=nullptr;int empty=function==nullptr;function=target;
                            printf("%d %d\\n",empty,function(3));function=nullptr;return function!=nullptr;}
                        ""","1 4\n"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void keywordReusesNullPointerSemantics(String name,String source,String expected)throws Exception{
        CppReferenceTest.agree(temporary,name,source,expected);
    }

    static Stream<Arguments> invalidPrograms(){
        return Stream.of(
                Arguments.of("implicit-integer","int main(){int value=nullptr; // bad\nreturn value;}"),
                Arguments.of("implicit-bool","int main(){bool value=nullptr; // bad\nreturn value;}"),
                Arguments.of("nullptr-is-not-lvalue","int main(){int value=0;nullptr=&value; // bad\nreturn 0;}"),
                Arguments.of("nullptr-arithmetic","int main(){return nullptr+1; // bad\n}"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void rejectsImplicitArithmeticAndMutation(String name,String source)throws Exception{
        Path file=temporary.resolve(name+".cpp");
        Files.writeString(file,source);
        var reference=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),
                "-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(reference.timedOut());assertFalse(reference.outputExceeded());assertNotEquals(0,reference.exitCode());
        var api=CppReferenceTest.compiler(source);
        api.runThrough(CppReferenceTest.stage(api,SemanticAnalyzer.class));
        // An invalid assignment target may be diagnosed during parsing or semantic analysis.
        var errors=api.stages().stream().flatMap(stage->api.errors(stage).stream()).toList();
        assertTrue(errors.stream().anyMatch(error->error.range().startLine()==1),errors::toString);
    }

    @Test void cModeStillTreatsNullptrAsAnOrdinaryIdentifier()throws Exception{
        String source="#include <stdio.h>\nint main(){int nullptr=7;printf(\"%d\\n\",nullptr);return 0;}";
        // The C++ reference compiler reserves nullptr, so this C-only control uses MiniC's C frontend.
        var debug=new DebugApi(new SourceFile("identifier.c",source),"",LanguageMode.C);
        for(int i=0;debug.canNext()&&i<1000;i++)debug.next();
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals("7\n",debug.current().runtime().stdout().replace("\r\n","\n"));
    }
}
