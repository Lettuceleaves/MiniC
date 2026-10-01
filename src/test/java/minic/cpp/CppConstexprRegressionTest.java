package minic.cpp;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppConstexprRegressionTest {
 @TempDir Path temporary;
 static Stream<Arguments> programs(){return Stream.of(
  Arguments.of("one-past-array-address","constexpr int a[2]={1,2};constexpr const int*end=&a[2];static_assert(end-a==2);int main(){printf(\"%d\\n\",(int)(end-a));return 0;}","2\n"),
  Arguments.of("array-reference-deduction","template<unsigned long long N>constexpr int sum(const int(&a)[N]){int n=0;for(unsigned long long i=0;i<N;i++)n+=a[i];return n;}constexpr int a[]={2,3,5};static_assert(sum(a)==10);int main(){return 0;}",""),
  Arguments.of("array-element-cv-deduction","template<class T,unsigned long long N>constexpr int last(const T(&a)[N]){return a[N-1];}template<class T,unsigned long long N>constexpr unsigned long long extent(T(&a)[N]){return N;}constexpr int fixed[]={2,5};static_assert(last(fixed)==5&&extent(fixed)==2);int main(){int values[2]={3,7};printf(\"%d %d\\n\",last(values),(int)extent(values));return 0;}","7 2\n"),
  Arguments.of("pointer-difference-cv","int main(){int a[3]={};int*const p=a;const int*q=a+2;volatile int*v=a+1;printf(\"%d %d %d\\n\",(int)(q-p),(int)(v-p),(int)(p-q));return 0;}","2 1 -2\n"),
  Arguments.of("reference-static-conversion-before-reinterpret","constexpr int n=3;constexpr char c=(const char&)n;constexpr const double&r=(const double&)n;static_assert(c==3&&r==3.0);int main(){int x=5;const double&d=(const double&)x;x=9;printf(\"%d %d\\n\",(int)r,(int)d);return 0;}","3 5\n"),
  Arguments.of("address-only-reference-cast-fallback","struct Box{int n;Box*operator&(){return nullptr;}};int main(){Box b={7};Box*p=(Box*)&(char&)(const volatile char&)b;p->n=9;const int&r=(const int&)b.n;printf(\"%d %d %d\\n\",b.n,(int)(p==__builtin_addressof(b)),(int)(&r==__builtin_addressof(b.n)));return 0;}","9 1 1\n"),
  Arguments.of("switch-exhaustive-return","constexpr int f(int n){switch(n){case 1:return 3;case 2:case 3:return 5;default:return 7;}}static_assert(f(1)+f(2)+f(9)==15);int main(){printf(\"%d\\n\",f(3));return 0;}","5\n"),
  Arguments.of("defaulted-constexpr-dmi","struct Box{int x=4;constexpr Box()=default;};constexpr Box b;static_assert(b.x==4);int main(){return 0;}",""),
  Arguments.of("deleted-defaulted-constexpr-exempt","struct Box{int&r;constexpr Box()=default;};int main(){return 0;}",""),
  Arguments.of("defaulted-ordinary-uninitialized-allowed","struct Box{int x;Box()=default;};int main(){Box b;b.x=3;return b.x-3;}",""),
  Arguments.of("nested-defaulted-constexpr-dmi","struct Inner{int x=7;constexpr Inner()=default;};struct Outer{Inner n;constexpr Outer()=default;};constexpr Outer b;static_assert(b.n.x==7);int main(){return 0;}","")
 );}
 @ParameterizedTest(name="{0}") @MethodSource("programs")
 void agrees(String name,String source,String output)throws Exception{agree(temporary,name,"#include <stdio.h>\n"+source,output);}
 @Test void explicitlyDefaultedConstexprMustInitializeScalarMembers()throws Exception {
  reject(temporary,"uninitialized-defaulted-ctor-member","struct Box{int x;constexpr Box()=default;}; // bad\nint main(){return 0;}");
 }
 @Test void arrayDeductionDoesNotDiscardVolatile()throws Exception {
  reject(temporary,"array-volatile-rejection","template<unsigned long long N>int f(const int(&a)[N]){return a[0];}int main(){volatile int a[1]={1};return f(a);} // bad\n");
 }
 @Test void pointerDifferenceStillRequiresMatchingPointeeTypes()throws Exception {
  reject(temporary,"different-pointee-types","int main(){int a=1;double b=2;return &a-&b;} // bad\n");
 }
 @Test void actualReferenceReinterpretationIsNotAConstantExpression()throws Exception {
  reject(temporary,"reference-reinterpretation","constexpr int n=3;constexpr char c=(char&)n; // bad\nint main(){return 0;}");
 }
 @Test void switchBreakAndMissingDefaultDoNotProveARequiredReturn(){
  for(String body:new String[]{"switch(n){case 1:return 1;}","switch(n){case 1:break;default:return 2;}","switch(n){case 1:if(n==1)break;return 1;default:return 2;}"}) {
   var api=compiler("int f(int n){"+body+"}int main(){return 0;}");var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
   assertFalse(semantic.succeeded(),body);assertTrue(semantic.errors().stream().anyMatch(d->d.message().contains("所有路径返回")),()->semantic.errors().toString());
  }
 }
}
