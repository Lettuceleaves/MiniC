package minic.compiler.link.pe;

import minic.compiler.library.LibraryBinding;
import minic.compiler.library.LibrarySymbol;
import minic.compiler.obj.coff.CoffObjectFile;
import minic.compiler.obj.coff.CoffObjectReader;
import minic.compiler.obj.coff.CoffObjectReader.CoffRelocation;
import minic.compiler.obj.coff.CoffObjectReader.CoffSection;
import minic.compiler.obj.coff.CoffObjectReader.CoffSymbol;
import minic.compiler.obj.coff.CoffObjectReader.ParsedCoffObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MiniC 自带的 AMD64 COFF 到 PE32+ 链接器。
 */
public final class WindowsPeLinker {
    private static final int IMAGE_FILE_MACHINE_AMD64 = 0x8664;
    private static final int IMAGE_REL_AMD64_ADDR64 = 0x0001;
    private static final int IMAGE_REL_AMD64_REL32 = 0x0004;
    private static final int PE_OFFSET = 0x80;
    private static final int OPTIONAL_HEADER_SIZE = 240;
    private static final int SECTION_ALIGNMENT = 0x1000;
    private static final int FILE_ALIGNMENT = 0x200;
    private static final long IMAGE_BASE = 0x0000000140000000L;
    private static final int SUBSYSTEM_WINDOWS_CUI = 3;
    private static final int DLL_CHARACTERISTICS_NX_COMPAT = 0x0100;
    private static final int DLL_CHARACTERISTICS_TERMINAL_SERVER_AWARE = 0x8000;

    private final CoffObjectReader reader;

    public WindowsPeLinker() {
        this(new CoffObjectReader());
    }

