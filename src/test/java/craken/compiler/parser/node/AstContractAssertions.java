package craken.compiler.parser.node;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

/** Public contracts captured from the original compiled records, before any migration. */
final class AstContractAssertions {
    private AstContractAssertions() {}

    static void assertMigrated(Predicate<Class<?>> selection, int expectedCount) throws Exception {
        var stream = AstContractAssertions.class.getResourceAsStream("/craken/ast-record-contracts.tsv");
        assertNotNull(stream, "original record contract fixture");
        List<String> lines;
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        }
        int count = 0;
        for (var line : lines) {
            var parts = line.split("\\t", -1);
            var type = Class.forName(parts[0]);
            if (!selection.test(type)) continue;
            count++;
            assertFalse(type.isRecord(), type.getName() + " must carry its own mutable visual slots");
            assertTrue(Modifier.isFinal(type.getModifiers()), type.getName());
            assertEquals("craken.compiler.parser.node.AbstractAstNode", type.getSuperclass().getName());
            if (type.getEnclosingClass() != null) assertTrue(Modifier.isStatic(type.getModifiers()), type.getName());
            for (var fieldSpec : parts[1].split(";")) {
                var pair = fieldSpec.split("=", 2);
                var field = type.getDeclaredField(pair[0]);
                assertTrue(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), field.toString());
                assertEquals(pair[1], field.getType().getName());
                assertEquals(field.getType(), type.getMethod(pair[0]).getReturnType());
            }
            var actual = Arrays.stream(type.getConstructors())
                    .map(c -> Arrays.stream(c.getParameterTypes()).map(Class::getName).collect(java.util.stream.Collectors.joining(",")))
                    .sorted().toList();
            assertEquals(Arrays.asList(parts[2].split(";", -1)), actual, type.getName() + " constructor compatibility");
        }
        assertEquals(expectedCount, count, "nonzero and complete migration coverage");
    }
}
