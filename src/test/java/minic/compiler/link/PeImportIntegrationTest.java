package minic.compiler.link;

import minic.compiler.SourceFile;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PeImportIntegrationTest {
    @Test
    void importsOnlyReferencedSystemSymbols() {
        String source = """
                #include "stdlib.mh"
                #include "stdio.mh"
                #include "minwindef.mh"
                int main() {
                    int input = -7;
                    void *first = malloc(8);
                    void *second = calloc(2, 4);
                    printf("%d", min(abs(input), 9));
                    free(first);
                    free(second);
                    return 0;
                }
                """;
        CompileObservationSession session = CompileObservationSession.fromSource(
                new SourceFile("pe-imports.mc", source)
        );

        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> "pre=" + session.preprocessor().diagnostics()
                + ", lex=" + session.lexer().diagnostics()
                + ", parse=" + session.parser().diagnostics()
                + ", semantic=" + session.semanticAnalyzer().diagnostics()
                + ", obj=" + session.objBuilder().diagnostics()
                + ", link=" + session.linker().diagnostics());
        Map<String, Set<String>> imports = readImports(session.linker().peImage().orElseThrow().bytes());
        assertEquals(
                Set.of("abs", "calloc", "free", "malloc", "printf"),
                imports.get("msvcrt.dll")
        );
        assertEquals(Set.of("ExitProcess"), imports.get("KERNEL32.dll"));
        assertFalse(imports.values().stream().anyMatch(symbols -> symbols.contains("min")));
    }

    @Test
    void reportsAnExternalSymbolMissingFromTheCatalog() {
        String source = """
                extern int unavailable_system_call();
                int main() { return unavailable_system_call(); }
                """;
        CompileObservationSession session = CompileObservationSession.fromSource(
                new SourceFile("missing-import.mc", source)
        );

        session.compilerApi().run();

        assertFalse(session.linker().succeeded());
        assertTrue(session.linker().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.message().contains("undefined symbol: unavailable_system_call")));
    }

    private static Map<String, Set<String>> readImports(byte[] image) {
        ByteBuffer bytes = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        int pe = bytes.getInt(0x3c);
        int sectionCount = Short.toUnsignedInt(bytes.getShort(pe + 6));
        int optionalHeaderSize = Short.toUnsignedInt(bytes.getShort(pe + 20));
        int optionalHeader = pe + 24;
        int importRva = bytes.getInt(optionalHeader + 112 + 8);
        int sectionTable = optionalHeader + optionalHeaderSize;
        Section[] sections = new Section[sectionCount];
        for (int index = 0; index < sectionCount; index++) {
            int header = sectionTable + index * 40;
            sections[index] = new Section(
                    bytes.getInt(header + 8),
                    bytes.getInt(header + 12),
                    bytes.getInt(header + 16),
                    bytes.getInt(header + 20)
            );
        }

        LinkedHashMap<String, Set<String>> imports = new LinkedHashMap<>();
        int descriptor = fileOffset(importRva, sections);
        while (bytes.getInt(descriptor) != 0 || bytes.getInt(descriptor + 12) != 0) {
            int lookupTableRva = bytes.getInt(descriptor);
            String library = asciiZero(image, fileOffset(bytes.getInt(descriptor + 12), sections));
            LinkedHashSet<String> symbols = new LinkedHashSet<>();
            int thunk = fileOffset(lookupTableRva, sections);
            while (bytes.getLong(thunk) != 0) {
                long nameRva = bytes.getLong(thunk);
                assertTrue((nameRva & Long.MIN_VALUE) == 0, "ordinal imports are not expected");
                symbols.add(asciiZero(image, fileOffset(Math.toIntExact(nameRva), sections) + 2));
                thunk += Long.BYTES;
            }
            imports.put(library, Set.copyOf(symbols));
            descriptor += 20;
        }
        return Map.copyOf(imports);
    }

    private static int fileOffset(int rva, Section[] sections) {
        for (Section section : sections) {
            int length = Math.max(section.virtualSize(), section.rawSize());
            if (rva >= section.virtualAddress() && rva < section.virtualAddress() + length) {
                return section.rawPointer() + rva - section.virtualAddress();
            }
        }
        throw new IllegalArgumentException("RVA is not in a section: " + rva);
    }

    private static String asciiZero(byte[] bytes, int offset) {
        int end = offset;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII);
    }

    private record Section(int virtualSize, int virtualAddress, int rawSize, int rawPointer) {
    }
}
