package minic.compiler.preprocess;

import minic.compiler.SourceFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LineSplicingPreprocessTest {
    @Test
    void splicesDirectivesInvocationsAndOrdinarySource() {
        String source = """
                #define ADD(a, b) ((a) + \\
                (b))
                #define VALUE ADD(1, \\
                2)
                int macro_value = VALUE;
                int direct_value = 3 + \\
                4;
                """;

        PreprocessResult result = new Preprocessor().preprocess(new SourceFile("continued.mc", source));
        String output = result.sourceFile().content();
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertTrue(output.contains("int macro_value = ((1) + (2));"), output);
        assertTrue(output.contains("int direct_value = 3 + 4;"), output);

        int directOutput = output.indexOf("4;");
        assertEquals(source.indexOf("4;"), result.sourceMap()[directOutput]);
        int expandedOutput = output.indexOf("((1) + (2))");
        assertEquals(source.indexOf("VALUE;"), result.sourceMap()[expandedOutput]);
    }

    @Test
    void splicesWindowsLineEndingsAndNestedMacroArguments() {
        String source = "#define ID(x) x\r\n"
                + "#define WRAP(x) ID(x)\r\n"
                + "int value = WRAP(\"a\" \\\r\n\"b\");\r\n";

        PreprocessResult result = new Preprocessor().preprocess(new SourceFile("windows-lines.mc", source));
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertTrue(result.sourceFile().content().contains("int value = \"a\" \"b\";"), result.sourceFile().content());
    }

    @Test
    void diagnosesDanglingContinuationAtEndOfFile() {
        String source = "#define VALUE 1 \\";
        PreprocessResult result = new Preprocessor().preprocess(new SourceFile("dangling.mc", source));

        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.message().contains("续行缺少下一行")),
                () -> result.diagnostics().toString());
        assertEquals(1, result.diagnostics().getFirst().range().startLine());
        assertEquals(source.length() - 1, result.diagnostics().getFirst().range().startByte());
    }
}
