package craken.visualization.acceptance;

import craken.ui.component.visualization.UiVisualizationContainer;
import craken.visualization.layout.CancellationToken;
import craken.visualization.layout.graphviz.GraphvizProcessBridge;
import craken.visualization.layout.graphviz.GraphvizRuntimeLocator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-layout")
class GraphvizDistributionAcceptanceTest {
    @TempDir Path arbitraryWorkingDirectory;

    @Test void installedApplicationJarFindsItsBundledRuntimeFromAnUnrelatedDirectoryWithoutPath() throws Exception {
        Path distribution=Path.of("build/install/Craken").toAbsolutePath();
        assertTrue(Files.isRegularFile(distribution.resolve("runtime/graphviz/bin/neato.exe")),"Run installDist before release acceptance");
        Path tests=Path.of(GraphvizDistributionAcceptanceTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String classpath=distribution.resolve("lib")+File.separator+"*"+File.pathSeparator+tests;
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),
                "-cp",classpath,DistributionProbe.class.getName(),distribution.toString())
                .directory(arbitraryWorkingDirectory.toFile()).redirectErrorStream(true);
        builder.environment().put("PATH",Path.of(System.getenv("SystemRoot"),"System32").toString());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        var process=builder.start();
        try {
            assertTrue(process.waitFor(20,TimeUnit.SECONDS),"Distribution probe timed out");
            String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0,process.exitValue(),output);
            assertTrue(output.contains("distribution-ok Graphviz 16.1.0"),output);
        } finally { if(process.isAlive())process.destroyForcibly(); }
    }

    /** Runs with production classes exclusively from the installed application's lib directory. */
    public static final class DistributionProbe {
        public static void main(String[] args) throws Exception {
            Path distribution=Path.of(args[0]);
            Path actual=GraphvizRuntimeLocator.resolve(UiVisualizationContainer.class);
            if(!actual.equals(distribution.resolve("runtime/graphviz")))throw new IllegalStateException("Unexpected runtime: "+actual);
            String source=UiVisualizationContainer.class.getProtectionDomain().getCodeSource().getLocation().toString();
            if(!source.endsWith(".jar"))throw new IllegalStateException("Probe loaded development classes: "+source);
            try(var bridge=new GraphvizProcessBridge(actual,Duration.ofSeconds(10))) {
                if(!bridge.version().equals("16.1.0"))throw new IllegalStateException("Wrong version");
                var result=bridge.execute("graph {a--b}",CancellationToken.NONE);
                if(!result.stdout().contains("edge a b"))throw new IllegalStateException("No real layout output");
            }
            System.out.println("distribution-ok Graphviz 16.1.0");
        }
    }
}
