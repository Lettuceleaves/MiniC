package minic.cpp;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static minic.cpp.CppReferenceTest.agree;
@Timeout(90)
final class CppBracedDefaultArgumentTest {
 @TempDir Path temporary;
 static Stream<Arguments> programs(){return Stream.of(
   Arguments.of("list-default", "int sum(std::initializer_list<int> values={2,3}){return values.begin()[0]+values.begin()[1];}int main(){printf(\"%d %d\\n\",sum(),sum({4,5}));return 0;}","5 9\n"),
   Arguments.of("record-default", "struct Value{int n;Value(int value):n(value){}};int get(Value value={7}){return value.n;}int main(){printf(\"%d %d\\n\",get(),get({9}));return 0;}","7 9\n"),
   Arguments.of("array-reference-default", "int sum(const int(&values)[3]={2,3}){return values[0]+values[1]+values[2];}int main(){printf(\"%d %d\\n\",sum(),sum({4,5,6}));return 0;}","5 15\n")
 );}
 @ParameterizedTest(name="{0}") @MethodSource("programs")
 void bracedDefaultsAndExplicitArgumentsAgree(String name,String source,String expected)throws Exception{
   agree(temporary,name,"#include <stdio.h>\n#include <initializer_list>\n"+source,expected);
 }
}
