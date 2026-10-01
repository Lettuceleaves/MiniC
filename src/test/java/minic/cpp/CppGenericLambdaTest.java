package minic.cpp;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static minic.cpp.CppReferenceTest.*;
/** Deferred unified runtime/oracle acceptance for generic closure integration. */
@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppGenericLambdaTest {
 @TempDir Path temporary;
 static Stream<Arguments> programs(){return Stream.of(
 Arguments.of("generic-independent-parameters","#include <stdio.h>\nint main(){auto add=[](auto a,auto b){return a+b;};int x=add(2,3);double y=add(2.5,4);printf(\"%d %.1f\\n\",x,y);return 0;}","5 6.5\n"),
 Arguments.of("generic-const-reference-comparator","#include <stdio.h>\nstruct P{int x;};int main(){auto less=[](const auto&a,const auto&b){return a.x<b.x;};P a={2};P b={5};printf(\"%d\\n\",less(a,b));return 0;}","1\n"),
 Arguments.of("generic-default-capture","#include <stdio.h>\nint main(){int offset=3;auto add=[=](auto n){return n+offset;};offset=8;printf(\"%d %lld\\n\",add(2),add(4LL));return 0;}","5 7\n"),
 Arguments.of("generic-reference-capture","#include <stdio.h>\nint main(){int total=0;auto add=[&](auto n){total+=n;};add(3);add(4LL);printf(\"%d\\n\",total);return 0;}","7\n"),
 Arguments.of("generic-mutable-copy","#include <stdio.h>\nint main(){int x=1;auto add=[x](auto n)mutable{x+=n;return x;};int a=add(2);int b=add(3);printf(\"%d %d %d\\n\",a,b,x);return 0;}","3 6 1\n"),
 Arguments.of("generic-reference-return","#include <stdio.h>\nint main(){auto get=[](auto&x)->decltype(auto){return(x);};int x=2;get(x)=7;printf(\"%d\\n\",x);return 0;}","7\n"),
 Arguments.of("generic-forwarding-reference","#include <stdio.h>\nint main(){auto get=[](auto&&x){return x+1;};int x=2;printf(\"%d %d\\n\",get(x),get(4));return 0;}","3 5\n"),
 Arguments.of("generic-function-pointer-conversion","#include <stdio.h>\nint main(){int(*a)(int)=[](auto n){return n+1;};long long(*b)(long long)=[](auto n){return n+2;};printf(\"%d %lld\\n\",a(3),b(5));return 0;}","4 7\n"),
 Arguments.of("generic-same-closure-two-pointer-instantiations","#include <stdio.h>\nint main(){auto f=[](auto n){return n+1;};int(*a)(int)=f;long long(*b)(long long)=f;printf(\"%d %lld\\n\",a(3),b(5));return 0;}","4 6\n"),
 Arguments.of("generic-algorithm-function-template","#include <stdio.h>\ntemplate<class F>int apply(F f,int x){return f(x);}int main(){int k=4;auto add=[=](auto n){return n+k;};printf(\"%d\\n\",apply(add,3));return 0;}","7\n"),
 Arguments.of("generic-nested-lambda-capture","#include <stdio.h>\nint main(){int k=3;auto outer=[=](auto x){return [=](auto y){return x+y+k;};};auto inner=outer(2);printf(\"%d\\n\",inner(4));return 0;}","9\n"),
 Arguments.of("generic-local-shadows-outer","#include <stdio.h>\nint main(){int x=9;auto f=[](auto n){int x=n;return x+1;};printf(\"%d %d\\n\",f(2),x);return 0;}","3 9\n")
 );}
 @ParameterizedTest(name="{0}") @MethodSource("programs")
 void matchesCpp(String name,String source,String output)throws Exception{agree(temporary,name,source,output);}
 @Test void capturesStillNeedPermission()throws Exception{reject(temporary,"generic-missing-capture","int main(){int x=1;auto f=[](auto n){return n+x; // bad\n};return f(2);}");}
 @Test void conversionCannotChangeFunctionReturnType()throws Exception{reject(temporary,"generic-pointer-return","int main(){long long(*p)(int)=[](auto n){return n;}; // bad\nreturn 0;}");}
}
