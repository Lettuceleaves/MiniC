package craken.visualization.layout;

import craken.visualization.layout.graphviz.GraphvizRuntimeLocator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-layout")
final class GraphvizRuntimeLocatorTest {
    @TempDir Path temporary;
    @Test void anExplicitRuntimeAlwaysWins() throws Exception {
        var explicit = temporary.resolve("chosen");
        assertEquals(explicit, GraphvizRuntimeLocator.resolve(temporary.resolve("app/lib/app.jar").toUri().toURL(), temporary.resolve("elsewhere"), explicit.toString()));
    }
    @Test void aPackagedJarResolvesItsRuntimeIndependentlyOfTheLaunchDirectory() throws Exception {
        Path distribution = temporary.resolve("distribution"), nativeRoot = distribution.resolve("runtime/graphviz");
        Files.createDirectories(nativeRoot.resolve("bin")); Files.writeString(nativeRoot.resolve("bin/neato.exe"), "runtime fixture");
        assertEquals(nativeRoot, GraphvizRuntimeLocator.resolve(distribution.resolve("lib/app.jar").toUri().toURL(), temporary.resolve("unrelated-cwd"), null));
    }
    @Test void aMissingPackagedRuntimeCannotFallBackToADevelopmentCache() throws Exception {
        var jar = temporary.resolve("distribution/lib/app.jar");
        assertEquals(temporary.resolve("distribution/runtime/graphviz"), GraphvizRuntimeLocator.resolve(jar.toUri().toURL(), temporary.resolve("elsewhere"), null));
        assertEquals(temporary.resolve("elsewhere/.local/tools/graphviz/16.1.0/Graphviz-16.1.0-win64"),
                GraphvizRuntimeLocator.resolve(temporary.resolve("classes").toUri().toURL(), temporary.resolve("elsewhere"), null));
    }
}
