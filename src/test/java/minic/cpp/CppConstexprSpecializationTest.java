package minic.cpp;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.util.stream.Stream;
@Timeout(120) final class CppConstexprSpecializationTest {
 @TempDir Path temporary;
 static Stream<Arguments> programs(){return Stream.of(
Arguments.of("function-nonliteral-local","struct Value{int n;Value():n(7){}~Value(){}};template<class T>constexpr int read(){T x;return x.n;}int main(){printf(\"%d\\n\",read<Value>());return 0;}","7\n"),
Arguments.of("class-nonliteral-local","struct Value{int n;Value():n(7){}~Value(){}};template<class T>struct Read{constexpr int get()const{T x;return x.n;}};int main(){printf(\"%d\\n\",Read<Value>().get());return 0;}","7\n"),
Arguments.of("function-nonliteral-parameter","struct Value{int n;Value(int x):n(x){}~Value(){}};template<class T>constexpr int read(T x){return x.n;}int main(){printf(\"%d\\n\",read(Value(7)));return 0;}","7\n"),
Arguments.of("function-nonliteral-return","struct Value{int n;Value():n(7){}~Value(){}};template<class T>constexpr T make(){return T();}int main(){Value x=make<Value>();printf(\"%d\\n\",x.n);return 0;}","7\n"),
Arguments.of("constructor-nonliteral-member","struct Value{int n;Value():n(7){}~Value(){}};template<class T>struct Holder{T value;constexpr Holder():value(){}};int main(){Holder<Value>x;printf(\"%d\\n\",x.value.n);return 0;}","7\n"),
Arguments.of("function-uninitialized-scalar","struct Value{constexpr Value(){}};template<class T>constexpr int f(){T x;return 7;}static_assert(f<Value>()==7);int main(){printf(\"%d\\n\",f<int>());return 0;}","7\n"),
Arguments.of("literal-specializations-still-constant","template<class T>constexpr int f(){T x{};return x+7;}template<class T>struct Holder{T x;constexpr Holder():x(7){}constexpr T get()const{return x;}};static_assert(f<int>()==7);static_assert(Holder<int>().get()==7);int main(){return 0;}",""),
Arguments.of("generic-lambda-nonliteral-local","struct Value{int n;Value(int x):n(x){}~Value(){}};int main(){auto f=[](auto x)constexpr{decltype(x) copy=x;return copy.n;};printf(\"%d\\n\",f(Value(7)));return 0;}","7\n"),
Arguments.of("out-of-line-class-member","struct Value{int n;Value():n(7){}~Value(){}};template<class T>struct Read{constexpr int get()const;};template<class T>constexpr int Read<T>::get()const{T x;return x.n;}int main(){printf(\"%d\\n\",Read<Value>().get());return 0;}","7\n"),
Arguments.of("template-missing-member-initialization","template<class T>struct Holder{T value;constexpr Holder(){}};int main(){Holder<int>x;return 0;}",""),
Arguments.of("actual-set-reverse-iterator","#include <set>\nint main(){std::set<int>a;a.insert(7);for(auto i=a.rbegin();i!=a.rend();++i){if(*i!=7)return 1;}return 0;}",""));}
 @ParameterizedTest(name="{0}") @MethodSource("programs") void runtimeSpecializations(String name,String source,String output)throws Exception{CppReferenceTest.agree(temporary,name,"#include <stdio.h>\n"+source,output);}
 static Stream<Arguments> invalid(){return Stream.of(
Arguments.of("constant-call-nonliteral-local","struct Value{int n;Value():n(7){}~Value(){}};template<class T>constexpr int read(){T x;return x.n;}\nstatic_assert(read<Value>()==7); // bad\nint main(){return 0;}"),
Arguments.of("constant-call-uninitialized-local","struct Value{constexpr Value(){}};template<class T>constexpr int read(){T x;return 7;}\nstatic_assert(read<int>()==7); // bad\nint main(){return 0;}"),
Arguments.of("explicit-class-specialization-local","struct Value{int n;Value():n(7){}~Value(){}};template<class T>struct Read{};\ntemplate<>struct Read<int>{constexpr int get(){Value x;return 7;}}; // bad\nint main(){return Read<int>().get();}"),
Arguments.of("constant-template-missing-initialization","template<class T>struct Holder{T value;constexpr Holder(){}};\nconstexpr Holder<int> x; // bad\nint main(){return 0;}"),
Arguments.of("ordinary-constexpr-local","struct Value{int n;Value():n(7){}~Value(){}};\nconstexpr int read(){Value x;return 7;} // bad\nint main(){return read();}"),
Arguments.of("explicit-specialization-local","struct Value{int n;Value():n(7){}~Value(){}};template<class T>constexpr int read(T x){return 7;}\ntemplate<>constexpr int read<int>(int x){Value v;return 7;} // bad\nint main(){return read(1);}"));}
 @ParameterizedTest(name="{0}") @MethodSource("invalid") void retainsConstantExpressionRequirements(String name,String source)throws Exception{CppReferenceTest.reject(temporary,name,source);}
}
