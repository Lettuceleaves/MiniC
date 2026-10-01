package minic.cpp;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static minic.cpp.CppReferenceTest.*;

@Tag("cpp-differential") @Timeout(90)
final class CppConstantValueArgumentTest {
 @TempDir Path temporary;
 static Stream<Arguments> programs(){return Stream.of(
  Arguments.of("static-zero-overloads","struct Value{static const int zero=0;};template<class T>struct Trait{static const int zero=0;};int select(int){return 1;}int select(int*){return 2;}int main(){printf(\"%d %d %d %d %d\\n\",select(Value::zero),select((Value::zero)),select(Trait<int>::zero),select(0),select(nullptr));return 0;}","1 1 1 1 2\n"),
  Arguments.of("static-zero-template-deduction","template<class T>struct Value{static const T zero=0;};template<class T>int size(T x){return (int)sizeof(T)+x;}int main(){printf(\"%d %d %d\\n\",size(Value<int>::zero),size(Value<long long>::zero),size(Value<bool>::zero));return 0;}","4 8 1\n")
 );}
 @ParameterizedTest(name="{0}") @MethodSource("programs")
 void constantValuesKeepTheirArgumentTypes(String name,String source,String output)throws Exception{agree(temporary,name,"#include <stdio.h>\n"+source,output);}
 @ParameterizedTest @ValueSource(strings={"Value::zero","(Value::zero)","Trait<int>::zero"})
 void staticZeroIsNotALiteralNullPointerConstant(String expression)throws Exception{
  reject(temporary,"static-zero-not-null","struct Value{static const int zero=0;};template<class T>struct Trait{static const int zero=0;};void take(int*);int main(){take("+expression+"); // bad\nreturn 0;}");
 }
}
