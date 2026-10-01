package minic.cpp;

import minic.compiler.*;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
final class CppMemberPlaceholderTest {
    @TempDir Path temporary;

    static final String SOURCE="""
            #include <stdio.h>
            struct Box{int value;auto get()const{return value;}auto& ref(){return value;}
                decltype(auto) alias(){return (value);}auto trailing()const->decltype(value){return value;}
                auto external();decltype(1) count;};
            auto Box::external(){return value+1;}
            template<class T>struct Holder{T value;auto get(){return value;}
                template<class U>auto add(U x){return value+x;}};
            int main(){Box box={3,1};box.ref()=5;box.alias()+=2;
                Holder<int> item={2};printf("%d %d %d %d %d %d\\n",box.get(),box.trailing(),
                    box.external(),box.count,item.get(),item.add(4));return 0;}
            """;

    @Test void parserAcceptsPlaceholderResultsAndDecltypeFields() {
        var api=new CompilerApi(new SourceFile("member-auto.cpp",SOURCE),LanguageMode.CPP17_ALGORITHM);
        var parser=api.stages().stream().filter(Parser.class::isInstance).map(Parser.class::cast).findFirst().orElseThrow();
        api.runThrough(parser);
        assertTrue(parser.succeeded(),parser.errors()::toString);
    }

    @Test void memberReturnDeductionPreservesReferencesAndClassLookup() throws Exception {
        var report=new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run("member-auto",SOURCE,"");
        assertTrue(report.passed(),report::describe);
        assertEquals("7 7 8 1 2 6\n",report.outcomes().get(CppDifferentialHarness.Backend.MINIC_DEBUG).stdout());
    }

    @Test void nonStaticFieldsCannotUseAnAutoPlaceholder() {
        for(String declaration:java.util.List.of("auto value=1;","decltype(auto) value=1;")) {
            var api=new CompilerApi(new SourceFile("invalid-auto-field.cpp",
                    "struct Box{"+declaration+"};int main(){return 0;}"),LanguageMode.CPP17_ALGORITHM);
            var semantic=api.stages().stream().filter(SemanticAnalyzer.class::isInstance)
                    .map(SemanticAnalyzer.class::cast).findFirst().orElseThrow();
            api.runThrough(semantic);
            assertTrue(api.stages().stream().flatMap(stage->stage.errors().stream()).anyMatch(error->
                    error.range().startLine()==1),declaration);
        }
    }
}
