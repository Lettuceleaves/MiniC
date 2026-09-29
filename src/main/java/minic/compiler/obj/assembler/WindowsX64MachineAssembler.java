package minic.compiler.obj.assembler;

import minic.compiler.asm.AsmResult;
import minic.compiler.obj.machine.ImmediateOperand;
import minic.compiler.obj.machine.MachineData;
import minic.compiler.obj.machine.MachineInstruction;
import minic.compiler.obj.machine.MachineItem;
import minic.compiler.obj.machine.MachineLabel;
import minic.compiler.obj.machine.MachineModule;
import minic.compiler.obj.machine.MachineOperand;
import minic.compiler.obj.machine.MachineSection;
import minic.compiler.obj.machine.MachineSectionKind;
import minic.compiler.obj.machine.MemoryOperand;
import minic.compiler.obj.machine.RegisterOperand;
import minic.compiler.obj.machine.SymbolOperand;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 将现有 Windows x64 汇编展示转换成结构化机器模块。
 *
 * <p>这是迁移兼容层：二进制编码只消费 {@link MachineModule}，不解析文本。</p>
 */
public final class WindowsX64MachineAssembler {
    private static final Set<String> REGISTERS = registerNames();

    public MachineModule assemble(AsmResult source) {
        Objects.requireNonNull(source, "source");
        ArrayList<MachineItem> text = new ArrayList<>();
        ArrayList<MachineItem> readOnlyData = new ArrayList<>();
        ArrayList<String> externals = new ArrayList<>();
        Section section = Section.NONE;

        String normalized = source.text().replace("\r\n", "\n").replace('\r', '\n');
        for (String rawLine : normalized.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }
            if (line.equalsIgnoreCase(".const")) {
                section = Section.CONST;
                continue;
            }
            if (line.equalsIgnoreCase(".code")) {
                section = Section.CODE;
                continue;
            }
            if (line.equalsIgnoreCase("END") || line.startsWith("PUBLIC ")) {
                continue;
            }
            if (line.startsWith("EXTERN ")) {
                int colon = line.indexOf(':');
                String symbol = line.substring("EXTERN ".length(), colon < 0 ? line.length() : colon).trim();
                if (!externals.contains(symbol)) {
                    externals.add(symbol);
                }
                continue;
            }
            if (section == Section.CONST) {
                parseDataLine(line, readOnlyData);
                continue;
            }
            if (section != Section.CODE) {
                continue;
            }
            if (line.endsWith(" PROC")) {
                text.add(new MachineLabel(line.substring(0, line.length() - " PROC".length()).trim(), true));
                continue;
            }
            if (line.endsWith(" ENDP")) {
                continue;
            }
            if (line.endsWith(":")) {
                text.add(new MachineLabel(line.substring(0, line.length() - 1), false));
                continue;
            }
            text.add(parseInstruction(line));
        }

