package minic.compiler.nativebuild.coff;

import minic.compiler.nativebuild.machine.MachineItem;
import minic.compiler.nativebuild.machine.MachineLabel;
import minic.compiler.nativebuild.machine.MachineModule;
import minic.compiler.nativebuild.machine.MachineSection;
import minic.compiler.nativebuild.machine.MachineSectionKind;
import minic.compiler.nativebuild.x64.EncodedMachineSection;
import minic.compiler.nativebuild.x64.EncodedMachineModule;
import minic.compiler.nativebuild.x64.MachineRelocation;
import minic.compiler.nativebuild.x64.MachineRelocationKind;
import minic.compiler.nativebuild.x64.X64Encoder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 仅依赖 Java 的 AMD64 COFF 对象文件 writer。
 */
public final class CoffObjectWriter {
    private static final int IMAGE_FILE_MACHINE_AMD64 = 0x8664;
    private static final int IMAGE_REL_AMD64_ADDR64 = 0x0001;
    private static final int IMAGE_REL_AMD64_REL32 = 0x0004;
    private static final int IMAGE_SYM_CLASS_EXTERNAL = 2;
    private static final int IMAGE_SYM_CLASS_STATIC = 3;
    private static final int COFF_HEADER_SIZE = 20;
    private static final int SECTION_HEADER_SIZE = 40;
    private static final int RELOCATION_SIZE = 10;
    private static final int SYMBOL_SIZE = 18;

    private final X64Encoder encoder;

    public CoffObjectWriter() {
        this(new X64Encoder());
    }

