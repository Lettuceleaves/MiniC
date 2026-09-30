package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.compiler.library.SystemLibraryCatalog;
import minic.compiler.preprocess.PreprocessResult;
import minic.compiler.preprocess.Preprocessor;
import minic.testing.CompilerFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InttypesHeaderTest {
    @Test
    @Tag("stdlib-contract")
    void expandsEveryStandardPrintAndScanFormatMacroForWindowsLlp64() {
        LinkedHashMap<String, String> expected = expectedFormatMacros();
        StringBuilder source = new StringBuilder("#include \"inttypes.mh\"\n#include \"inttypes.mh\"\n");
        expected.forEach((name, value) -> source.append("char *fmt_")
                .append(name).append(" = ").append(name).append(";\n"));

        var resultStage = new Preprocessor();
        PreprocessResult result = resultStage.preprocess(
                new SourceFile("inttypes-format-macros.mc", source.toString()));

        assertTrue(resultStage.errors().isEmpty(), resultStage.errors()::toString);
        assertEquals(2, result.includes().stream()
                .filter(include -> include.requestedPath().equals("inttypes.mh"))
                .count());
        String expanded = result.sourceFile().content();
        expected.forEach((name, value) -> assertTrue(
                expanded.contains("fmt_" + name + " = \"" + value + "\";"),
                () -> name + " did not expand to \"" + value + "\":\n" + expanded
        ));
        assertEquals(154, expected.size());
        assertFalse(result.macros().stream().anyMatch(macro -> macro.name().startsWith("SCNX")),
                "ISO C has PRIX macros but no SCNX family");
    }

    @Test
    @Tag("stdlib-contract")
    void aliasesExistingExactWidthFunctionsWithoutPublishingFakeImportsOrImaxdiv() {
        String source = """
                #include "inttypes.mh"
                intmax_t absolute_value(intmax_t value) { return imaxabs(value); }
                intmax_t parse_signed(char *text, char **end) { return strtoimax(text, end, 10); }
                uintmax_t parse_unsigned(char *text, char **end) { return strtoumax(text, end, 10); }
                """;

        var resultStage = new Preprocessor();
        PreprocessResult result = resultStage.preprocess(new SourceFile("inttypes-aliases.mc", source));

        assertTrue(resultStage.errors().isEmpty(), resultStage.errors()::toString);
        String expanded = result.sourceFile().content();
        assertTrue(expanded.contains("return llabs(value);"), expanded);
        assertTrue(expanded.contains("return strtoll(text, end, 10);"), expanded);
        assertTrue(expanded.contains("return strtoull(text, end, 10);"), expanded);

        SystemLibraryCatalog catalog = SystemLibraryCatalog.defaults();
        assertTrue(catalog.binding("imaxabs").isEmpty());
        assertTrue(catalog.binding("strtoimax").isEmpty());
        assertTrue(catalog.binding("strtoumax").isEmpty());
        assertTrue(catalog.binding("imaxdiv").isEmpty());

        String header = catalog.header("inttypes.mh").orElseThrow().content();
        assertTrue(header.contains("struct minic_imaxdiv_result"), header);
        assertTrue(header.contains("typedef struct minic_imaxdiv_result imaxdiv_t;"), header);
        assertTrue(header.contains("imaxdiv is deferred"), header);
        assertFalse(header.matches("(?s).*\\bimaxdiv_t\\s+imaxdiv\\s*\\(.*"), header);
    }

    @Test
    @Tag("stdlib-native")
    void formatsScansAndConvertsExactMaxAndPointerWidthValues() {
        String source = """
                #include "inttypes.mh"
                #include "stdio.mh"

                int main(void) {
                    int8_t signed8 = 0;
                    uint16_t hex16 = 0;
                    int32_t automatic32 = 0;
                    int64_t signed64 = 0;
                    intmax_t maximum = 0;
                    intptr_t pointer_width = 0;
                    int read = scanf(
                        "%" SCNd8 " %" SCNx16 " %" SCNi32 " %" SCNd64 " %" SCNdMAX " %" SCNiPTR,
                        &signed8, &hex16, &automatic32, &signed64, &maximum, &pointer_width
                    );
                    if (read != 6) return 1;

                    char *end = NULL;
                    intmax_t parsed = strtoimax("-5000000000", &end, 10);
                    if (*end != '\0' || imaxabs(parsed) != 5000000000LL) return 2;
                    uintmax_t unsigned_maximum = strtoumax("18446744073709551615", &end, 10);
                    if (*end != '\0' || unsigned_maximum != UINTMAX_MAX) return 3;

                    imaxdiv_t layout;
                    layout.quot = 1;
                    layout.rem = 2;
                    if (layout.quot + layout.rem != 3) return 4;

                    int printed = printf(
                        "%" PRId8 "|%" PRIx16 "|%" PRIi32 "|%" PRId64 "|%" PRIdMAX "|%" PRIdPTR "\\n",
                        signed8, hex16, automatic32, signed64, maximum, pointer_width
                    );
                    return printed > 0 ? 0 : 5;
                }
                """;
        SourceFile sourceFile = new SourceFile("inttypes-native.mc", source);
        CompilerFixture session = CompilerFixture.fromSource(sourceFile);

        session.compilerApi().runThrough(session.linker());

        assertTrue(session.linker().succeeded(), () -> "pre=" + session.preprocessor().errors()
                + ", lex=" + session.lexer().errors()
                + ", parse=" + session.parser().errors()
                + ", semantic=" + session.semanticAnalyzer().errors()
                + ", obj=" + session.objBuilder().errors()
                + ", link=" + session.linker().errors());
        var executionStage = new ExecutableRunner();
        var execution = executionStage.run(
                sourceFile,
                session.linker().result().executableArtifactOptional().orElseThrow(),
                "-7 ff 077 -5000000000 6000000000 -42\n"
        );
        assertTrue(executionStage.errors().isEmpty(), executionStage.errors()::toString);
        assertEquals(0, execution.exitCode(), execution::stderr);
        assertEquals("-7|ff|63|-5000000000|6000000000|-42\r\n", execution.stdout());
    }

    private static LinkedHashMap<String, String> expectedFormatMacros() {
        LinkedHashMap<String, String> expected = new LinkedHashMap<>();
        List<String> printConversions = List.of("d", "i", "o", "u", "x", "X");
        List<String> scanConversions = List.of("d", "i", "o", "u", "x");
        Map<Integer, String> exactPrefixes = Map.of(8, "hh", 16, "h", 32, "", 64, "I64");
        Map<Integer, String> fastPrefixes = Map.of(8, "", 16, "", 32, "", 64, "I64");

        addWidthFamilies(expected, "PRI", printConversions, "", exactPrefixes);
        addWidthFamilies(expected, "PRI", printConversions, "LEAST", exactPrefixes);
        addWidthFamilies(expected, "PRI", printConversions, "FAST", fastPrefixes);
        addMaxAndPointerFamilies(expected, "PRI", printConversions);
        addWidthFamilies(expected, "SCN", scanConversions, "", exactPrefixes);
        addWidthFamilies(expected, "SCN", scanConversions, "LEAST", exactPrefixes);
        addWidthFamilies(expected, "SCN", scanConversions, "FAST", fastPrefixes);
        addMaxAndPointerFamilies(expected, "SCN", scanConversions);
        return expected;
    }

    private static void addWidthFamilies(
            Map<String, String> target,
            String family,
            List<String> conversions,
            String category,
            Map<Integer, String> prefixes
    ) {
        conversions.forEach(conversion -> prefixes.forEach((width, prefix) -> target.put(
                family + conversion + category + width,
                prefix + conversion
        )));
    }

    private static void addMaxAndPointerFamilies(
            Map<String, String> target,
            String family,
            List<String> conversions
    ) {
        conversions.forEach(conversion -> {
            target.put(family + conversion + "MAX", "I64" + conversion);
            target.put(family + conversion + "PTR", "I64" + conversion);
        });
    }
}