        ArrayList<MachineSection> sections = new ArrayList<>();
        if (!text.isEmpty()) {
            sections.add(new MachineSection(".text", MachineSectionKind.CODE, 16, text));
        }
        if (!readOnlyData.isEmpty()) {
            sections.add(new MachineSection(".rdata", MachineSectionKind.READ_ONLY_DATA, 8, readOnlyData));
        }
        return new MachineModule(source.entrySymbol(), sections, externals);
    }

    private static void parseDataLine(String line, List<MachineItem> output) {
        String values;
        if (line.startsWith("BYTE ")) {
            values = line.substring("BYTE ".length());
        } else {
            int marker = line.indexOf(" BYTE ");
            if (marker < 1) {
                throw new IllegalArgumentException("unsupported MASM data line: " + line);
            }
            output.add(new MachineLabel(line.substring(0, marker).trim(), false));
            values = line.substring(marker + " BYTE ".length());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (String value : values.split(",")) {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < -128 || parsed > 255) {
                throw new IllegalArgumentException("BYTE value is outside range: " + parsed);
            }
            bytes.write(parsed);
        }
        output.add(new MachineData(bytes.toByteArray(), 1));
    }

    private static MachineInstruction parseInstruction(String line) {
        if (line.equalsIgnoreCase("rep movsb")) {
            return new MachineInstruction("rep_movsb");
        }
        int whitespace = firstWhitespace(line);
        if (whitespace < 0) {
            return new MachineInstruction(line.toLowerCase(Locale.ROOT));
        }
        String mnemonic = line.substring(0, whitespace).toLowerCase(Locale.ROOT);
        String operandText = line.substring(whitespace).trim();
        List<String> parts = splitOperands(operandText);
        ArrayList<MachineOperand> operands = new ArrayList<>();
        for (String part : parts) {
            operands.add(parseOperand(part));
        }
        return new MachineInstruction(mnemonic, operands, null);
    }

    private static MachineOperand parseOperand(String text) {
        String value = text.trim();
        int sizeBits = 0;
        for (SizePrefix prefix : SizePrefix.values()) {
            if (value.regionMatches(true, 0, prefix.text, 0, prefix.text.length())) {
                sizeBits = prefix.bits;
                value = value.substring(prefix.text.length()).trim();
                break;
            }
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            return parseMemory(value.substring(1, value.length() - 1), sizeBits);
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (REGISTERS.contains(lower)) {
            return new RegisterOperand(lower);
        }
        if (isInteger(value)) {
            return new ImmediateOperand(parseInteger(value));
        }
        if (sizeBits != 0) {
            return MemoryOperand.ripRelative(sizeBits, value);
        }
        // MASM 的 lea reg, symbol 表示地址；其余裸符号由 call/jump 使用。
        return new SymbolOperand(value);
    }

    private static MemoryOperand parseMemory(String expression, int sizeBits) {
        String compact = expression.replace(" ", "");
        String base = null;
        String index = null;
        int scale = 1;
        int displacement = 0;
        String symbol = null;
        int position = 0;
        int sign = 1;
        while (position < compact.length()) {
            char current = compact.charAt(position);
            if (current == '+') {
                sign = 1;
                position++;
                continue;
            }
            if (current == '-') {
                sign = -1;
                position++;
                continue;
            }
            int end = position;
            while (end < compact.length() && compact.charAt(end) != '+' && compact.charAt(end) != '-') {
                end++;
            }
            String term = compact.substring(position, end);
            int star = term.indexOf('*');
            String candidate = star < 0 ? term : term.substring(0, star);
            if (REGISTERS.contains(candidate.toLowerCase(Locale.ROOT))) {
                if (star >= 0) {
                    index = candidate.toLowerCase(Locale.ROOT);
                    scale = Integer.parseInt(term.substring(star + 1));
                } else if (base == null) {
                    base = candidate.toLowerCase(Locale.ROOT);
                } else {
                    index = candidate.toLowerCase(Locale.ROOT);
                    scale = 1;
                }
            } else if (isInteger(term)) {
                displacement = Math.addExact(displacement, Math.multiplyExact(sign, Math.toIntExact(parseInteger(term))));
            } else {
                if (sign < 0 || symbol != null) {
                    throw new IllegalArgumentException("unsupported symbolic memory expression: " + expression);
                }
                symbol = term;
            }
            sign = 1;
            position = end;
        }
        if (symbol != null && base == null) {
            base = "rip";
        }
        return new MemoryOperand(sizeBits, symbol, base, index, scale, displacement);
    }

    private static List<String> splitOperands(String text) {
        ArrayList<String> result = new ArrayList<>();
        int bracketDepth = 0;
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '[') {
                bracketDepth++;
            } else if (current == ']') {
                bracketDepth--;
            } else if (current == ',' && bracketDepth == 0) {
                result.add(text.substring(start, index).trim());
                start = index + 1;
            }
        }
        result.add(text.substring(start).trim());
        return List.copyOf(result);
    }

    private static int firstWhitespace(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isInteger(String value) {
        if (value.isEmpty()) {
            return false;
        }
        int start = value.charAt(0) == '-' || value.charAt(0) == '+' ? 1 : 0;
        if (start == value.length()) {
            return false;
        }
        for (int index = start; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static long parseInteger(String value) {
        if (value.startsWith("-")) {
            return Long.parseLong(value);
        }
        try {
            return Long.parseLong(value.startsWith("+") ? value.substring(1) : value);
        } catch (NumberFormatException ignored) {
            return Long.parseUnsignedLong(value);
        }
    }

    private static Set<String> registerNames() {
        HashSet<String> names = new HashSet<>();
        names.addAll(List.of(
                "rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi",
                "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15",
                "eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi",
                "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d",
                "ax", "cx", "dx", "bx", "sp", "bp", "si", "di",
                "al", "cl", "dl", "bl", "spl", "bpl", "sil", "dil",
                "r8b", "r9b", "r10b", "r11b", "r12b", "r13b", "r14b", "r15b"
        ));
        for (int index = 0; index < 16; index++) {
            names.add("xmm" + index);
        }
        return Set.copyOf(names);
    }

    private enum Section {
        NONE,
        CONST,
        CODE
    }

    private enum SizePrefix {
        BYTE("BYTE PTR ", 8),
        WORD("WORD PTR ", 16),
        DWORD("DWORD PTR ", 32),
        QWORD("QWORD PTR ", 64);

        private final String text;
        private final int bits;

        SizePrefix(String text, int bits) {
            this.text = text;
            this.bits = bits;
        }
    }
}
