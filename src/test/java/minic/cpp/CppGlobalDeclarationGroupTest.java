package minic.cpp;
import minic.compiler.*;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.cpp.support.CppDifferentialHarness;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static minic.cpp.CppReferenceTest.*;
@Timeout(120) final class CppGlobalDeclarationGroupTest {
 @TempDir Path temporary;
 static Stream<Arguments> programs(){return Stream.of(
Arguments.of("sequential-dynamic","int calls=0;int next(int n){++calls;return n;}int a=next(2),b=next(a+3),c=next(b+4);int main(){printf(\"%d %d %d %d\\n\",a,b,c,calls);return 0;}","2 5 9 3\n"),
Arguments.of("mixed-declarators","int plus(int),n=7,*p=&n,a[2]={3,5},(*fn)(int)=plus;int plus(int x){return x+1;}int main(){printf(\"%d %d %d %d\\n\",n,*p,a[1],fn(a[0]));return 0;}","7 7 5 4\n"),
Arguments.of("namespace-and-linkage","namespace A{static int a=2,b=3;int &r=a,*p=&b;}int main(){A::r=7;printf(\"%d %d\\n\",A::a,*A::p);return 0;}","7 3\n"),
Arguments.of("construct-and-destroy","struct Item{int n;Item(int v):n(v){printf(\"c%d \",n);}~Item(){printf(\"d%d \",n);}};Item a(1),b{2};int main(){printf(\"body%d \",a.n+b.n);return 0;}","c1 c2 body3 d2 d1 "),
Arguments.of("constexpr-auto","constexpr auto a=2,b=a+3;int n=4;auto *p=&n,x=7;static_assert(b==5);int main(){printf(\"%d\\n\",*p+x+a+b);return 0;}","18\n"),
Arguments.of("typedef-group","typedef int Integer,*Pointer,Array[2];Integer a=3,b=4;Pointer p=&a;Array values={5,6};int main(){printf(\"%d\\n\",*p+b+values[1]);return 0;}","13\n"),
Arguments.of("extern-prototypes","extern int a,b;int f(int),g(int);int a=2,b=3;int f(int x){return x+1;}int g(int x){return x+2;}int main(){printf(\"%d\\n\",f(a)+g(b));return 0;}","8\n"));}
 @ParameterizedTest(name="{0}") @MethodSource("programs") void agreesWithCpp(String name,String source,String expected)throws Exception{agree(temporary,name,"#include <stdio.h>\n"+source,expected);}
 static Stream<Arguments> invalid(){return Stream.of(
Arguments.of("different-auto-types","auto a=1,b=2.0; // bad\nint main(){return a;}"),
Arguments.of("later-name-initializer","int a=b,b=2; // bad\nint main(){return a;}"),
Arguments.of("duplicate-definition","int a=1,a=2; // bad\nint main(){return a;}"));}
 @ParameterizedTest(name="{0}") @MethodSource("invalid") void invalidNamesOrSharedAuto(String name,String source)throws Exception{reject(temporary,name,source);}
 @Test void retainsCommonSpecifierIndependentDeclaratorsAndSourceIdentity(){
  var api=compiler("int n=7;int *p=&n, x=3;int main(){return *p+x;}");var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
  assertTrue(semantic.succeeded(),()->parser.errors()+" "+semantic.errors());
  var group=parser.result().program().declarations().stream().filter(d->d.getClass().getSimpleName().equals("DeclGroupDecl")).findFirst().orElseThrow();
  var children=AstChildren.of(group);assertEquals(2,children.size());var pointer=(GlobalVarDecl)children.get(0);var value=(GlobalVarDecl)children.get(1);
  assertEquals(MiniType.INT.pointerTo(),pointer.type());assertEquals(MiniType.INT,value.type());assertThrows(UnsupportedOperationException.class,()->children.clear());
  assertTrue(pointer.range().startByte()<value.range().startByte());assertSame(pointer,parser.result().program().globals().get(1));assertSame(value,parser.result().program().globals().get(2));
  assertNotNull(semantic.semanticResult().sourceToCore().get(pointer));assertNotNull(semantic.semanticResult().sourceToCore().get(value));
 }
 @Test void cRetainsAllGlobalDeclarators()throws Exception{
  String source="int a,b;int selected(int);int (*p)(int)=selected,x=4,values[2]={5,6};int selected(int n){return n+1;}int main(){a=p(x);b=values[1];return a==5&&b==6?0:1;}";
  var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),new CppDifferentialHarness.Limits(Duration.ofSeconds(40),Duration.ofSeconds(10),100000,1048576),LanguageMode.C).run("c-global-group",source,"");assertTrue(report.passed(),report::describe);
 }
 @Test void groupedFunctionDefinitionsAreRejected(){
  for(String source:List.of("int x=1,f(){return 0;}int main(){return 0;}","int f(){return 0;},x=1;int main(){return 0;}","int a=1,;int main(){return 0;}")){
   var api=compiler(source);var parser=stage(api,Parser.class);api.runThrough(parser);assertFalse(parser.succeeded(),source);
  }
 }
}
