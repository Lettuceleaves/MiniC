package minic.compiler.ir;

import minic.compiler.SourceFile;
import minic.testing.CompilerFixture;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class IrReachabilityTest {
    @Test
    void removesAnUnreachableLocalFunctionAndItsExternalDependency() {
        IrResult result = lower("""
                extern int abandoned_external(int value);

                int unused_adapter(int value) {
                    return abandoned_external(value);
                }

                int main(void) {
                    return 0;
                }
                """);

        assertEquals(Set.of("main"), functionNames(result));
        assertEquals(Set.of(), result.externalFunctionNames());
    }

    @Test
    void retainsTheDirectCallClosureIncludingRecursion() {
        IrResult result = lower("""
                int countdown(int value) {
                    if (value == 0) return 0;
                    return countdown(value - 1);
                }

                int unused(void) {
                    return 99;
                }

                int main(void) {
                    return countdown(3);
                }
                """);

        assertEquals(Set.of("countdown", "main"), functionNames(result));
        assertEquals(Set.of(), result.externalFunctionNames());
    }

    @Test
    void retainsAddressTakenLocalFunctionsForIndirectCalls() {
        IrResult result = lower("""
                int selected(int value) {
                    return value + 1;
                }

                int unused(int value) {
                    return value + 2;
                }

                int main(void) {
                    int (*operation)(int) = selected;
                    return operation(0) - 1;
                }
                """);

        assertEquals(Set.of("selected", "main"), functionNames(result));
        assertEquals(Set.of(), result.externalFunctionNames());
    }

    @Test
    void retainsAddressTakenExternalFunctionsAsDependencies() {
        IrResult result = lower("""
                extern int external_operation(int value);

                int main(void) {
                    int (*operation)(int) = external_operation;
                    return operation(0);
                }
                """);

        assertEquals(Set.of("main"), functionNames(result));
        assertEquals(Set.of("external_operation"), result.externalFunctionNames());
    }

    @Test
    void retainsFunctionsAndCallClosureReferencedOnlyByStaticRelocations() {
        var result=lower("""
            extern int external_dependency(int);
            int leaf(int n){return external_dependency(n);}
            int selected(int n){return leaf(n);}
            int unused(int n){return n;}
            int (*pointer)(int)=selected;
            int main(void){return 0;}
            """);
        assertEquals(Set.of("selected","leaf","main"),functionNames(result));
        assertEquals(Set.of("external_dependency"),result.externalFunctionNames());
    }

    @Test
    void retainsExternalFunctionsReferencedOnlyByStaticRelocations() {
        var result=lower("""
            extern int external_operation(int);
            extern int unused_operation(int);
            int (*pointer)(int)=external_operation;
            int main(void){return 0;}
            """);
        assertEquals(Set.of("main"),functionNames(result));
        assertEquals(Set.of("external_operation"),result.externalFunctionNames());
    }

    @Test
    void findsFunctionRootsInsideAggregateStaticData() {
        var result=lower("""
            int selected(int n){return n+1;}
            int unused(int n){return n;}
            struct Table{int(*items[2])(int);};
            struct Table table={{0,selected}};
            int main(void){return 0;}
            """);
        assertEquals(Set.of("selected","main"),functionNames(result));
        assertEquals(Set.of(),result.externalFunctionNames());
    }

    private static IrResult lower(String source) {
        CompilerFixture session = CompilerFixture.fromSource(
                new SourceFile("reachability.mc", source)
        );
        return session.compilerApi().runToIr();
    }

    private static Set<String> functionNames(IrResult result) {
        return result.functions().stream().map(function -> function.name()).collect(java.util.stream.Collectors.toSet());
    }
}