    public CoffObjectWriter(X64Encoder encoder) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
    }

    /**
     * 将机器模块编码为标准 AMD64 COFF 对象。
     *
     * @param module 结构化机器模块
     * @return COFF 对象文件
     */
    public CoffObjectFile write(MachineModule module) {
        Objects.requireNonNull(module, "module");
        return write(encoder.encode(module));
    }

    /** 将已经完成 x64 编码的模块封装为 COFF。 */
    public CoffObjectFile write(EncodedMachineModule encodedModule) {
        Objects.requireNonNull(encodedModule, "encodedModule");
        MachineModule module = encodedModule.source();
        if (module.sections().size() > 0xFFFF) {
            throw new IllegalArgumentException("too many COFF sections");
        }

        ArrayList<SectionRecord> sections = new ArrayList<>();
        for (int index = 0; index < module.sections().size(); index++) {
            MachineSection section = module.sections().get(index);
            sections.add(new SectionRecord(index + 1, section, encodedModule.sections().get(index)));
        }

        ArrayList<SymbolRecord> symbols = collectSymbols(module, sections);
        LinkedHashMap<String, Integer> symbolIndices = new LinkedHashMap<>();
        for (int index = 0; index < symbols.size(); index++) {
            SymbolRecord symbol = symbols.get(index);
            if (symbolIndices.putIfAbsent(symbol.name, index) != null) {
                throw new IllegalArgumentException("duplicate COFF symbol: " + symbol.name);
            }
        }
        for (SectionRecord section : sections) {
            for (MachineRelocation relocation : section.encoded.relocations()) {
                if (!symbolIndices.containsKey(relocation.symbol())) {
                    throw new IllegalArgumentException("relocation references unknown symbol: " + relocation.symbol());
                }
            }
        }

        int cursor = COFF_HEADER_SIZE + sections.size() * SECTION_HEADER_SIZE;
        for (SectionRecord section : sections) {
            cursor = align(cursor, 4);
            section.rawPointer = cursor;
            cursor += section.encoded.bytes().length;
            if (!section.encoded.relocations().isEmpty()) {
                cursor = align(cursor, 4);
                section.relocationPointer = cursor;
                cursor += section.encoded.relocations().size() * RELOCATION_SIZE;
            }
        }
        int symbolTablePointer = align(cursor, 4);

        StringTable stringTable = new StringTable();
        for (SymbolRecord symbol : symbols) {
            if (!fitsShortName(symbol.name)) {
                stringTable.offsetOf(symbol.name);
            }
        }
        int totalSize = symbolTablePointer + symbols.size() * SYMBOL_SIZE + stringTable.byteSize();
        LittleEndianBuffer output = new LittleEndianBuffer(totalSize);

        writeHeader(output, sections.size(), symbolTablePointer, symbols.size());
        for (SectionRecord section : sections) {
            writeSectionHeader(output, section);
        }
        for (SectionRecord section : sections) {
            output.position(section.rawPointer);
            output.bytes(section.encoded.bytes());
            if (section.relocationPointer != 0) {
                output.position(section.relocationPointer);
                for (MachineRelocation relocation : section.encoded.relocations()) {
                    output.u32(relocation.offset());
                    output.u32(symbolIndices.get(relocation.symbol()));
                    output.u16(relocationType(relocation.kind()));
                }
            }
        }
        output.position(symbolTablePointer);
        for (SymbolRecord symbol : symbols) {
            writeSymbol(output, symbol, stringTable);
        }
        output.u32(stringTable.byteSize());
        output.bytes(stringTable.payload());
        return new CoffObjectFile(output.bytes());
    }

    private static ArrayList<SymbolRecord> collectSymbols(
            MachineModule module,
            List<SectionRecord> sections
    ) {
        ArrayList<SymbolRecord> symbols = new ArrayList<>();
        HashSet<String> defined = new HashSet<>();
        for (SectionRecord section : sections) {
            for (MachineItem item : section.source.items()) {
                if (!(item instanceof MachineLabel label)) {
                    continue;
                }
                if (!defined.add(label.name())) {
                    throw new IllegalArgumentException("duplicate machine symbol: " + label.name());
                }
                Integer value = section.encoded.symbols().get(label.name());
                if (value == null) {
                    throw new IllegalStateException("encoder did not expose label: " + label.name());
                }
                symbols.add(new SymbolRecord(
                        label.name(),
                        value,
                        section.number,
                        label.global() ? IMAGE_SYM_CLASS_EXTERNAL : IMAGE_SYM_CLASS_STATIC
                ));
            }
        }
        for (String external : module.externalSymbols()) {
            if (!defined.contains(external)) {
                symbols.add(new SymbolRecord(external, 0, 0, IMAGE_SYM_CLASS_EXTERNAL));
            }
        }
        return symbols;
    }

    private static void writeHeader(
            LittleEndianBuffer output,
            int sectionCount,
            int symbolTablePointer,
            int symbolCount
    ) {
        output.u16(IMAGE_FILE_MACHINE_AMD64);
        output.u16(sectionCount);
        output.u32(0); // 可复现构建：时间戳固定为 0。
        output.u32(symbolTablePointer);
        output.u32(symbolCount);
        output.u16(0); // 对象文件没有 optional header。
        output.u16(0);
    }

    private static void writeSectionHeader(LittleEndianBuffer output, SectionRecord section) {
        writeFixedName(output, section.source.name());
        output.u32(0);
        output.u32(0);
        output.u32(section.encoded.bytes().length);
        output.u32(section.rawPointer);
        output.u32(section.relocationPointer);
        output.u32(0);
        output.u16(section.encoded.relocations().size());
        output.u16(0);
        output.u32(sectionCharacteristics(section.source.kind(), section.source.alignment()));
    }

    private static void writeSymbol(
            LittleEndianBuffer output,
            SymbolRecord symbol,
            StringTable stringTable
    ) {
        if (fitsShortName(symbol.name)) {
            writeFixedName(output, symbol.name);
        } else {
            output.u32(0);
            output.u32(stringTable.offsetOf(symbol.name));
        }
        output.u32(symbol.value);
        output.u16(symbol.sectionNumber);
        output.u16(symbol.sectionNumber == 0 ? 0 : 0x20); // function 标记对当前代码符号足够。
        output.u8(symbol.storageClass);
        output.u8(0);
    }

    private static void writeFixedName(LittleEndianBuffer output, String name) {
        byte[] bytes = name.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > 8) {
            throw new IllegalArgumentException("COFF short name is too long: " + name);
        }
        output.bytes(bytes);
        for (int index = bytes.length; index < 8; index++) {
            output.u8(0);
        }
    }

    private static boolean fitsShortName(String name) {
        return name.getBytes(StandardCharsets.UTF_8).length <= 8;
    }

    private static int relocationType(MachineRelocationKind kind) {
        return switch (kind) {
            case REL32, RIP_REL32 -> IMAGE_REL_AMD64_REL32;
            case ADDR64 -> IMAGE_REL_AMD64_ADDR64;
        };
    }

    private static int sectionCharacteristics(MachineSectionKind kind, int alignment) {
        int base = switch (kind) {
            case CODE -> 0x60000020;
            case READ_ONLY_DATA, UNWIND_DATA -> 0x40000040;
            case WRITABLE_DATA -> 0xC0000040;
        };
        return base | alignmentFlag(alignment);
    }

    private static int alignmentFlag(int alignment) {
        int exponent = Integer.numberOfTrailingZeros(alignment);
        if (exponent < 0 || exponent > 13) {
            throw new IllegalArgumentException("unsupported COFF section alignment: " + alignment);
        }
        return (exponent + 1) << 20;
    }

    private static int align(int value, int alignment) {
        return (value + alignment - 1) & -alignment;
    }

    private static final class SectionRecord {
        private final int number;
        private final MachineSection source;
        private final EncodedMachineSection encoded;
        private int rawPointer;
        private int relocationPointer;

        private SectionRecord(int number, MachineSection source, EncodedMachineSection encoded) {
            this.number = number;
            this.source = source;
            this.encoded = encoded;
        }
    }

    private record SymbolRecord(String name, int value, int sectionNumber, int storageClass) {
    }

    private static final class StringTable {
        private final LinkedHashMap<String, Integer> offsets = new LinkedHashMap<>();
        private final ByteArrayOutputStream payload = new ByteArrayOutputStream();

        private int offsetOf(String value) {
            return offsets.computeIfAbsent(value, ignored -> {
                int offset = Integer.BYTES + payload.size();
                payload.writeBytes(value.getBytes(StandardCharsets.UTF_8));
                payload.write(0);
                return offset;
            });
        }

        private int byteSize() {
            return Integer.BYTES + payload.size();
        }

        private byte[] payload() {
            return payload.toByteArray();
        }
    }

    private static final class LittleEndianBuffer {
        private final byte[] bytes;
        private int position;

        private LittleEndianBuffer(int size) {
            bytes = new byte[size];
        }

        private void position(int position) {
            if (position < this.position || position > bytes.length) {
                throw new IllegalArgumentException("invalid output position: " + position);
            }
            this.position = position;
        }

        private void u8(int value) {
            bytes[position++] = (byte) value;
        }

        private void u16(int value) {
            u8(value);
            u8(value >>> 8);
        }

        private void u32(long value) {
            u16((int) value);
            u16((int) (value >>> 16));
        }

        private void bytes(byte[] value) {
            System.arraycopy(value, 0, bytes, position, value.length);
            position += value.length;
        }

        private byte[] bytes() {
            return bytes.clone();
        }
    }
}
