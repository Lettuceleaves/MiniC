package minic.compiler.link;

import minic.compiler.CompilerApi;
import minic.compiler.lexer.Lexer;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.library.LibraryBinding;
import minic.compiler.library.LibrarySymbol;
import minic.compiler.link.pe.PeImage;
import minic.compiler.link.pe.WindowsPeLinker;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        CompilerApi session = new CompilerApi(
                new SourceFile("pe-imports.mc", source)
        );

        session.runThrough(session.stage(Linker.class));

        assertTrue(session.stage(Linker.class).succeeded(), () -> "pre=" + session.stage(Preprocessor.class).errors()
                + ", lex=" + session.stage(Lexer.class).errors()
                + ", parse=" + session.stage(Parser.class).errors()
                + ", semantic=" + session.stage(SemanticAnalyzer.class).errors()
                + ", obj=" + session.stage(ObjBuilder.class).errors()
                + ", link=" + session.stage(Linker.class).errors());
        Map<String, Set<String>> imports = readImports(session.stage(Linker.class).peImage().orElseThrow().bytes());
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
        CompilerApi session = new CompilerApi(
                new SourceFile("missing-import.mc", source)
        );

        session.runThrough(session.stage(Linker.class));

        assertFalse(session.stage(Linker.class).succeeded());
        assertTrue(session.stage(Linker.class).errors().stream()
                .anyMatch(diagnostic -> diagnostic.message().contains("undefined symbol: unavailable_system_call")));
    }

    @Test
    void importsTheNativeExportNameWhileRelocationsKeepTheSourceName() {
        CompilerApi session = compiledMinimalProgram();
        LibraryBinding aliasedExit = binding(
                "ExitProcess",
                "KERNEL32.dll",
                "NativeExitAlias",
                LibrarySymbol.SymbolKind.FUNCTION,
                LibraryBinding.NativeKind.DLL_IMPORT
        );
        LibraryBinding unused = binding(
                "unused_source_symbol",
                "example.dll",
                "UnusedNativeExport",
                LibrarySymbol.SymbolKind.FUNCTION,
                LibraryBinding.NativeKind.DLL_IMPORT
        );

        PeImage image = new WindowsPeLinker().link(
                session.stage(ObjBuilder.class).result().objectFile(),
                session.stage(ObjBuilder.class).result().entrySymbol(),
                Map.of(aliasedExit.sourceName(), aliasedExit, unused.sourceName(), unused)
        );

        Map<String, Set<String>> imports = readImports(image.bytes());
        assertEquals(Map.of("KERNEL32.dll", Set.of("NativeExitAlias")), imports);
        assertFalse(imports.containsKey("example.dll"), "unused bindings must not create an import descriptor");
    }

    @Test
    void rejectsReferencedBindingsThatThePeLinkerCannotImplement() {
        CompilerApi session = compiledMinimalProgram();
        LibraryBinding dataBinding = binding(
                "ExitProcess",
                "KERNEL32.dll",
                "ExitProcess",
                LibrarySymbol.SymbolKind.DATA,
                LibraryBinding.NativeKind.DLL_IMPORT
        );

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> new WindowsPeLinker().link(
                        session.stage(ObjBuilder.class).result().objectFile(),
                        session.stage(ObjBuilder.class).result().entrySymbol(),
                        Map.of(dataBinding.sourceName(), dataBinding)
                )
        );
        assertTrue(exception.getMessage().contains("unsupported imported symbol kind"));

        LibraryBinding staticWrapper = binding(
                "ExitProcess",
                "KERNEL32.dll",
                "ExitProcess",
                LibrarySymbol.SymbolKind.FUNCTION,
                LibraryBinding.NativeKind.STATIC_WRAPPER
        );
        UnsupportedOperationException nativeKindException = assertThrows(
                UnsupportedOperationException.class,
                () -> new WindowsPeLinker().link(
                        session.stage(ObjBuilder.class).result().objectFile(),
                        session.stage(ObjBuilder.class).result().entrySymbol(),
                        Map.of(staticWrapper.sourceName(), staticWrapper)
                )
        );
        assertTrue(nativeKindException.getMessage().contains("unsupported native binding kind"));

        LibraryBinding mismatched = binding(
                "different_source_name",
                "KERNEL32.dll",
                "ExitProcess",
                LibrarySymbol.SymbolKind.FUNCTION,
                LibraryBinding.NativeKind.DLL_IMPORT
        );
        IllegalArgumentException mismatchException = assertThrows(
                IllegalArgumentException.class,
                () -> new WindowsPeLinker().link(
                        session.stage(ObjBuilder.class).result().objectFile(),
                        session.stage(ObjBuilder.class).result().entrySymbol(),
                        Map.of("ExitProcess", mismatched)
                )
        );
        assertTrue(mismatchException.getMessage().contains("does not match source symbol"));
    }

    private static CompilerApi compiledMinimalProgram() {
        CompilerApi session = new CompilerApi(
                new SourceFile("minimal-import.mc", "int main() { return 0; }")
        );
        session.runThrough(session.stage(Linker.class));
        assertTrue(session.stage(ObjBuilder.class).succeeded(), () -> session.stage(ObjBuilder.class).errors().toString());
        return session;
    }

    private static LibraryBinding binding(
            String sourceName,
            String dllName,
            String exportName,
            LibrarySymbol.SymbolKind symbolKind,
            LibraryBinding.NativeKind nativeKind
    ) {
        return new LibraryBinding(
                sourceName,
                dllName,
                exportName,
                symbolKind,
                LibraryBinding.RuntimeFamily.WINDOWS,
                LibraryBinding.NativeCallingConvention.WINDOWS_X64,
                nativeKind
        );
    }

    static Map<String, Set<String>> readImports(byte[] image) {
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
