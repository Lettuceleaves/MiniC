package minic.compiler.ir;

import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
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

    private static IrResult lower(String source) {
        CompilerApi session = new CompilerApi(
                new SourceFile("reachability.mc", source)
        );
        return session.runToIr();
    }

    private static Set<String> functionNames(IrResult result) {
        return result.functions().stream().map(function -> function.name()).collect(java.util.stream.Collectors.toSet());
    }
}
