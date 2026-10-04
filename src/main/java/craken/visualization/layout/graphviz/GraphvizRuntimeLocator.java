package craken.visualization.layout.graphviz;

import java.net.URL;
import java.nio.file.*;

/** Uses a configured development runtime or the distribution's lib/../runtime, independently of launch cwd. */
public final class GraphvizRuntimeLocator {
    private GraphvizRuntimeLocator() {}
    public static Path resolve(Class<?> applicationClass) {
        var source = applicationClass.getProtectionDomain().getCodeSource();
        return resolve(source == null ? null : source.getLocation(), Path.of("").toAbsolutePath(), System.getProperty("craken.graphviz.runtime"));
    }
    public static Path resolve(URL codeSource, Path workingDirectory, String override) {
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        if (codeSource != null) try {
            var source = Path.of(codeSource.toURI()).toAbsolutePath();
            if (source.getFileName().toString().endsWith(".jar") && source.getParent() != null && source.getParent().getParent() != null) {
                var runtime = source.getParent().getParent().resolve("runtime/graphviz");
                if (Files.isRegularFile(runtime.resolve("bin/neato.exe"))) return runtime;
                // A broken distribution must report its missing bundled runtime instead of choosing a development cache.
                return runtime;
            }
        } catch (Exception error) { throw new IllegalArgumentException("Cannot locate application runtime", error); }
        return workingDirectory.toAbsolutePath().normalize().resolve(".local/tools/graphviz/16.1.0/Graphviz-16.1.0-win64");
    }
}