    public WindowsPeLinker(CoffObjectReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    /**
     * 链接一个由 MiniC 生成的 COFF 对象。当前阶段要求所有实际引用的符号均在对象内定义。
     */
    public PeImage link(CoffObjectFile objectFile, String entrySymbol) {
        return link(objectFile, entrySymbol, Map.of());
    }

    /**
     * 链接 COFF 对象并直接构建指定 DLL imports，不需要 import library。
     *
     * @param bindings MiniC 源符号到 Windows 原生导出的绑定
     */
    public PeImage link(
            CoffObjectFile objectFile,
            String entrySymbol,
            Map<String, LibraryBinding> bindings
    ) {
        Objects.requireNonNull(objectFile, "objectFile");
        Objects.requireNonNull(entrySymbol, "entrySymbol");
        Objects.requireNonNull(bindings, "bindings");
        ParsedCoffObject object = reader.read(objectFile);
        if (object.sections().isEmpty()) {
            throw new IllegalArgumentException("COFF object has no sections");
        }

        // COFF 符号表可以保留未使用的 extern 声明；只有重定位真正引用的符号才需要解析。
        List<String> importedSymbols = object.sections().stream()
                .flatMap(section -> section.relocations().stream())
                .map(relocation -> object.symbols().get(relocation.symbolIndex()))
                .filter(symbol -> !symbol.defined())
                .map(CoffSymbol::name)
                .distinct()
                .sorted()
                .toList();
        for (String symbol : importedSymbols) {
            LibraryBinding binding = bindings.get(symbol);
            if (binding == null) {
                throw new IllegalArgumentException("undefined symbol: " + symbol);
            }
            validateImportBinding(symbol, binding);
        }
        ImportPlan importPlan = importedSymbols.isEmpty() ? null : ImportPlan.plan(importedSymbols, bindings);
        int outputSectionCount = object.sections().size() + (importPlan == null ? 0 : 1);
        int headersSize = align(
                PE_OFFSET + 4 + 20 + OPTIONAL_HEADER_SIZE + outputSectionCount * 40,
                FILE_ALIGNMENT
        );
        ArrayList<LinkedSection> sections = new ArrayList<>();
        // PE sections must occupy consecutive ranges rounded to the one image-wide alignment.
        // Merely inserting RVA gaps for an over-aligned COFF section is rejected by Windows.
        int sectionAlignment = SECTION_ALIGNMENT;
        for (CoffSection section : object.sections()) {
            int encoded = (section.characteristics() >>> 20) & 15;
            if (encoded > 0) sectionAlignment = Math.max(sectionAlignment, 1 << (encoded - 1));
        }
        int nextRva = sectionAlignment;
        int nextRaw = headersSize;
        for (int index = 0; index < object.sections().size(); index++) {
            CoffSection section = object.sections().get(index);
            byte[] data = section.data();
            if (importPlan != null && ".text".equals(section.name())) {
                int thunkStart = align(data.length, 16);
                data = Arrays.copyOf(data, thunkStart + importedSymbols.size() * 6);
                importPlan.thunkSectionNumber = index + 1;
                importPlan.thunkStart = thunkStart;
            }
            LinkedSection linked = new LinkedSection(
                    index + 1,
                    section.name(),
                    data,
                    nextRva,
                    nextRaw,
                    align(data.length, FILE_ALIGNMENT),
                    section.characteristics()
            );
            sections.add(linked);
            nextRva = align(nextRva + Math.max(data.length, 1), sectionAlignment);
            nextRaw += linked.rawSize;
        }

        if (importPlan != null) {
            if (importPlan.thunkSectionNumber == 0) {
                throw new IllegalArgumentException("imports require a .text section");
            }
            LinkedSection idata = new LinkedSection(
                    sections.size() + 1,
                    ".idata",
                    new byte[importPlan.size],
                    nextRva,
                    nextRaw,
                    align(importPlan.size, FILE_ALIGNMENT),
                    0xC0000040
            );
            sections.add(idata);
            nextRva = align(nextRva + Math.max(importPlan.size, 1), sectionAlignment);
            nextRaw += idata.rawSize;
            byte[] importData = importPlan.build(idata.rva);
            System.arraycopy(importData, 0, idata.data, 0, importData.length);
        }

        LinkedHashMap<String, ResolvedSymbol> symbols = resolveSymbols(object.symbols(), sections);
        ImportDirectory importDirectory = ImportDirectory.empty();
        if (importPlan != null) {
            LinkedSection text = sections.get(importPlan.thunkSectionNumber - 1);
            LinkedSection idata = sections.getLast();
            for (int index = 0; index < importedSymbols.size(); index++) {
                String symbol = importedSymbols.get(index);
                int thunkOffset = importPlan.thunkStart + index * 6;
                int thunkRva = text.rva + thunkOffset;
                int iatRva = idata.rva + importPlan.iatOffsets.get(symbol);
                text.data[thunkOffset] = (byte) 0xFF;
                text.data[thunkOffset + 1] = 0x25;
                putInt(text.data, thunkOffset + 2, iatRva - (thunkRva + 6));
                symbols.put(symbol, new ResolvedSymbol(thunkRva));
            }
            importDirectory = new ImportDirectory(
                    idata.rva,
                    importPlan.descriptorSize,
                    idata.rva + importPlan.firstIatOffset,
                    importPlan.totalIatSize
            );
        }
        ResolvedSymbol entry = symbols.get(entrySymbol);
        if (entry == null) {
            throw new IllegalArgumentException("entry symbol is not defined: " + entrySymbol);
        }
        applyRelocations(object, sections, symbols);

        byte[] image = new byte[nextRaw];
        ByteBuffer output = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        writeDosHeader(output);
        output.position(PE_OFFSET);
        output.putInt(0x00004550);
        writeCoffHeader(output, sections.size());
        writeOptionalHeader(output, sections, entry.rva, nextRva, headersSize, importDirectory, sectionAlignment);
        for (LinkedSection section : sections) {
            writeSectionHeader(output, section);
        }
        for (LinkedSection section : sections) {
            System.arraycopy(section.data, 0, image, section.rawPointer, section.data.length);
        }
        return new PeImage(image);
    }

    private static void validateImportBinding(String sourceName, LibraryBinding binding) {
        if (!sourceName.equals(binding.sourceName())) {
            throw new IllegalArgumentException("library binding key does not match source symbol: " + sourceName);
        }
        if (binding.symbolKind() != LibrarySymbol.SymbolKind.FUNCTION) {
            throw new UnsupportedOperationException(
                    "unsupported imported symbol kind for " + sourceName + ": " + binding.symbolKind()
            );
        }
        if (binding.nativeKind() != LibraryBinding.NativeKind.DLL_IMPORT) {
            throw new UnsupportedOperationException(
                    "unsupported native binding kind for " + sourceName + ": " + binding.nativeKind()
            );
        }
        if (binding.callingConvention() != LibraryBinding.NativeCallingConvention.WINDOWS_X64) {
            throw new UnsupportedOperationException(
                    "unsupported calling convention for " + sourceName + ": " + binding.callingConvention()
            );
        }
    }

    private static LinkedHashMap<String, ResolvedSymbol> resolveSymbols(
            List<CoffSymbol> sourceSymbols,
            List<LinkedSection> sections
    ) {
        LinkedHashMap<String, ResolvedSymbol> symbols = new LinkedHashMap<>();
        for (CoffSymbol symbol : sourceSymbols) {
            if (!symbol.defined()) {
                continue;
            }
            if (symbol.sectionNumber() > sections.size()) {
                throw new IllegalArgumentException("symbol has invalid section: " + symbol.name());
            }
            LinkedSection section = sections.get(symbol.sectionNumber() - 1);
            if (symbol.value() < 0 || symbol.value() > section.data.length) {
                throw new IllegalArgumentException("symbol is outside its section: " + symbol.name());
            }
            ResolvedSymbol previous = symbols.putIfAbsent(
                    symbol.name(),
                    new ResolvedSymbol(section.rva + symbol.value())
            );
            if (previous != null) {
                throw new IllegalArgumentException("duplicate symbol: " + symbol.name());
            }
        }
        return symbols;
    }

    private static void applyRelocations(
            ParsedCoffObject object,
            List<LinkedSection> sections,
            Map<String, ResolvedSymbol> symbols
    ) {
        for (int sectionIndex = 0; sectionIndex < object.sections().size(); sectionIndex++) {
            CoffSection source = object.sections().get(sectionIndex);
            LinkedSection targetSection = sections.get(sectionIndex);
            ByteBuffer data = ByteBuffer.wrap(targetSection.data).order(ByteOrder.LITTLE_ENDIAN);
            for (CoffRelocation relocation : source.relocations()) {
                CoffSymbol referenced = object.symbols().get(relocation.symbolIndex());
                ResolvedSymbol target = symbols.get(referenced.name());
                if (target == null) {
                    throw new IllegalArgumentException("undefined symbol: " + referenced.name());
                }
                if (relocation.type() == IMAGE_REL_AMD64_REL32) {
                    requirePatchRange(targetSection, relocation.offset(), Integer.BYTES);
                    long sourceAfterPatch = (long) targetSection.rva + relocation.offset() + Integer.BYTES;
                    long displacement = (long) target.rva - sourceAfterPatch + data.getInt(relocation.offset());
                    if (displacement < Integer.MIN_VALUE || displacement > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("REL32 overflow for symbol: " + referenced.name());
                    }
                    data.putInt(relocation.offset(), (int) displacement);
                } else if (relocation.type() == IMAGE_REL_AMD64_ADDR64) {
                    requirePatchRange(targetSection, relocation.offset(), Long.BYTES);
                    data.putLong(relocation.offset(), IMAGE_BASE + target.rva + data.getLong(relocation.offset()));
                } else {
                    throw new IllegalArgumentException("unsupported AMD64 relocation type: 0x"
                            + Integer.toHexString(relocation.type()));
                }
            }
        }
    }

    private static void requirePatchRange(LinkedSection section, int offset, int size) {
        if (offset < 0 || offset > section.data.length - size) {
            throw new IllegalArgumentException("relocation patch is outside section " + section.name);
        }
    }

    private static void writeDosHeader(ByteBuffer output) {
        output.put(0, (byte) 'M');
        output.put(1, (byte) 'Z');
        output.putInt(0x3C, PE_OFFSET);
        byte[] message = "This program cannot be run in DOS mode.\r\n$".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(message, 0, output.array(), 0x40, message.length);
    }

    private static void writeCoffHeader(ByteBuffer output, int sectionCount) {
        output.putShort((short) IMAGE_FILE_MACHINE_AMD64);
        output.putShort((short) sectionCount);
        output.putInt(0);
        output.putInt(0);
        output.putInt(0);
        output.putShort((short) OPTIONAL_HEADER_SIZE);
        output.putShort((short) 0x0022); // EXECUTABLE_IMAGE | LARGE_ADDRESS_AWARE
    }

    private static void writeOptionalHeader(
            ByteBuffer output,
            List<LinkedSection> sections,
            int entryRva,
            int imageSize,
            int headersSize
            , ImportDirectory imports, int sectionAlignment
    ) {
        int codeSize = sections.stream()
                .filter(section -> (section.characteristics & 0x20) != 0)
                .mapToInt(section -> section.rawSize)
                .sum();
        int initializedDataSize = sections.stream()
                .filter(section -> (section.characteristics & 0x40) != 0)
                .mapToInt(section -> section.rawSize)
                .sum();
        int baseOfCode = sections.stream()
                .filter(section -> (section.characteristics & 0x20) != 0)
                .mapToInt(section -> section.rva)
                .findFirst()
                .orElse(0);

        output.putShort((short) 0x020B);
        output.put((byte) 1);
        output.put((byte) 0);
        output.putInt(codeSize);
        output.putInt(initializedDataSize);
        output.putInt(0);
        output.putInt(entryRva);
        output.putInt(baseOfCode);
        output.putLong(IMAGE_BASE);
        output.putInt(sectionAlignment);
        output.putInt(FILE_ALIGNMENT);
        output.putShort((short) 6);
        output.putShort((short) 0);
        output.putShort((short) 0);
        output.putShort((short) 0);
        output.putShort((short) 6);
        output.putShort((short) 0);
        output.putInt(0);
        output.putInt(imageSize);
        output.putInt(headersSize);
        output.putInt(0);
        output.putShort((short) SUBSYSTEM_WINDOWS_CUI);
        output.putShort((short) (DLL_CHARACTERISTICS_NX_COMPAT | DLL_CHARACTERISTICS_TERMINAL_SERVER_AWARE));
        output.putLong(0x100000L);
        output.putLong(0x1000L);
        output.putLong(0x100000L);
        output.putLong(0x1000L);
        output.putInt(0);
        output.putInt(16);
        for (int index = 0; index < 16; index++) {
            if (index == 1) {
                output.putInt(imports.importRva);
                output.putInt(imports.importSize);
            } else if (index == 12) {
                output.putInt(imports.iatRva);
                output.putInt(imports.iatSize);
            } else {
                output.putLong(0);
            }
        }
    }

    private static void writeSectionHeader(ByteBuffer output, LinkedSection section) {
        byte[] name = section.name.getBytes(StandardCharsets.US_ASCII);
        if (name.length > 8) {
            throw new IllegalArgumentException("PE section name is too long: " + section.name);
        }
        output.put(name);
        for (int index = name.length; index < 8; index++) {
            output.put((byte) 0);
        }
        output.putInt(section.data.length);
        output.putInt(section.rva);
        output.putInt(section.rawSize);
        output.putInt(section.rawPointer);
        output.putInt(0);
        output.putInt(0);
        output.putShort((short) 0);
        output.putShort((short) 0);
        output.putInt(section.characteristics);
    }

    private static int align(int value, int alignment) {
        return (value + alignment - 1) & -alignment;
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }

    private static final class LinkedSection {
        private final int number;
        private final String name;
        private final byte[] data;
        private final int rva;
        private final int rawPointer;
        private final int rawSize;
        private final int characteristics;

        private LinkedSection(
                int number,
                String name,
                byte[] data,
                int rva,
                int rawPointer,
                int rawSize,
                int characteristics
        ) {
            this.number = number;
            this.name = name;
            this.data = data;
            this.rva = rva;
            this.rawPointer = rawPointer;
            this.rawSize = rawSize;
            this.characteristics = characteristics;
        }
    }

    private record ResolvedSymbol(int rva) {
    }

    private record ImportDirectory(int importRva, int importSize, int iatRva, int iatSize) {
        private static ImportDirectory empty() {
            return new ImportDirectory(0, 0, 0, 0);
        }
    }

    private static final class ImportPlan {
        private final List<ImportDll> dlls;
        private final Map<String, Integer> iatOffsets = new LinkedHashMap<>();
        private final int descriptorSize;
        private final int firstIatOffset;
        private final int totalIatSize;
        private final int size;
        private int thunkSectionNumber;
        private int thunkStart;

        private ImportPlan(
                List<ImportDll> dlls,
                int descriptorSize,
                int firstIatOffset,
                int totalIatSize,
                int size
        ) {
            this.dlls = dlls;
            this.descriptorSize = descriptorSize;
            this.firstIatOffset = firstIatOffset;
            this.totalIatSize = totalIatSize;
            this.size = size;
            for (ImportDll dll : dlls) {
                for (int index = 0; index < dll.entries.size(); index++) {
                    iatOffsets.put(dll.entries.get(index).sourceName(), dll.iatOffset + index * Long.BYTES);
                }
            }
        }

        private static ImportPlan plan(List<String> symbols, Map<String, LibraryBinding> bindings) {
            LinkedHashMap<String, ArrayList<ImportEntry>> byDll = new LinkedHashMap<>();
            symbols.stream()
                    .sorted(Comparator
                            .comparing((String symbol) -> bindings.get(symbol).dllName())
                            .thenComparing(symbol -> bindings.get(symbol).exportName())
                            .thenComparing(Comparator.naturalOrder()))
                    .forEach(sourceName -> {
                        LibraryBinding binding = bindings.get(sourceName);
                        byDll.computeIfAbsent(binding.dllName(), ignored -> new ArrayList<>())
                                .add(new ImportEntry(sourceName, binding.exportName()));
                    });
            int descriptorSize = (byDll.size() + 1) * 20;
            int cursor = descriptorSize;
            ArrayList<ImportDll> dlls = new ArrayList<>();
            for (Map.Entry<String, ArrayList<ImportEntry>> entry : byDll.entrySet()) {
                ImportDll dll = new ImportDll(entry.getKey(), List.copyOf(entry.getValue()));
                dll.iltOffset = cursor;
                cursor += (dll.entries.size() + 1) * Long.BYTES;
                dlls.add(dll);
            }
            int firstIat = cursor;
            for (ImportDll dll : dlls) {
                dll.iatOffset = cursor;
                cursor += (dll.entries.size() + 1) * Long.BYTES;
            }
            int totalIat = cursor - firstIat;
            for (ImportDll dll : dlls) {
                for (ImportEntry entry : dll.entries) {
                    cursor = align(cursor, 2);
                    dll.hintNameOffsets.put(entry.sourceName(), cursor);
                    cursor += 2 + entry.exportName().getBytes(StandardCharsets.US_ASCII).length + 1;
                }
                dll.nameOffset = cursor;
                cursor += dll.name.getBytes(StandardCharsets.US_ASCII).length + 1;
            }
            return new ImportPlan(List.copyOf(dlls), descriptorSize, firstIat, totalIat, cursor);
        }

        private byte[] build(int sectionRva) {
            byte[] result = new byte[size];
            ByteBuffer output = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
            for (int dllIndex = 0; dllIndex < dlls.size(); dllIndex++) {
                ImportDll dll = dlls.get(dllIndex);
                int descriptor = dllIndex * 20;
                output.putInt(descriptor, sectionRva + dll.iltOffset);
                output.putInt(descriptor + 12, sectionRva + dll.nameOffset);
                output.putInt(descriptor + 16, sectionRva + dll.iatOffset);
                for (int symbolIndex = 0; symbolIndex < dll.entries.size(); symbolIndex++) {
                    ImportEntry entry = dll.entries.get(symbolIndex);
                    long hintNameRva = Integer.toUnsignedLong(
                            sectionRva + dll.hintNameOffsets.get(entry.sourceName())
                    );
                    output.putLong(dll.iltOffset + symbolIndex * 8, hintNameRva);
                    output.putLong(dll.iatOffset + symbolIndex * 8, hintNameRva);
                    int hintOffset = dll.hintNameOffsets.get(entry.sourceName());
                    output.putShort(hintOffset, (short) 0);
                    putAsciiZ(result, hintOffset + 2, entry.exportName());
                }
                putAsciiZ(result, dll.nameOffset, dll.name);
            }
            return result;
        }

        private static void putAsciiZ(byte[] target, int offset, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(bytes, 0, target, offset, bytes.length);
            target[offset + bytes.length] = 0;
        }
    }

    private static final class ImportDll {
        private final String name;
        private final List<ImportEntry> entries;
        private final Map<String, Integer> hintNameOffsets = new LinkedHashMap<>();
        private int iltOffset;
        private int iatOffset;
        private int nameOffset;

        private ImportDll(String name, List<ImportEntry> entries) {
            this.name = name;
            this.entries = entries;
        }
    }

    private record ImportEntry(String sourceName, String exportName) {
    }
}
