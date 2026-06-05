package minic.compiler.preprocess;

import minic.source.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCPreprocessorRegressionTest {
    @TempDir
    Path tempDir;

    @Test
    void preprocessesIncludesMacrosConditionalsHeadersAndDiagnostics() throws Exception {
        Path includeRoot = tempDir.resolve("include");
        Files.createDirectories(includeRoot);
        Files.writeString(includeRoot.resolve("defs.mh"), """
                extern int value();
                #define FROM_HEADER 3
                """);
        SourceFile source = new SourceFile("main.mc", """
                #define ENABLED
                #define VALUE 2
                #include "defs.mh"
                #ifdef ENABLED
                int main() { return VALUE + FROM_HEADER; }
                #else
                int main() { return 0; }
                #endif
                """);

        PreprocessResult result = new MiniCPreprocessor().preprocess(source, new PreprocessOptions(List.of(includeRoot)));

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.includes()).hasSize(1);
        assertThat(result.macros()).extracting(MacroSummary::name).contains("ENABLED", "VALUE", "FROM_HEADER");
        assertThat(result.sourceFile().content())
                .contains("extern int value();", "return 2 + 3;")
                .doesNotContain("#define", "#ifdef", "FROM_HEADER");

        List<SourceFile> invalidSources = List.of(
                new SourceFile("missing.mc", "#include \"missing.mh\"\nint main() { return 0; }\n"),
                new SourceFile("suffix.mc", "#include \"bad.h\"\nint main() { return 0; }\n"),
                new SourceFile("condition.mc", "#ifdef\nint main() { return 0; }\n"),
                new SourceFile("else.mc", "#else\nint main() { return 0; }\n")
        );

        for (SourceFile invalidSource : invalidSources) {
            assertThat(new MiniCPreprocessor().preprocess(invalidSource).diagnostics())
                    .as(invalidSource.path())
                    .isNotEmpty();
        }
    }
}
