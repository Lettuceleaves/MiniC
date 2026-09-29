package minic.compiler.preprocess;

import minic.compiler.SourceFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SystemHeaderPreprocessTest {
    @Test
    void expandsBuiltInHeadersAndFunctionLikeMinMacro() {
        String source = """
                #include "stdlib.mh"
                #include "stdio.mh"
                #include "minwindef.mh"

                int main() {
                    return min(abs(-7), 4);
                }
                """;

        PreprocessResult result = new Preprocessor().preprocess(new SourceFile("system-library.mc", source));

        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertTrue(result.sourceFile().content().contains("extern void *malloc(long size);"));
        assertTrue(result.sourceFile().content().contains("extern int printf(char *format, ...);"));
        assertTrue(result.sourceFile().content().contains("(((abs(-7)) < (4)) ? (abs(-7)) : (4))"));
        assertEquals(3, result.includes().size());
        assertTrue(result.includes().stream().allMatch(PreprocessResult.IncludeSummary::expanded));
    }
}
