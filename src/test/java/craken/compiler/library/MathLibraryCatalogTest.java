package craken.compiler.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static craken.compiler.library.LibraryBinding.NativeCallingConvention.WINDOWS_X64;
import static craken.compiler.library.LibraryBinding.NativeKind.DLL_IMPORT;
import static craken.compiler.library.LibraryBinding.RuntimeFamily.MSVCRT;
import static craken.compiler.library.LibrarySymbol.SymbolKind.FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-contract")
final class MathLibraryCatalogTest {
    static final Set<String> DOUBLE_FUNCTIONS = Set.of(
            "acos", "asin", "atan", "atan2", "ceil", "cos", "cosh", "exp", "fabs",
            "floor", "fmod", "frexp", "ldexp", "log", "log10", "modf", "pow", "sin",
            "sinh", "sqrt", "tan", "tanh"
    );

    static final Set<String> FLOAT_FUNCTIONS = Set.of(
            "acosf", "asinf", "atanf", "atan2f", "ceilf", "cosf", "coshf", "expf",
            "floorf", "fmodf", "logf", "log10f", "modff", "powf", "sinf", "sinhf",
            "sqrtf", "tanf", "tanhf"
    );

    @Test
    void everySupportedMathFunctionHasADirectMsvcrtBinding() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        LinkedHashSet<String> expected = new LinkedHashSet<>(DOUBLE_FUNCTIONS);
        expected.addAll(FLOAT_FUNCTIONS);

        for (String sourceName : expected) {
            LibraryBinding binding = catalog.binding(sourceName).orElseThrow();
            assertEquals(sourceName, binding.exportName(), sourceName);
            assertEquals("msvcrt.dll", binding.dllName(), sourceName);
            assertEquals(FUNCTION, binding.symbolKind(), sourceName);
            assertEquals(MSVCRT, binding.runtimeFamily(), sourceName);
            assertEquals(WINDOWS_X64, binding.callingConvention(), sourceName);
            assertEquals(DLL_IMPORT, binding.nativeKind(), sourceName);
        }
    }

    @Test
    void mathHeaderDeclaresExactlyTheVerifiedProfile() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        String header = catalog.header("math.mh").orElseThrow().content();
        LinkedHashSet<String> expected = new LinkedHashSet<>(DOUBLE_FUNCTIONS);
        expected.addAll(FLOAT_FUNCTIONS);

        assertEquals(Set.copyOf(expected), declaredFunctions(header));
        assertTrue(header.contains("extern double frexp(double value, int *exponent);"));
        assertTrue(header.contains("extern double modf(double value, double *integerPart);"));
        assertTrue(header.contains("extern float modff(float value, float *integerPart);"));
    }

    @Test
    void nonExportedFloatWrappersRemainDeferred() {
        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        Set<String> deferred = Set.of("fabsf", "frexpf", "ldexpf");
        String header = catalog.header("math.mh").orElseThrow().content();

        for (String name : deferred) {
            assertFalse(declaredFunctions(header).contains(name), name);
            assertFalse(catalog.bindings().containsKey(name), name);
        }
    }

    private static Set<String> declaredFunctions(String header) {
        Pattern functionName = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String line : header.lines().filter(value -> value.startsWith("extern ")).toList()) {
            Matcher matcher = functionName.matcher(line);
            if (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return Set.copyOf(names);
    }
}
