package minic.compiler.pe;

import minic.compiler.coff.CoffObjectFile;
import minic.compiler.coff.CoffObjectReader;
import minic.compiler.coff.CoffObjectReader.CoffRelocation;
import minic.compiler.coff.CoffObjectReader.CoffSection;
import minic.compiler.coff.CoffObjectReader.CoffSymbol;
import minic.compiler.coff.CoffObjectReader.ParsedCoffObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
     * 链接一个由 MiniC 生成的 COFF 对象。当前阶段要求所有符号均在对象内定义。
     */
    public PeImage link(CoffObjectFile objectFile, String entrySymbol) {
        Objects.requireNonNull(objectFile, "objectFile");
        Objects.requireNonNull(entrySymbol, "entrySymbol");
        ParsedCoffObject object = reader.read(objectFile);
        if (object.sections().isEmpty()) {
            throw new IllegalArgumentException("COFF object has no sections");
        }

        int headersSize = align(
                PE_OFFSET + 4 + 20 + OPTIONAL_HEADER_SIZE + object.sections().size() * 40,
                FILE_ALIGNMENT
        );
        ArrayList<LinkedSection> sections = new ArrayList<>();
        int nextRva = SECTION_ALIGNMENT;
        int nextRaw = headersSize;
        for (int index = 0; index < object.sections().size(); index++) {
            CoffSection section = object.sections().get(index);
            byte[] data = section.data();
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
            nextRva = align(nextRva + Math.max(data.length, 1), SECTION_ALIGNMENT);
            nextRaw += linked.rawSize;
        }

        LinkedHashMap<String, ResolvedSymbol> symbols = resolveSymbols(object.symbols(), sections);
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
        writeOptionalHeader(output, sections, entry.rva, nextRva, headersSize);
        for (LinkedSection section : sections) {
            writeSectionHeader(output, section);
        }
        for (LinkedSection section : sections) {
            System.arraycopy(section.data, 0, image, section.rawPointer, section.data.length);
        }
        return new PeImage(image);
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
        output.putInt(SECTION_ALIGNMENT);
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
            output.putLong(0);
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
}
