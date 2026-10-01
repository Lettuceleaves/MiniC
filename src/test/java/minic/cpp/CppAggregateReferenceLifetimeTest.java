package minic.cpp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;

/** C++17 [class.temporary]/6 and [dcl.init.aggr]/3: bind at the member's actual lifetime boundary. */
@Tag("cpp-differential") @Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppAggregateReferenceLifetimeTest {
    @TempDir Path temporary;
    private static final String PREFIX = "#include <stdio.h>\nstruct T{int n;T(int x):n(x){printf(\"C%d \",n);}~T(){printf(\"D%d \",n);}};struct R{const T& value;};";
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("braced-class-referent", "int main(){{R r{{1}};printf(\"M%d \",r.value.n);}printf(\"E\");return 0;}","C1 M1 D1 E"),
        Arguments.of("local", "int main(){{R r{T{1}};printf(\"M%d \",r.value.n);}printf(\"E\");return 0;}","C1 M1 D1 E"),
        Arguments.of("nested", "struct B{R a;R b;};int main(){{B b{{T{1}},{T{2}}};printf(\"M%d%d \",b.a.value.n,b.b.value.n);}printf(\"E\");return 0;}","C1 C2 M12 D2 D1 E"),
        Arguments.of("array", "int main(){{R a[2]={{T{1}},{T{2}}};printf(\"M%d%d \",a[0].value.n,a[1].value.n);}printf(\"E\");return 0;}","C1 C2 M12 D2 D1 E"),
        Arguments.of("construction-result", "int main(){{R r=R{T{1}};printf(\"M%d \",r.value.n);}printf(\"E\");return 0;}","C1 M1 D1 E"),
        Arguments.of("reference-to-aggregate", "int main(){{const R&r=R{T{1}};printf(\"M%d \",r.value.n);}printf(\"E\");return 0;}","C1 M1 D1 E"),
        Arguments.of("copy-does-not-rebind", "int main(){{R a{T{1}};{R b=a;printf(\"B%d \",b.value.n);}printf(\"A%d \",a.value.n);}printf(\"E\");return 0;}","C1 B1 A1 D1 E"),
        Arguments.of("full-expression-argument", "void use(R r){printf(\"U%d \",r.value.n);}int main(){use(R{T{1}});printf(\"E\");return 0;}","C1 U1 D1 E"),
        Arguments.of("returned-reference-not-extended", "R make(){return R{T{1}};}int main(){R r=make();printf(\"E\");return 0;}","C1 D1 E"),
        Arguments.of("static-object", "R global{T{1}};int main(){printf(\"M%d \",global.value.n);return 0;}","C1 M1 D1 "),
        Arguments.of("rvalue-reference-scope", "struct V{T&&value;};int main(){{V r{T{1}};printf(\"M%d \",r.value.n);}printf(\"E\");return 0;}","C1 M1 D1 E"),
        Arguments.of("placement-new-full-expression", "void*operator new(unsigned long long,void*p){return p;}union Storage{long long align;unsigned char bytes[64];};int main(){Storage memory;R*p=new(memory.bytes)R{T{1}};printf(\"E\");return 0;}","C1 D1 E")
    );}
    @Test void structuredBindingOwnsItsHiddenAggregate() throws Exception {
        CppReferenceTest.agree(temporary,"structured-aggregate-lifetime",PREFIX+"int main(){{auto [value]=R{T{1}};printf(\"M%d \",value.n);}printf(\"E\");return 0;}","C1 M1 D1 E");
    }
    @Test void aggregateDestructorPrecedesItsBoundTemporary() throws Exception {
        // CWG2256 fixes the older vacuous-initialization lifetime wording; G++ 8.1 predates that DR.
        // https://cplusplus.github.io/CWG/issues/2256.html and N4659 [class.temporary]/7.
        String source=PREFIX+"struct D{const T& value;~D(){printf(\"R \");}};int main(){{D d{T{1}};printf(\"M%d \",d.value.n);}printf(\"E\");return 0;}";
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run("destructor-order-standard",source,"");
        for(var backend:java.util.List.of(CppDifferentialHarness.Backend.MINIC_NATIVE,CppDifferentialHarness.Backend.MINIC_DEBUG)) {
            var result=report.outcomes().get(backend);assertEquals(CppDifferentialHarness.Status.OK,result.status(),report::describe);
            assertEquals("C1 M1 R D1 E",result.stdout(),report::describe);
        }
        assertEquals(CppDifferentialHarness.Status.OK,report.outcomes().get(CppDifferentialHarness.Backend.GXX).status(),report::describe);
    }
    @Test void constructedValueMemberDestructionOrderAgreesWithReferenceCompiler() throws Exception {
        CppReferenceTest.agree(temporary,"value-member-destruction",PREFIX+"struct D{T value;D(int n):value(n){}~D(){printf(\"R \");}};int main(){{D d{1};printf(\"M%d \",d.value.n);}printf(\"E\");return 0;}","C1 M1 R D1 E");
    }
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void observesMemberLifetime(String name,String source,String expected)throws Exception{
        CppReferenceTest.agree(temporary,name,PREFIX+source,expected);
    }
}
