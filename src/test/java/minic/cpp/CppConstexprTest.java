package minic.cpp;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.parser.node.*;
import minic.compiler.semantic.SemanticAnalyzer;

/** Saved acceptance cases. Run only with the final unified compiler verification. */
@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppConstexprTest {
 @TempDir Path temporary;
 static Stream<Arguments> programs(){return Stream.of(
  Arguments.of("scalar-loop","constexpr int sum(int n){int s=0;for(int i=0;i<=n;++i)s+=i;return s;}constexpr int result=sum(6);static_assert(result==21,\"loop\");int main(){printf(\"%d\\n\",result);return 0;}","21\n"),
  Arguments.of("recursive-function","constexpr int factorial(int n){return n<2?1:n*factorial(n-1);}static_assert(factorial(6)==720);int main(){return 0;}",""),
  Arguments.of("local-constant-array-bound","constexpr int twice(int n){return 2*n;}int main(){constexpr int n=twice(3);int a[n]={};static_assert(n==6);printf(\"%d\\n\",(int)sizeof(a));return 0;}","24\n"),
  Arguments.of("nttp-call","constexpr int f(int n){return n+1;}template<int N>struct Box{int data[N];};static_assert(sizeof(Box<f(2)>)==12);int main(){return 0;}",""),
  Arguments.of("aggregate-constexpr","struct Point{int x;int y;};constexpr Point p={2,3};static_assert(p.x+p.y==5);int main(){printf(\"%d\\n\",p.y);return 0;}","3\n"),
  Arguments.of("member-constructor","struct Box{int n;constexpr Box(int x):n(x){}constexpr int get()const{return n;}constexpr explicit operator bool()const{return n!=0;}};constexpr Box b(7);static_assert(b.get()==7);static_assert(b);int main(){return 0;}",""),
  Arguments.of("out-of-line-definition","struct Box{int n;constexpr Box(int);constexpr int get()const;};constexpr Box::Box(int x):n(x){}constexpr int Box::get()const{return n;}constexpr Box b(4);static_assert(b.get()==4);int main(){return 0;}",""),
  Arguments.of("mutable-local-member","struct Box{int n;constexpr Box(int x):n(x){}constexpr void add(int x){n+=x;}};constexpr int f(){Box b(2);b.add(5);return b.n;}static_assert(f()==7);int main(){return 0;}",""),
  Arguments.of("array-reference","template<unsigned long long N>constexpr int sum(const int(&a)[N]){int n=0;for(unsigned long long i=0;i<N;i++)n+=a[i];return n;}constexpr int a[]={2,3,5};static_assert(sum(a)==10);int main(){return 0;}",""),
  Arguments.of("static-reference-temporary","constexpr const int&r=3;static_assert(r==3);int main(){printf(\"%d\\n\",r);return 0;}","3\n"),
  Arguments.of("pointer-reference-identity","constexpr int x=3;constexpr const int*p=&x;constexpr const int&r=x;static_assert(*p==3&&&r==p);int main(){printf(\"%d\\n\",*p);return 0;}","3\n"),
  Arguments.of("builtin-addressof","struct Box{int n;Box*operator&(){return nullptr;}};constexpr Box b={4};static_assert(__builtin_addressof(b)!=nullptr);static_assert(__builtin_addressof(b)==&b);int main(){return 0;}",""),
  Arguments.of("implicit-lambda","constexpr auto f=[](int x){return x*3;};static_assert(f(4)==12);int main(){return 0;}",""),
  Arguments.of("generic-lambda","constexpr auto f=[](auto x)constexpr{return x+2;};static_assert(f(3)==5);static_assert(f(3LL)==5);int main(){return 0;}",""),
  Arguments.of("capture-lambda","constexpr int f(){int n=4;auto lambda=[n](int x)constexpr{return n+x;};return lambda(3);}static_assert(f()==7);int main(){return 0;}",""),
  Arguments.of("lambda-function-pointer","constexpr int(*f)(int)=[](int n){return n+1;};static_assert(f(4)==5);int main(){return 0;}",""),
  Arguments.of("class-static-constexpr","template<class T,T V>struct Constant{static constexpr T value=V;constexpr operator T()const{return value;}constexpr T operator()()const{return value;}};template<class T,T V>constexpr T Constant<T,V>::value;static_assert(Constant<int,4>{}()==4);static_assert(Constant<bool,true>{});constexpr const int*p=&Constant<int,4>::value;static_assert(*p==4);int main(){return 0;}",""),
  Arguments.of("constructor-array-zero","struct Bits{unsigned long long words[2];constexpr Bits():words{}{}constexpr Bits(unsigned long long n):words{n}{}constexpr unsigned long long sum()const{return words[0]+words[1];}};constexpr Bits b(7);static_assert(b.sum()==7);static_assert(Bits().sum()==0);int main(){return 0;}",""),
  Arguments.of("shortcircuit","constexpr int divide(int x){return 8/x;}static_assert(true||divide(0));static_assert(false?divide(0):3);int main(){return 0;}",""),
  Arguments.of("block-control-flow","constexpr int f(){int n=0;int i=0;do{++i;if(i==2)continue;n+=i;if(i==4)break;}while(i<8);switch(n){case 8:return 9;default:return 0;}}static_assert(f()==9);int main(){return 0;}",""),
  Arguments.of("const-integral-without-keyword","const int n=3;static_assert(n==3);int main(){const int local=4;int a[local]={};static_assert(sizeof(a)==16);return 0;}",""),
  Arguments.of("string-storage","constexpr const char*p=\"abc\";static_assert(p[1]=='b');static_assert(p+3-p==3);int main(){return 0;}",""),
  Arguments.of("static-address-before-dynamic","extern int*p;int earlier=*p;int target=13;int*p=&target;int main(){printf(\"%d\\n\",earlier);return 0;}","13\n"),
  Arguments.of("static-member-address-no-read","struct Box{int field;};Box ordinary={7};constexpr int*p=&ordinary.field;static_assert(p==&ordinary.field);int main(){printf(\"%d\\n\",*p);return 0;}","7\n"),
  Arguments.of("negative-zero-comparison","static_assert(-0.0==0.0);static_assert(!(-0.0<0.0));int main(){return 0;}",""),
  Arguments.of("dereferenced-function-pointer","constexpr int plus(int n){return n+1;}constexpr auto p=&plus;static_assert((*p)(3)==4);static_assert(&*p==p);int main(){return 0;}",""),
  Arguments.of("constexpr-variadic-without-cursor","constexpr int first(int n,...){return n;}static_assert(first(3,9,4)==3);int main(){return 0;}",""),
  Arguments.of("trivial-defaulted-destructor","struct Box{int n;constexpr Box(int x):n(x){}~Box()=default;};constexpr int f(){Box b(7);return b.n;}static_assert(f()==7);constexpr Box b(4);static_assert(b.n==4);int main(){return 0;}",""),
  Arguments.of("defaulted-copy-assignment","struct Box{int n;constexpr Box(int x):n(x){}Box&operator=(const Box&)=default;};constexpr int f(){Box a(1);Box b(4);a=b;return a.n;}static_assert(f()==4);int main(){return 0;}",""),
  Arguments.of("one-past-array-address","constexpr int a[2]={1,2};constexpr const int*end=&a[2];static_assert(end-a==2);int main(){return 0;}",""),
  Arguments.of("nested-ineligible-lambda-not-invoked","constexpr int f(){auto unused=[](){static int n=1;return n;};return 7;}static_assert(f()==7);int main(){return 0;}",""),
  Arguments.of("template-out-of-line-special-members","template<class T>struct Box{T n;constexpr Box(T);constexpr operator T()const;};template<class T>constexpr Box<T>::Box(T x):n(x){}template<class T>constexpr Box<T>::operator T()const{return n;}constexpr Box<int>b(7);static_assert((int)b==7);int main(){return 0;}",""),
  Arguments.of("nonconstexpr-specialization-independent","template<class T>constexpr int f(T){return 1;}template<>int f<int>(int){return 2;}static_assert(f('a')==1);int main(){printf(\"%d\\n\",f(1));return 0;}","2\n"),
  Arguments.of("constexpr-specialization-independent","template<class T>int f(T){return 1;}template<>constexpr int f<int>(int){return 2;}static_assert(f(1)==2);int main(){return 0;}",""),
  Arguments.of("class-scope-static-assert","template<int N>struct Box{static_assert(N>0,\"positive\");int data[N];};static_assert(sizeof(Box<2>)==8);int main(){return 0;}","")
 );}
 @ParameterizedTest(name="{0}") @MethodSource("programs") void agrees(String name,String source,String output)throws Exception{agree(temporary,name,"#include <stdio.h>\n"+source,output);}
 static Stream<Arguments> invalid(){return Stream.of(
  Arguments.of("ordinary-function","int f(){return 3;}constexpr int n=f(); // bad\nint main(){return 0;}"),
  Arguments.of("mutable-global-read","int n=3;constexpr int k=n; // bad\nint main(){return 0;}"),
  Arguments.of("global-write","int n;constexpr int f(){return ++n;}constexpr int k=f(); // bad\nint main(){return 0;}"),
  Arguments.of("volatile-read","volatile int n=3;constexpr int k=n; // bad\nint main(){return 0;}"),
  Arguments.of("divide-zero","constexpr int n=1/0; // bad\nint main(){return 0;}"),
  Arguments.of("signed-overflow","constexpr int n=2147483647+1; // bad\nint main(){return 0;}"),
  Arguments.of("floating-cast-out-of-range","constexpr int n=(int)2147483648.0; // bad\nint main(){return 0;}"),
  Arguments.of("one-past-read","constexpr int a[2]={1,2};constexpr int n=a[2]; // bad\nint main(){return 0;}"),
  Arguments.of("reinterpret-reference","constexpr int n=3;constexpr char c=(char&)n; // bad\nint main(){return 0;}"),
  Arguments.of("reinterpret-pointer","constexpr int a[2]={1,2};constexpr const int*p=(const int*)&a; // bad\nint main(){return 0;}"),
  Arguments.of("nontrivial-destructor","struct Box{int x;constexpr Box():x(1){}~Box(){}};constexpr Box b; // bad\nint main(){return 0;}"),
  Arguments.of("uninitialized-constexpr","constexpr int n; // bad\nint main(){return 0;}"),
  Arguments.of("uninitialized-defaulted-ctor-member","struct Box{int x;constexpr Box()=default;}; // bad\nint main(){return 0;}"),
  Arguments.of("nonliteral-local","struct Box{Box(){}~Box(){}};constexpr int f(){Box b;return 0;} // bad\nint main(){return 0;}"),
  Arguments.of("uninitialized-ctor-member","struct Box{int x;constexpr Box(){}}; // bad\nint main(){return 0;}"),
  Arguments.of("static-local-function","constexpr int f(){static int n=1;return n;} // bad\nint main(){return 0;}"),
  Arguments.of("automatic-address","int main(){int n=1;constexpr int*p=&n; // bad\nreturn 0;}"),
  Arguments.of("false-assertion","static_assert(2==3,\"must reject\"); // bad\nint main(){return 0;}"),
  Arguments.of("bad-builtin-addressof","int main(){__builtin_addressof(3); // bad\nreturn 0;}"),
  Arguments.of("nonconstexpr-specialization-not-inherited","template<class T>constexpr int f(T){return 1;}template<>int f<int>(int){return 2;}constexpr int n=f(1); // bad\nint main(){return 0;}"),
  Arguments.of("template-out-of-line-constexpr-mismatch","template<class T>struct Box{constexpr int get()const;};template<class T>int Box<T>::get()const{return 1;}Box<int>b; // bad\nint main(){return b.get();}"),
  Arguments.of("specifier-mismatch","constexpr int f();int f(){return 3;} // bad\nint main(){return 0;}"),
  Arguments.of("constexpr-structured-binding","constexpr int a[2]={1,2};constexpr auto[x,y]=a; // bad\nint main(){return 0;}")
 );}
 @ParameterizedTest(name="{0}") @MethodSource("invalid") void rejects(String name,String source)throws Exception{
  if(!name.equals("constexpr-structured-binding")){reject(temporary,name,source);return;}
  // constexpr is forbidden on a C++17 structured binding; rejecting it during parsing is valid.
  var file=temporary.resolve(name+".cpp");java.nio.file.Files.writeString(file,source);
  var reference=minic.cpp.support.BoundedProcess.run(java.util.List.of(minic.cpp.support.CppDifferentialHarness.referenceCompiler(System.getenv()),
    "-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",java.time.Duration.ofSeconds(20),65536);
  assertFalse(reference.timedOut());assertFalse(reference.outputExceeded());assertNotEquals(0,reference.exitCode(),reference::stderr);
  var api=compiler(source);var parser=stage(api,Parser.class);api.runThrough(parser);
  assertFalse(parser.succeeded());assertTrue(parser.errors().stream().anyMatch(error->error.range().startLine()==1),()->parser.errors().toString());
 }
 // Same-type prvalues initialize the destination directly: N4659 [dcl.init]/17.6.1.
 // https://timsong-cpp.github.io/cppwp/n4659/dcl.init#17.6.1
 // MinGW G++ 8.1 rejects the original self-pointer source. Related GCC PR110822
 // acknowledges a constexpr factory/self-address example as valid code:
 // https://gcc.gnu.org/pipermail/gcc-bugs/2023-August/832104.html
 // Preserve the exact original source and test both MiniC engines independently
 // of that legacy oracle. Nontrivial/deleted copies also exclude optional ABI copies.
 static Stream<Arguments> destinationIdentityPrograms(){return Stream.of(
  Arguments.of("same-object-return","struct Self{const Self*p;constexpr Self():p(this){}};constexpr Self make(){return Self();}constexpr Self s=make();static_assert(s.p==&s);int main(){return 0;}",""),
  Arguments.of("self-nontrivial-copy","struct Self{const Self*p;constexpr Self():p(this){}constexpr Self(const Self&):p(this){}};constexpr Self make(){return Self();}constexpr Self s=make();static_assert(s.p==&s);int main(){printf(\"%d\\n\",s.p==&s);return 0;}","1\n"),
  Arguments.of("self-deleted-copy","struct Self{const Self*p;constexpr Self():p(this){}Self(const Self&)=delete;};constexpr Self make(){return Self();}constexpr Self s=make();static_assert(s.p==&s);int main(){printf(\"%d\\n\",s.p==&s);return 0;}","1\n")
 );}
 @ParameterizedTest(name="{0}") @MethodSource("destinationIdentityPrograms")
 void prvalueConstructionPreservesTheConstantDestinationAddress(String name,String source,String expected)throws Exception{
  var report=new minic.cpp.support.CppDifferentialHarness(temporary,
   minic.cpp.support.CppDifferentialHarness.referenceCompiler(System.getenv()),
   minic.cpp.support.CppDifferentialHarness.Limits.defaults(),minic.compiler.LanguageMode.CPP17_ALGORITHM)
   .run(name,"#include <stdio.h>\n"+source,"");
  for(var backend:java.util.List.of(minic.cpp.support.CppDifferentialHarness.Backend.MINIC_NATIVE,
    minic.cpp.support.CppDifferentialHarness.Backend.MINIC_DEBUG)){
   var outcome=report.outcomes().get(backend);
   assertEquals(minic.cpp.support.CppDifferentialHarness.Status.OK,outcome.status(),report::describe);
   assertEquals(0,outcome.exitCode(),report::describe);
   assertEquals(expected,outcome.stdout().replace("\r\n","\n"),report::describe);
  }
 }
 @Test void sourceMetadataAndOriginsSurviveNormalization(){
  var api=compiler("constexpr int plus(int x){return x+1;}constexpr int n=plus(2);int main(){constexpr int k=plus(3);static_assert(k==4);return k-n-1;}");
  var parser=stage(api,Parser.class);api.runThrough(parser);assertTrue(parser.succeeded(),()->parser.errors().toString());
  var source=parser.result().program();
  var functions=nodes(source).stream().filter(FunctionDecl.class::isInstance).map(FunctionDecl.class::cast).toList();
  assertTrue(functions.stream().anyMatch(FunctionDecl::constexprSpecifier));
  assertTrue(nodes(source).stream().filter(GlobalVarDecl.class::isInstance).map(GlobalVarDecl.class::cast).anyMatch(GlobalVarDecl::constexprSpecifier));
  assertTrue(nodes(source).stream().filter(VarDeclStmt.class::isInstance).map(VarDeclStmt.class::cast).anyMatch(VarDeclStmt::constexprSpecifier));
  var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);assertTrue(semantic.succeeded(),()->semantic.errors().toString());
  assertNull(AstChildren.firstCppSyntax(semantic.semanticResult().program()));
 }
}
