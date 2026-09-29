package minic.debug;

import minic.debug.DebugLibraryCallResult.Returned;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-debug")
final class DebugSystemLibraryRegistryTest {
    @Test
    void collectsTheCurrentFunctionsFromModuleProviders() {
        DebugSystemLibrary library = new DebugSystemLibrary();

        assertEquals(
                Set.of(
                        "malloc", "calloc", "realloc", "free",
                        "printf", "scanf", "getchar", "putchar", "puts",
                        "sprintf", "snprintf", "sscanf", "remove", "rename",
                        "clock", "difftime", "time", "mktime", "gmtime", "localtime", "strftime",
                        "abort", "exit", "minic_immediate_exit", "minic_assert_fail",
                        "abs", "labs", "llabs",
                        "atof", "atoi", "atol", "atoll", "strtod", "strtof",
                        "strtol", "strtoll", "strtoul", "strtoull",
                        "rand", "srand", "minic_errno_location",
                        "acos", "asin", "atan", "atan2", "ceil", "cos", "cosh",
                        "exp", "fabs", "floor", "fmod", "frexp", "ldexp", "log",
                        "log10", "modf", "pow", "sin", "sinh", "sqrt", "tan", "tanh",
                        "acosf", "asinf", "atanf", "atan2f", "ceilf", "cosf", "coshf",
                        "expf", "floorf", "fmodf", "logf", "log10f", "modff", "powf",
                        "sinf", "sinhf", "sqrtf", "tanf", "tanhf",
                        "isalnum", "isalpha", "isblank", "iscntrl", "isdigit", "isgraph",
                        "islower", "isprint", "ispunct", "isspace", "isupper", "isxdigit",
                        "tolower", "toupper",
                        "memcpy", "memmove", "memchr", "memcmp", "memset",
                        "strcpy", "strncpy", "strcat", "strncat", "strcmp", "strncmp",
                        "strcoll", "strchr", "strrchr", "strspn", "strcspn", "strpbrk",
                        "strstr", "strtok", "strerror", "strlen", "strxfrm"
                ),
                library.functionNames()
        );
    }

    @Test
    void rejectsDuplicateSymbolsAcrossProviders() {
        DebugLibraryFunction function = (runtime, arguments) -> new Returned(null);
        DebugLibraryProvider first = new TestProvider("first", Map.of("same", function));
        DebugLibraryProvider second = new TestProvider("second", Map.of("same", function));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new DebugSystemLibrary(List.of(first, second))
        );

        assertTrue(error.getMessage().contains("same"), error::getMessage);
        assertTrue(error.getMessage().contains("second"), error::getMessage);
    }

    private record TestProvider(
            String name,
            Map<String, DebugLibraryFunction> functions
    ) implements DebugLibraryProvider {
    }
}
