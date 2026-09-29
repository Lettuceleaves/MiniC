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
                        "malloc", "calloc", "free", "printf", "scanf", "abs",
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
