package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.preprocess.Preprocessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-contract")
final class StandardLibraryInventoryTest {
    private static final String HEADER_RESOURCE = "/minic/stdlib/c23-headers.properties";
    private static final String ENTITY_RESOURCE = "/minic/stdlib/c23-entities.properties";

    private static final Set<String> C23_HEADERS = Set.of(
            "assert.mh", "complex.mh", "ctype.mh", "errno.mh", "fenv.mh", "float.mh",
            "inttypes.mh", "iso646.mh", "limits.mh", "locale.mh", "math.mh", "setjmp.mh",
            "signal.mh", "stdalign.mh", "stdarg.mh", "stdatomic.mh", "stdbit.mh", "stdbool.mh",
            "stdckdint.mh", "stddef.mh", "stdint.mh", "stdio.mh", "stdlib.mh", "stdnoreturn.mh",
            "string.mh", "tgmath.mh", "threads.mh", "time.mh", "uchar.mh", "wchar.mh", "wctype.mh"
    );

    @Test
    void enumeratesTheExactHostedC23HeaderSet() throws IOException {
        Map<String, String> headers = readLedger(HEADER_RESOURCE);

        assertEquals(31, headers.size());
        assertEquals(C23_HEADERS, headers.keySet());
        headers.forEach((header, value) -> {
            String[] fields = value.split("\\|", -1);
            assertEquals(3, fields.length, header);
            assertTrue(Set.of("IMPLEMENTED", "PARTIAL", "DEFERRED").contains(fields[0]), header);
            assertTrue(fields[1].matches("C(90|95|99|11|23)"), header);
            assertFalse(fields[2].isBlank(), header);
        });
    }

    @Test
    void publishedHeadersAreRealResourcesAndNeverEmptyPlaceholders() throws IOException {
        Map<String, String> headers = readLedger(HEADER_RESOURCE);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String status = entry.getValue().substring(0, entry.getValue().indexOf('|'));
            if (status.equals("DEFERRED")) {
                continue;
            }
            String resourceName = "/minic/include/" + entry.getKey();
            try (InputStream input = StandardLibraryInventoryTest.class.getResourceAsStream(resourceName)) {
                assertNotNull(input, resourceName);
                String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(content.isBlank(), resourceName);
                assertTrue(content.contains("#ifndef"), resourceName + " must have an include guard");
            }
        }
    }

    @Test
    void everyPublishedHeaderCanBeIncludedTwiceAndAllCanBeCombined() throws IOException {
        Map<String, String> headers = readLedger(HEADER_RESOURCE);
        List<String> published = headers.entrySet().stream()
                .filter(entry -> !entry.getValue().startsWith("DEFERRED|"))
                .map(Map.Entry::getKey)
                .toList();

        for (String header : published) {
            String source = "#include \"" + header + "\"\n#include \"" + header + "\"\nint main() { return 0; }\n";
            var result = new Preprocessor().preprocess(new SourceFile("repeat-" + header + ".mc", source));
            assertTrue(result.diagnostics().isEmpty(), () -> header + ": " + result.diagnostics());
            assertEquals(2, result.includes().stream()
                    .filter(include -> include.requestedPath().equals(header))
                    .count(), header);
            assertTrue(result.includes().stream().allMatch(include -> include.expanded()), header);
        }

        StringBuilder combined = new StringBuilder();
        published.forEach(header -> combined.append("#include \"").append(header).append("\"\n"));
        combined.append("int main() { return 0; }\n");
        var result = new Preprocessor().preprocess(new SourceFile("all-standard-headers.mc", combined.toString()));
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertTrue(result.includes().size() >= published.size());
        published.forEach(header -> assertTrue(
                result.includes().stream().anyMatch(include -> include.requestedPath().equals(header)),
                header
        ));
    }

    @Test
    void everyPublishedEntityHasProvidersEffectsAndTestIds() throws IOException {
        Map<String, String> headers = readLedger(HEADER_RESOURCE);
        Map<String, String> entities = readLedger(ENTITY_RESOURCE);
        Set<String> entityHeaders = new LinkedHashSet<>();

        entities.forEach((qualifiedName, value) -> {
            int dot = qualifiedName.indexOf('.');
            assertTrue(dot > 0 && dot < qualifiedName.length() - 1, qualifiedName);
            String header = qualifiedName.substring(0, dot) + ".mh";
            entityHeaders.add(header);
            assertTrue(headers.containsKey(header), qualifiedName);

            String[] fields = value.split("\\|", -1);
            assertEquals(6, fields.length, qualifiedName);
            assertFalse(fields[0].isBlank(), qualifiedName + " kind");
            assertFalse(fields[1].isBlank(), qualifiedName + " status");
            assertFalse(fields[2].isBlank(), qualifiedName + " native provider");
            assertFalse(fields[3].isBlank(), qualifiedName + " debug provider");
            assertFalse(fields[4].isBlank(), qualifiedName + " effects");
            List<String> testIds = Arrays.stream(fields[5].split(","))
                    .map(String::strip)
                    .filter(testId -> !testId.isEmpty())
                    .toList();
            assertTrue(testIds.size() >= 3, qualifiedName + " test ids");
        });

        headers.forEach((header, value) -> {
            String status = value.substring(0, value.indexOf('|'));
            if (!status.equals("DEFERRED")) {
                assertTrue(entityHeaders.contains(header), header + " has no entity ledger");
            }
        });
    }

    private static Map<String, String> readLedger(String resourceName) throws IOException {
        InputStream input = StandardLibraryInventoryTest.class.getResourceAsStream(resourceName);
        assertNotNull(input, resourceName);
        try (input) {
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            for (String rawLine : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equals = line.indexOf('=');
                assertTrue(equals > 0 && equals < line.length() - 1, line);
                String previous = result.putIfAbsent(line.substring(0, equals).strip(), line.substring(equals + 1).strip());
                assertEquals(null, previous, line);
            }
            return result;
        }
    }
}
