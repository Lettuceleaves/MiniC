package craken.visualization.layout;

import craken.visualization.layout.graphviz.GraphvizProcessBridge;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-layout")
final class GraphvizPackagingTest {
    @TempDir Path temporary;
    private final Path project = Path.of("").toAbsolutePath();
    private record ScriptResult(int exit, String output) {}
    private ScriptResult stage(Path root, String name) throws Exception {
        Path shell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        var process = new ProcessBuilder(shell.toString(), "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                project.resolve("scripts/stage-graphviz-runtime.ps1").toString(), "-ProjectRoot", root.toString(),
                "-StageName", name).redirectErrorStream(true).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Runtime staging timed out"); }
        return new ScriptResult(process.exitValue(), new String(process.getInputStream().readAllBytes()));
    }
    @Test void stagedRuntimeContainsAllPluginsAndRunsWithNoGlobalGraphvizOnPath() throws Exception {
        var result = stage(project, "test"); assertEquals(0, result.exit(), result.output());
        Path staged = project.resolve("build/graphviz-staging/test/graphviz");
        assertTrue(Files.isRegularFile(staged.resolve("licenses/Graphviz-EPL-2.0.txt")));
        assertTrue(Files.isRegularFile(staged.resolve("SOURCE.txt")));
        assertTrue(Files.isRegularFile(staged.resolve("bin/config8")));
        Path source = Path.of(System.getProperty("craken.graphviz.runtime"));
        try (var files = Files.walk(source)) {
            for (var file : files.filter(Files::isRegularFile).toList())
                assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(staged.resolve(source.relativize(file))));
        }
        var process = new ProcessBuilder(staged.resolve("bin/neato.exe").toString(), "-Tplain-ext")
                .directory(staged.toFile()).redirectErrorStream(true);
        process.environment().put("PATH", Path.of(System.getenv("SystemRoot"), "System32").toString());
        process.environment().put("GVBINDIR", staged.resolve("bin").toString());
        var child = process.start();
        child.getOutputStream().write("graph {a--b}".getBytes()); child.getOutputStream().close();
        assertTrue(child.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, child.exitValue());
        assertTrue(new String(child.getInputStream().readAllBytes()).contains("edge a b"));
        try (var bridge = new GraphvizProcessBridge(staged, Duration.ofSeconds(10))) { assertEquals("16.1.0", bridge.version()); }
    }
    @Test void missingLockedRuntimeFailsBeforeCreatingAReleaseStage() throws Exception {
        Files.createDirectories(temporary.resolve("config/visualization/graphviz"));
        Files.copy(project.resolve("config/visualization/graphviz-runtime.json"), temporary.resolve("config/visualization/graphviz-runtime.json"));
        Files.copy(project.resolve("config/visualization/graphviz/LICENSE"), temporary.resolve("config/visualization/graphviz/LICENSE"));
        var result = stage(temporary, "missing"); assertNotEquals(0, result.exit());
        assertTrue(result.output().contains("Missing locked Graphviz artifact"), result.output());
        assertFalse(Files.exists(temporary.resolve("build/graphviz-staging/missing/graphviz")));
    }
}
