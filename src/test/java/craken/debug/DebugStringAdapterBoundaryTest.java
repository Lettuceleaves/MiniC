package craken.debug;

import craken.compiler.SourceFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-debug")
@Timeout(30)
final class DebugStringAdapterBoundaryTest {
    @Test
    void strndupReturnsNullWithoutCopyingWhenItsMallocFails() {
        String source = """
                #include "string.mh"
                int main(void) {
                    char *copy = strndup("abcd", 4);
                    return copy == NULL ? 0 : 1;
                }
                """;
        Debugger debugger = new Debugger(
                new SourceFile("strndup-oom.mc", source),
                "",
                DebugTimeSource.system(),
                4
        );
        DebugApi api = new DebugApi(debugger);

        while (api.canNext()) {
            api.next();
        }

        assertEquals(Debugger.Status.COMPLETED, api.current().stop().status(), api.current().stop().error());
        assertEquals(0, api.current().runtime().returnValue().integer());
        assertEquals(DebugLibrarySupport.ENOMEM, api.current().runtime().errno());
        assertTrue(api.current().runtime().heap().isEmpty());
    }
}
