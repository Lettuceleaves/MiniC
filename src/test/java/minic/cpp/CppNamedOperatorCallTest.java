package minic.cpp;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.util.stream.Stream;

@Timeout(120) final class CppNamedOperatorCallTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("global", "struct Box{int n;};int operator+(Box a,Box b){return a.n+b.n;}int main(){Box a={2};Box b={3};printf(\"%d %d\\n\",::operator+(a,b),operator+(a,b));return 0;}","5 5\n"),
        Arguments.of("namespace", "namespace N{struct Box{int n;};int operator+(Box a,int b){return a.n+b;}}int main(){N::Box a={4};printf(\"%d\\n\",N::operator+(a,5));return 0;}","9\n"),
        Arguments.of("address", "struct Box{int n;};int operator-(Box a,Box b){return a.n-b.n;}int main(){int(*f)(Box,Box)=&::operator-;Box a={7};Box b={2};printf(\"%d\\n\",f(a,b));return 0;}","5\n"),
        Arguments.of("placement", "#include <new>\nint main(){int value=7;void* p=::operator new(sizeof(int),&value);::operator delete(p,&value);printf(\"%d\\n\",p==&value);return 0;}","1\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void namedOperatorFunctionsUseOrdinaryLookupAndCalls(String name,String source,String expected)throws Exception{
        CppReferenceTest.agree(temporary,name,"#include <stdio.h>\n"+source,expected);
    }
    @org.junit.jupiter.api.Test void placementNoexceptContract()throws Exception{
        CppReferenceTest.agree(temporary,"placement-contract",Files.readString(Path.of("src/test/resources/cpp/library-noexcept/placement-contract.cpp")),"");
    }
}
