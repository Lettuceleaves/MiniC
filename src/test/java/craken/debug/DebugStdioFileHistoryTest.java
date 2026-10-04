package craken.debug;

import craken.compiler.SourceFile;
import craken.compiler.ir.instruction.ControlInstruction.TrapKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Timeout(30)
final class DebugStdioFileHistoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void historyNavigationDoesNotRepeatARenameSideEffect() throws Exception {
        Path sourcePath = temporaryDirectory.resolve("before.txt");
        Path targetPath = temporaryDirectory.resolve("after.txt");
        Files.writeString(sourcePath, "content");
        String source = """
                extern int rename(char *old_name, char *new_name);
                int main() {
                    int result = rename("%s", "%s");
                    return result;
                }
                """.formatted(cLiteral(sourcePath), cLiteral(targetPath));
        DebugApi api = new DebugApi(new SourceFile("debug-rename-history.mc", source));

        Debugger.Context beforeRename = advanceToCall(api);
        Debugger.Context afterRename = api.next();
        assertFalse(Files.exists(sourcePath));
        assertTrue(Files.exists(targetPath));

        for (int cycle = 0; cycle < 10; cycle++) {
            assertSame(beforeRename, api.previous());
            assertSame(afterRename, api.next());
            assertFalse(Files.exists(sourcePath));
            assertTrue(Files.exists(targetPath));
        }
    }

    private static Debugger.Context advanceToCall(DebugApi api) {
        for (int remaining = 10_000; api.canNext() && remaining > 0; remaining--) {
            Debugger.Context context = api.next();
            if (context.stop().kind() == TrapKind.CALL) {
                return context;
            }
        }
        return fail("debugger did not reach rename: " + api.current().stop());
    }

    private static String cLiteral(Path path) {
        return path.toAbsolutePath().toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
