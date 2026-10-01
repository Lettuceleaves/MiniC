package minic.cpp;

import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120) final class CppNullptrObjectTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("global-and-copy", "std::nullptr_t global=nullptr;int main(){std::nullptr_t local=global;int*p=local;printf(\"%d %d\\n\",p==nullptr,(int)sizeof(local));return 0;}","1 8\n"),
        Arguments.of("value-initialization", "int main(){std::nullptr_t a{};std::nullptr_t b=std::nullptr_t();auto c=std::nullptr_t{};printf(\"%d %d %d\\n\",a==nullptr,b==nullptr,c==nullptr);return 0;}","1 1 1\n"),
        Arguments.of("references", "void assign(std::nullptr_t& value){value=nullptr;}int main(){std::nullptr_t value=nullptr;assign(value);const std::nullptr_t& r=std::nullptr_t{};printf(\"%d %d\\n\",value==nullptr,r==nullptr);return 0;}","1 1\n"),
        Arguments.of("array-and-field", "struct Holder{std::nullptr_t value;};int main(){std::nullptr_t a[2]={nullptr,nullptr};Holder h={nullptr};printf(\"%d %d\\n\",a[1]==nullptr,h.value==nullptr);return 0;}","1 1\n"),
        Arguments.of("overload", "int pick(std::nullptr_t){return 1;}int pick(void*){return 2;}std::nullptr_t make(){return nullptr;}int main(){std::nullptr_t value=nullptr;printf(\"%d %d\\n\",pick(value),pick(make()));return 0;}","1 1\n"),
        Arguments.of("constexpr", "constexpr std::nullptr_t value=nullptr;static_assert(value==nullptr);int main(){printf(\"%d\\n\",value==nullptr);return 0;}","1\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void nullptrObjectsPreserveSourceIdentityWithPointerStorage(String name,String source,String expected)throws Exception{
        CppReferenceTest.agree(temporary,name,"#include <cstddef>\n#include <stdio.h>\n"+source,expected);
    }
    @ParameterizedTest @ValueSource(strings={"int main(){std::nullptr_t p=1;return 0;}","int main(){int a=0;std::nullptr_t p=&a;return 0;}","int main(){std::nullptr_t p=nullptr;return p+1;}","int main(){std::nullptr_t p=nullptr;int x=p;return x;}"})
    void pointerRepresentationDoesNotBroadenSourceConversions(String source)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).compile("invalid-nullptr-object","#include <cstddef>\n"+source);
        report.outcomes().values().forEach(outcome->assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,outcome.status(),report::describe));
    }
    @org.junit.jupiter.api.Test void standardModelTypes()throws Exception{
        CppReferenceTest.agree(temporary,"model-types",Files.readString(Path.of("src/test/resources/cpp/library-lookup/model-types.cpp")),"1 3 3 -2\n");
    }
}
