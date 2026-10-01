package minic.cpp;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Compile now; execute with the final unified C++ acceptance run. */
@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppUnknownArrayTest {
 @TempDir Path temporary;
 static Stream<Arguments> programs(){return Stream.of(
  Arguments.of("partial-specialization","template<class T>struct is_array{static const int value=0;};template<class T>struct is_array<T[]>{static const int value=1;};template<class T,unsigned long long N>struct is_array<T[N]>{static const int value=2;};int main(){printf(\"%d %d %d\\n\",is_array<int>::value,is_array<int[]>::value,is_array<int[3]>::value);return 0;}","0 1 2\n"),
  Arguments.of("remove-extent","template<class T>struct remove_extent{typedef T type;};template<class T>struct remove_extent<T[]>{typedef T type;};int main(){remove_extent<const int[]>::type x=3;printf(\"%d %d\\n\",x,(int)sizeof(x));return 0;}","3 4\n"),
  Arguments.of("pointer-and-typedef","typedef int Array[];int main(){Array*p=nullptr;printf(\"%d %d\\n\",(int)sizeof(p),(int)alignof(Array));return 0;}","8 4\n"),
  Arguments.of("parameter-decay","int first(int values[]){return values[0];}int main(){int a[2]={3,4};printf(\"%d\\n\",first(a));return 0;}","3\n"),
  Arguments.of("infer-local","int main(){int values[]={2,3,5};printf(\"%d %d\\n\",(int)sizeof(values),values[2]);return 0;}","12 5\n"),
  Arguments.of("infer-global","int values[]={4,7};int main(){printf(\"%d %d\\n\",(int)sizeof(values),values[1]);return 0;}","8 7\n"),
  Arguments.of("infer-outer-multidimensional","int main(){int values[][2]={{1,2},{3,4}};printf(\"%d %d\\n\",(int)sizeof(values),values[1][1]);return 0;}","16 4\n"),
  Arguments.of("infer-flat-multidimensional","int main(){int values[][2]={1,2,3,4,5};printf(\"%d %d %d\\n\",(int)sizeof(values),values[2][0],values[2][1]);return 0;}","24 5 0\n"),
  Arguments.of("infer-string","int main(){char text[]=\"abc\";printf(\"%d %s\\n\",(int)sizeof(text),text);return 0;}","4 abc\n"),
  Arguments.of("extern-completed-later","extern int values[];int values[2]={2,8};int main(){printf(\"%d %d\\n\",(int)sizeof(values),values[1]);return 0;}","8 8\n"),
  Arguments.of("extern-after-complete","int values[2]={2,8};extern int values[];int main(){printf(\"%d %d\\n\",(int)sizeof(values),values[1]);return 0;}","8 8\n"),
  Arguments.of("record-array-deduction","struct Pair{int a;int b;};int main(){Pair values[]={{2,3},{5,7}};printf(\"%d %d\\n\",(int)sizeof(values),values[1].b);return 0;}","16 7\n")
 );}
 @ParameterizedTest(name="{0}") @MethodSource("programs")
 void matchesCpp(String name,String source,String output)throws Exception{agree(temporary,name,"#include <stdio.h>\n"+source,output);}
 static Stream<Arguments> invalidPrograms(){return Stream.of(
  Arguments.of("sizeof-incomplete","int main(){return sizeof(int[]); // bad\n}"),
  Arguments.of("uninitialized-object","int main(){int values[]; // bad\nreturn 0;}"),
  Arguments.of("empty-inferred-array","int main(){int values[]={}; // bad\nreturn 0;}"),
  Arguments.of("unknown-record-field","struct Invalid{int values[];}; // bad\nint main(){return 0;}"),
  Arguments.of("pointer-arithmetic-incomplete-pointee","int main(){int(*p)[]=nullptr;++p; // bad\nreturn 0;}"),
  Arguments.of("definition-cannot-repair-prior-query","extern int values[];int count=sizeof(values); // bad\nint values[2];int main(){return count;}"),
  Arguments.of("mismatched-completion","extern int values[];double values[2]; // bad\nint main(){return 0;}")
 );}
 @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
 void rejectsIncompleteObjects(String name,String source)throws Exception{reject(temporary,name,source);}
 @Test void incompleteArrayIsARealTypeWithoutSize(){
  MiniType array=MiniType.INT.arrayOf(-1);
  assertEquals("int[]",array.toString());assertFalse(TypeLayout.hasFixedLayout(array));
  assertThrows(IllegalArgumentException.class,()->TypeLayout.sizeOf(array));
  assertEquals(4,TypeLayout.alignmentOf(array));assertEquals(8,TypeLayout.sizeOf(array.pointerTo()));
  assertThrows(IllegalArgumentException.class,()->MiniType.INT.arrayOf(0));
  assertThrows(IllegalArgumentException.class,()->MiniType.INT.arrayOf(-2));
 }
 @Test void omittedInnerDimensionIsRejectedDuringParsing()throws Exception{
  String source="typedef int Invalid[2][];int main(){return 0;}";
  var api=compiler(source);var parser=stage(api,minic.compiler.parser.Parser.class);api.runThrough(parser);
  assertFalse(parser.succeeded());
  assertTrue(parser.errors().stream().anyMatch(d->d.code().equals("PAR001")
    && d.message().contains("Only the outermost array extent")),()->parser.errors().toString());
  var report=new minic.cpp.support.CppDifferentialHarness(temporary,
    minic.cpp.support.CppDifferentialHarness.referenceCompiler(System.getenv()),
    minic.cpp.support.CppDifferentialHarness.Limits.defaults(),minic.compiler.LanguageMode.CPP17_ALGORITHM).compile("unknown-inner-dimension",source);
  report.outcomes().values().forEach(outcome->assertEquals(minic.cpp.support.CppDifferentialHarness.Status.COMPILE_ERROR,outcome.status(),report::describe));
 }
 @Test void constructionQueryDoesNotTreatUnknownArraysAsObjects(){
  var api=compiler("static_assert(!__is_constructible(int[]));int main(){return 0;}");
  var semantic=stage(api,minic.compiler.semantic.SemanticAnalyzer.class);api.runThrough(semantic);
  assertTrue(semantic.succeeded(),()->semantic.errors().toString());
  assertNotNull(api.runToIr());
 }
}
