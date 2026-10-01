package minic.cpp;
import java.nio.file.Path;import java.util.stream.Stream;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.*;
import static minic.cpp.CppReferenceTest.*;
@Tag("cpp-differential") @Timeout(90)
final class CppArrayBraceElisionTest {
 @TempDir Path temporary;
 static Stream<Arguments> programs(){return Stream.of(
  Arguments.of("fixed-flat","int main(){int a[2][2]={1,2,3};printf(\"%d %d %d %d\\n\",a[0][0],a[0][1],a[1][0],a[1][1]);return 0;}","1 2 3 0\n"),
  Arguments.of("unknown-mixed","int main(){int a[][2]={1,2,{3},4,5};printf(\"%llu %d %d %d\\n\",sizeof(a),a[1][1],a[2][0],a[2][1]);return 0;}","24 0 4 5\n"),
  Arguments.of("deep-flat","int main(){int a[2][2][2]={1,2,3,4,5};printf(\"%d %d %d\\n\",a[0][1][1],a[1][0][0],a[1][1][1]);return 0;}","4 5 0\n"),
  Arguments.of("global-const-narrow","const short a[][2]={1,2,3};int main(){printf(\"%llu %d %d\\n\",sizeof(a),a[1][0],a[1][1]);return 0;}","8 3 0\n"),
  Arguments.of("evaluated-once-in-order","int calls;int next(){return ++calls;}int main(){int a[][2]={next(),next(),next()};printf(\"%d %d %d %d %d\\n\",a[0][0],a[0][1],a[1][0],a[1][1],calls);return 0;}","1 2 3 0 3\n"),
  Arguments.of("character-array-elements","int main(){char a[][3]={\"ab\",\"c\"};printf(\"%llu %s %s %d\\n\",sizeof(a),a[0],a[1],a[1][1]);return 0;}","6 ab c 0\n")
 );}
 @ParameterizedTest(name="{0}") @MethodSource("programs")
 void arrayDimensionsConsumeElidedBraces(String name,String source,String output)throws Exception{agree(temporary,name,"#include <stdio.h>\n"+source,output);}
 @ParameterizedTest @ValueSource(strings={
"int main(){int a[1][2]={1,2,3}; // bad\nreturn 0;}",
"int main(){int a[1][2]={1.5,2}; // bad\nreturn 0;}",
"int main(){int a[2][2]={{1,2,3}}; // bad\nreturn 0;}"
 }) void excessAndNarrowingRemainInvalid(String source)throws Exception{reject(temporary,"invalid-array-elision",source);}
}
