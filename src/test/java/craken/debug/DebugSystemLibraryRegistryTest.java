package craken.debug;

import craken.debug.DebugLibraryCallResult.Returned;
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
                        "malloc", "craken_string_malloc", "calloc", "realloc", "free",
                        "craken_iob_base", "fgetc", "fputc", "ungetc", "fflush",
                        "printf", "scanf", "getchar", "putchar", "puts",
                        "sprintf", "snprintf", "sscanf", "remove", "rename",
                        "clock", "difftime", "time", "mktime", "gmtime", "localtime", "strftime",
                        "setlocale", "localeconv",
                        "abort", "exit", "craken_immediate_exit", "craken_assert_fail",
                        "abs", "labs", "llabs",
                        "atof", "atoi", "atol", "atoll", "strtod", "strtof",
                        "craken_ucrt_strtod", "craken_ucrt_strtof", "craken_ucrt_errno_location",
                        "strtol", "strtoll", "strtoul", "strtoull",
                        "rand", "srand", "craken_errno_location",
                        "acos", "asin", "atan", "atan2", "ceil", "cos", "cosh",
                        "exp", "fabs", "floor", "fmod", "frexp", "ldexp", "log",
                        "log10", "modf", "pow", "sin", "sinh", "sqrt", "tan", "tanh",
                        "acosf", "asinf", "atanf", "atan2f", "ceilf", "cosf", "coshf",
                        "expf", "floorf", "fmodf", "logf", "log10f", "modff", "powf",
                        "sinf", "sinhf", "sqrtf", "tanf", "tanhf",
                        "craken_isctype", "isalnum", "isalpha", "iscntrl", "isdigit", "isgraph",
                        "islower", "isprint", "ispunct", "isspace", "isupper", "isxdigit",
                        "tolower", "toupper",
                        "memcpy", "memmove", "memchr", "memcmp", "memccpy", "memset",
                        "strcpy", "strncpy", "strcat", "strncat", "strcmp", "strncmp",
                        "strcoll", "strchr", "strrchr", "strspn", "strcspn", "strpbrk",
                        "strstr", "strtok", "strerror", "strdup", "strlen", "strxfrm"
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
