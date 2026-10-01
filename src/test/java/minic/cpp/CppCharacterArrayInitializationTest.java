package minic.cpp;

import minic.compiler.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
final class CppCharacterArrayInitializationTest {
    @TempDir Path temporary;

    @Test void initializedArraysRetainStorageTerminatorPaddingAndEncoding() throws Exception {
        String source="""
                #include <stdio.h>
                char global[]={"abc"};
                const char padded[7]="xy";
                struct Text{char letters[5];};
                int main(){char local[]="hello";char braced[5]={"ab"};
                    char nested[][4]={"abc",{"xy"}};Text object={"text"};
                    unsigned char bytes[]=u8"é";signed char signedBytes[]="ok";
                    char embedded[5]="a\\0b";static char persistent[4]="old";
                    local[0]='H';global[0]='A';persistent[0]='n';
                    printf("%s %s %s %s %s %s %d %d %d %d %d %d\\n",local,global,braced,
                        nested[1],object.letters,persistent,(int)sizeof(local),(int)sizeof(bytes),
                        bytes[0]+bytes[1],signedBytes[1],embedded[2],padded[6]+braced[4]+nested[1][3]);
                    return 0;}
                """;
        var report=harness().run("character-array-initialization",source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals("Hello Abc ab xy text nld 6 3 364 107 98 0\n",
                report.outcomes().get(CppDifferentialHarness.Backend.MINIC_DEBUG).stdout());
    }

    @ParameterizedTest
    @ValueSource(strings={"char text[3]=\"abc\";", "char text[3]={\"abc\"};",
            "int text[4]=\"abc\";", "char text[4]=u\"abc\";",
            "char text[2][3]={\"abc\",\"x\"};"})
    void rejectsWrongElementEncodingOrInsufficientTerminatorSpace(String declaration) throws Exception {
        var report=harness().compile("invalid-character-array","int main(){"+declaration+"return 0;}");
        for(var result:report.outcomes().values())
            assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,result.status(),report::describe);
    }

    @Test void constexprCharacterArrayCanBeReadDuringConstantEvaluation() {
        var compiler=new CompilerApi(new SourceFile("constexpr-array.cpp","""
                constexpr char text[]="abc";
                static_assert(sizeof(text)==4);
                static_assert(text[0]=='a'&&text[3]==0);
                int main(){return 0;}
                """),LanguageMode.CPP17_ALGORITHM);
        var semantic=compiler.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
        compiler.runThrough(semantic);
        assertTrue(semantic.succeeded(),semantic.errors()::toString);
    }

    private CppDifferentialHarness harness() {
        return new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM);
    }
}
