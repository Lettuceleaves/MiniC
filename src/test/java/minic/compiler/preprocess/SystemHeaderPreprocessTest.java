package minic.compiler.preprocess;

import minic.compiler.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SystemHeaderPreprocessTest {
    @Test
    @Tag("stdlib-contract")
    void mapsAngleBracketCHeaderToProjectLibraryMhFile() {
        String source = "#include<stdio.h>\nint main() { return printf(\"ok\"); }\n";

        var resultStage = new Preprocessor();
        PreprocessResult result = resultStage.preprocess(new SourceFile("angle-header.mc", source));

        assertTrue(resultStage.errors().isEmpty(), () -> resultStage.errors().toString());
        assertTrue(result.sourceFile().content().contains("extern int printf(char *format, ...);"));
        assertEquals(1, result.includes().size());
        PreprocessResult.IncludeSummary include = result.includes().getFirst();
        assertEquals("stdio.h", include.requestedPath());
        assertEquals(Path.of("lib", "stdio.mh").toAbsolutePath().normalize(), include.resolvedPath());
        assertTrue(include.expanded());
    }

    @Test
    @Tag("stdlib-contract")
    void expandsBuiltInHeadersAndFunctionLikeMinMacro() {
        String source = """
                #include "stdlib.mh"
                #include "stdio.mh"
                #include "minwindef.mh"

                int main() {
                    return min(abs(-7), 4);
                }
                """;

        var resultStage = new Preprocessor();
        PreprocessResult result = resultStage.preprocess(new SourceFile("system-library.mc", source));

        assertTrue(resultStage.errors().isEmpty(), () -> resultStage.errors().toString());
        assertTrue(result.sourceFile().content().contains("extern void *malloc(unsigned long long size);"));
        assertTrue(result.sourceFile().content().contains("extern int printf(char *format, ...);"));
        assertTrue(result.sourceFile().content().contains("(((abs(-7)) < (4)) ? (abs(-7)) : (4))"));
        assertEquals(3, result.includes().size());
        assertTrue(result.includes().stream().allMatch(PreprocessResult.IncludeSummary::expanded));
    }

    @Test
    @Tag("stdlib-contract")
    void allowsAFunctionAdapterDefinedByABuiltInHeader() {
        String source = """
                #include "ctype.mh"

                int main() {
                    return isblank(' ') ? 0 : 1;
                }
                """;

        var resultStage = new Preprocessor();
        PreprocessResult result = resultStage.preprocess(new SourceFile("header-adapter.mc", source));

        assertTrue(resultStage.errors().isEmpty(), () -> resultStage.errors().toString());
        assertTrue(result.sourceFile().content().contains("int isblank(int character) {"));
        assertEquals(1, result.includes().size());
        assertTrue(result.includes().getFirst().expanded());
    }

    @Test
    @Tag("stdlib-contract")
    void allowsAHeaderToUseATypedefExpandedByAnEarlierHeader() {
        String source = """
                #include "stddef.mh"
                #include "string.mh"

                int main() {
                    return strlen("MiniC") == 5 ? 0 : 1;
                }
                """;

        var resultStage = new Preprocessor();
        PreprocessResult result = resultStage.preprocess(new SourceFile("dependent-headers.mc", source));

        assertTrue(resultStage.errors().isEmpty(), () -> resultStage.errors().toString());
        assertTrue(result.sourceFile().content().contains("typedef unsigned long long size_t;"));
        assertTrue(result.sourceFile().content().contains("extern size_t strlen(const char *string);"));
    }
}
