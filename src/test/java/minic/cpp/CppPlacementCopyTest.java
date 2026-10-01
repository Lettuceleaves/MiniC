package minic.cpp;

import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Placement construction uses the selected copy constructor in final raw storage. */
@Tag("cpp-differential") @Timeout(90)
final class CppPlacementCopyTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs() { return Stream.of(
        Arguments.of("explicit-copy", "int copies;struct T{T*self;int n;T(int x):self(this),n(x){} explicit T(const T&x):self(this),n(x.n){++copies;}~T(){printf(\"D%d \",n);}};int main(){{T source(7);void*s=malloc(sizeof(T));T*p=new(s)T(source);printf(\"%d %d %d \",p->self==p,p->n,copies);p->~T();free(s);}return 0;}", "1 7 1 D7 D7 "),
        Arguments.of("implicit-member-copy", "int copies;struct T{T*self;T():self(this){} T(const T&x):self(this){++copies;}};struct O{T item;};int main(){O source;void*s=malloc(sizeof(O));O*p=new(s)O(source);printf(\"%d %d %d \",source.item.self==&source.item,p->item.self==&p->item,copies);p->~O();free(s);return 0;}", "1 1 1 "),
        Arguments.of("prvalue-direct-construction", "int copies;struct T{T*self;T():self(this){} T(const T&x):self(this){++copies;}~T(){printf(\"D \");}};T make(){return T();}int main(){void*s=malloc(sizeof(T));T*p=new(s)T(make());printf(\"%d %d \",p->self==p,copies);p->~T();free(s);return 0;}", "1 0 D ")
    ); }
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void preservesCopySemanticsAndFinalAddress(String name,String body,String expected) throws Exception {
        String source="#include <stdio.h>\n#include <stdlib.h>\nvoid* operator new(unsigned long long,void*p){return p;}"+body;
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,source,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,outcome.stdout()));
    }
}
