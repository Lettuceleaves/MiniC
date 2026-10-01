package minic.cpp;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.util.stream.Stream;

@Timeout(120) final class CppLazyImplicitDestructorTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("query-only", "template<class T>struct Member{~Member(){}};template<class T>struct Outer{Member<T> member;};static_assert(noexcept(Outer<int>{}));int main(){return 0;}",""),
        Arguments.of("query-then-use", "int destroyed=0;template<class T>struct Member{~Member(){destroyed++;}};template<class T>struct Outer{Member<T> member;};static_assert(noexcept(Outer<int>{}));int main(){{Outer<int> value;}printf(\"%d\\n\",destroyed);return 0;}","1\n"),
        Arguments.of("unused-dependent-body", "struct Tag{};template<class T>struct Member{~Member(){T::missing();}};template<class T>struct Outer{Member<T> member;};static_assert(noexcept(Outer<Tag>{}));int main(){return 0;}",""),
        Arguments.of("nested-query", "template<class T>struct Member{~Member(){}};template<class T>struct Outer{Member<T> member;};template<class T>struct Layer{Outer<T> item;};static_assert(noexcept(Layer<int>{}));int main(){return 0;}","")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void implicitTemplateDestructorsStayLazyUntilUsed(String name,String source,String expected)throws Exception{
        CppReferenceTest.agree(temporary,name,"#include <stdio.h>\n"+source,expected);
    }
    @ParameterizedTest @ValueSource(strings={"library-noexcept/associative-adaptor-contract.cpp","library-lookup/lookup-participation.cpp"})
    void standardContainerQueriesDoNotEmitUninstantiatedDependencies(String name)throws Exception{
        CppReferenceTest.agree(temporary,"container-query",Files.readString(Path.of("src/test/resources/cpp").resolve(name)),name.contains("lookup-participation")?"4\n":"");
    }
}
