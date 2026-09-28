package minic.compiler.nativebuild.coff;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 内置链接器使用的受控 AMD64 COFF reader。
 */
public final class CoffObjectReader {
    private static final int IMAGE_FILE_MACHINE_AMD64 = 0x8664;
    private static final int COFF_HEADER_SIZE = 20;
    private static final int SECTION_HEADER_SIZE = 40;
    private static final int SYMBOL_SIZE = 18;
    private static final int RELOCATION_SIZE = 10;

    public ParsedCoffObject read(CoffObjectFile objectFile) {
        Objects.requireNonNull(objectFile, "objectFile");
        byte[] bytes = objectFile.bytes();
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        requireRange(bytes, 0, COFF_HEADER_SIZE, "COFF header");
        int machine = u16(buffer, 0);
        if (machine != IMAGE_FILE_MACHINE_AMD64) {
            throw new IllegalArgumentException("unsupported COFF machine: 0x" + Integer.toHexString(machine));
        }
        int sectionCount = u16(buffer, 2);
        int symbolTablePointer = buffer.getInt(8);
        int symbolCount = buffer.getInt(12);
        int optionalHeaderSize = u16(buffer, 16);
        if (optionalHeaderSize != 0) {
            throw new IllegalArgumentException("COFF object must not contain an optional header");
        }
        requireRange(bytes, COFF_HEADER_SIZE, sectionCount * SECTION_HEADER_SIZE, "section headers");
        requireRange(bytes, symbolTablePointer, symbolCount * SYMBOL_SIZE + Integer.BYTES, "symbol table");
        int stringTableOffset = symbolTablePointer + symbolCount * SYMBOL_SIZE;
        int stringTableSize = buffer.getInt(stringTableOffset);
        if (stringTableSize < Integer.BYTES) {
            throw new IllegalArgumentException("invalid COFF string table size");
        }
        requireRange(bytes, stringTableOffset, stringTableSize, "string table");

        ArrayList<CoffSymbol> symbols = new ArrayList<>();
        for (int index = 0; index < symbolCount; index++) {
            int offset = symbolTablePointer + index * SYMBOL_SIZE;
            String name = readSymbolName(bytes, buffer, offset, stringTableOffset, stringTableSize);
            int value = buffer.getInt(offset + 8);
            int sectionNumber = Short.toUnsignedInt(buffer.getShort(offset + 12));
            int storageClass = Byte.toUnsignedInt(buffer.get(offset + 16));
            int auxiliaryCount = Byte.toUnsignedInt(buffer.get(offset + 17));
            if (auxiliaryCount != 0) {
                throw new IllegalArgumentException("COFF auxiliary symbols are not supported");
            }
            symbols.add(new CoffSymbol(name, value, sectionNumber, storageClass));
        }

        ArrayList<CoffSection> sections = new ArrayList<>();
        for (int index = 0; index < sectionCount; index++) {
            int offset = COFF_HEADER_SIZE + index * SECTION_HEADER_SIZE;
            String name = readFixedName(bytes, offset, 8);
            int rawSize = buffer.getInt(offset + 16);
            int rawPointer = buffer.getInt(offset + 20);
            int relocationPointer = buffer.getInt(offset + 24);
            int relocationCount = u16(buffer, offset + 32);
            int characteristics = buffer.getInt(offset + 36);
            requireRange(bytes, rawPointer, rawSize, "section " + name);
            requireRange(bytes, relocationPointer, relocationCount * RELOCATION_SIZE, "relocations for " + name);
            ArrayList<CoffRelocation> relocations = new ArrayList<>();
            for (int relocationIndex = 0; relocationIndex < relocationCount; relocationIndex++) {
                int relocationOffset = relocationPointer + relocationIndex * RELOCATION_SIZE;
                int virtualAddress = buffer.getInt(relocationOffset);
                int symbolIndex = buffer.getInt(relocationOffset + 4);
                int type = u16(buffer, relocationOffset + 8);
                if (symbolIndex < 0 || symbolIndex >= symbols.size()) {
                    throw new IllegalArgumentException("COFF relocation has invalid symbol index: " + symbolIndex);
                }
                relocations.add(new CoffRelocation(virtualAddress, symbolIndex, type));
            }
            sections.add(new CoffSection(
                    name,
                    Arrays.copyOfRange(bytes, rawPointer, rawPointer + rawSize),
                    characteristics,
                    relocations
            ));
        }
        return new ParsedCoffObject(sections, symbols);
    }

    private static String readSymbolName(
            byte[] bytes,
            ByteBuffer buffer,
            int symbolOffset,
            int stringTableOffset,
            int stringTableSize
    ) {
        if (buffer.getInt(symbolOffset) != 0) {
            return readFixedName(bytes, symbolOffset, 8);
        }
        int relativeOffset = buffer.getInt(symbolOffset + 4);
        if (relativeOffset < Integer.BYTES || relativeOffset >= stringTableSize) {
            throw new IllegalArgumentException("invalid COFF string offset: " + relativeOffset);
        }
        int start = stringTableOffset + relativeOffset;
        int end = start;
        int limit = stringTableOffset + stringTableSize;
        while (end < limit && bytes[end] != 0) {
            end++;
        }
        if (end == limit) {
            throw new IllegalArgumentException("unterminated COFF symbol name");
        }
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }

    private static String readFixedName(byte[] bytes, int offset, int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII);
    }

    private static int u16(ByteBuffer buffer, int offset) {
        return Short.toUnsignedInt(buffer.getShort(offset));
    }

    private static void requireRange(byte[] bytes, int offset, int length, String subject) {
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IllegalArgumentException(subject + " is outside the COFF file");
        }
    }

    public record ParsedCoffObject(List<CoffSection> sections, List<CoffSymbol> symbols) {
        public ParsedCoffObject {
            sections = List.copyOf(sections);
            symbols = List.copyOf(symbols);
        }
    }

    public record CoffSection(String name, byte[] data, int characteristics, List<CoffRelocation> relocations) {
        public CoffSection {
            data = data.clone();
            relocations = List.copyOf(relocations);
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }

    public record CoffSymbol(String name, int value, int sectionNumber, int storageClass) {
        public boolean defined() {
            return sectionNumber > 0;
        }
    }

    public record CoffRelocation(int offset, int symbolIndex, int type) {
    }
}
