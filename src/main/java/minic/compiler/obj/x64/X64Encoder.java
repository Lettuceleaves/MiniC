package minic.compiler.obj.x64;

import minic.compiler.obj.machine.ImmediateOperand;
import minic.compiler.obj.machine.MachineData;
import minic.compiler.obj.machine.MachineInstruction;
import minic.compiler.obj.machine.MachineItem;
import minic.compiler.obj.machine.MachineLabel;
import minic.compiler.obj.machine.MachineModule;
import minic.compiler.obj.machine.MachineOperand;
import minic.compiler.obj.machine.MachineSection;
import minic.compiler.obj.machine.MemoryOperand;
import minic.compiler.obj.machine.RegisterOperand;
import minic.compiler.obj.machine.SymbolOperand;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** MiniC Windows 后端所需 x86-64 指令的内置二进制编码器。 */
public final class X64Encoder {
    /** 编码模块中的全部机器节。 */
    public EncodedMachineModule encode(MachineModule module) {
        Objects.requireNonNull(module, "module");
        ArrayList<EncodedMachineSection> sections = new ArrayList<>();
        for (MachineSection section : module.sections()) {
            sections.add(encode(section));
        }
        return new EncodedMachineModule(module, sections);
    }

    public EncodedMachineSection encode(MachineSection section) {
        Objects.requireNonNull(section, "section");
        LinkedHashMap<String, Integer> symbols = layout(section);
        ByteWriter writer = new ByteWriter();
        ArrayList<MachineRelocation> relocations = new ArrayList<>();
        for (MachineItem item : section.items()) {
            switch (item) {
                case MachineLabel ignored -> { }
                case MachineData data -> {
                    writer.align(data.alignment());int start=writer.size();byte[] bytes=data.bytes();
                    var image=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    for(var address:data.addresses()) {
                        image.putLong(address.offset(),address.addend());
                        relocations.add(new MachineRelocation(start+address.offset(),address.symbol(),MachineRelocationKind.ADDR64,address.addend()));
                    }
                    writer.bytes(bytes);
                }
                case MachineInstruction instruction -> encodeInstruction(writer, instruction, symbols, relocations);
            }
        }
        return new EncodedMachineSection(section.name(), writer.toByteArray(), symbols, relocations);
    }

    private LinkedHashMap<String, Integer> layout(MachineSection section) {
        LinkedHashMap<String, Integer> symbols = new LinkedHashMap<>();
        int offset = 0;
        for (MachineItem item : section.items()) {
            switch (item) {
                case MachineLabel label -> {
                    if (symbols.putIfAbsent(label.name(), offset) != null) throw new IllegalArgumentException("duplicate label: " + label.name());
                }
                case MachineData data -> offset = align(offset, data.alignment()) + data.bytes().length;
                case MachineInstruction instruction -> offset += instructionSize(instruction);
            }
        }
        return symbols;
    }

    private int instructionSize(MachineInstruction instruction) {
        ByteWriter writer = new ByteWriter();
        encodeInstruction(writer, instruction, Map.of(), new ArrayList<>());
        return writer.size();
    }

    private void encodeInstruction(ByteWriter w, MachineInstruction i, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        switch (i.mnemonic().toLowerCase(Locale.ROOT)) {
            case "ret" -> noOperands(w, i, 0xC3);
            case "nop" -> noOperands(w, i, 0x90);
            case "cdq" -> noOperands(w, i, 0x99);
            case "cqo" -> { count(i, 0); w.u8(0x48); w.u8(0x99); }
            case "rep_movsb" -> { count(i, 0); w.u8(0xF3); w.u8(0xA4); }
            case "push" -> pushPop(w, i, 0x50);
            case "pop" -> pushPop(w, i, 0x58);
            case "mov" -> mov(w, i, symbols, relocs);
            case "lea" -> lea(w, i, symbols, relocs);
            case "movzx" -> extension(w, i, false, symbols, relocs);
            case "movsx" -> extension(w, i, true, symbols, relocs);
            case "movsxd" -> extension(w, i, 0x63, true, symbols, relocs);
            case "add" -> integerBinary(w, i, 0x01, 0, symbols, relocs);
            case "or" -> integerBinary(w, i, 0x09, 1, symbols, relocs);
            case "and" -> integerBinary(w, i, 0x21, 4, symbols, relocs);
            case "sub" -> integerBinary(w, i, 0x29, 5, symbols, relocs);
            case "xor" -> integerBinary(w, i, 0x31, 6, symbols, relocs);
            case "cmp" -> integerBinary(w, i, 0x39, 7, symbols, relocs);
            case "imul" -> imul(w, i, symbols, relocs);
            case "idiv" -> unaryGroup(w, i, 7, symbols, relocs);
            case "div" -> unaryGroup(w, i, 6, symbols, relocs);
            case "not" -> unaryGroup(w, i, 2, symbols, relocs);
            case "neg" -> unaryGroup(w, i, 3, symbols, relocs);
            case "shl" -> shift(w, i, 4, symbols, relocs);
            case "shr" -> shift(w, i, 5, symbols, relocs);
            case "sar" -> shift(w, i, 7, symbols, relocs);
            case "sete" -> setcc(w, i, 0x94, symbols, relocs);
            case "setne" -> setcc(w, i, 0x95, symbols, relocs);
            case "setb" -> setcc(w, i, 0x92, symbols, relocs);
            case "setbe" -> setcc(w, i, 0x96, symbols, relocs);
            case "seta" -> setcc(w, i, 0x97, symbols, relocs);
            case "setae" -> setcc(w, i, 0x93, symbols, relocs);
            case "setl" -> setcc(w, i, 0x9C, symbols, relocs);
            case "setle" -> setcc(w, i, 0x9E, symbols, relocs);
            case "setg" -> setcc(w, i, 0x9F, symbols, relocs);
            case "setge" -> setcc(w, i, 0x9D, symbols, relocs);
            case "call" -> call(w, i, symbols, relocs);
            case "jmp" -> branch(w, i, 0xE9, null, symbols, relocs);
            case "je" -> branch(w, i, 0x0F, 0x84, symbols, relocs);
            case "jne" -> branch(w, i, 0x0F, 0x85, symbols, relocs);
            case "jge" -> branch(w, i, 0x0F, 0x8D, symbols, relocs);
            case "movss" -> sseMove(w, i, 0xF3, symbols, relocs);
            case "movsd" -> sseMove(w, i, 0xF2, symbols, relocs);
            case "movd" -> movdMovq(w, i, false, symbols, relocs);
            case "movq" -> movdMovq(w, i, true, symbols, relocs);
            case "xorps" -> sseBinary(w, i, 0, 0x57, symbols, relocs);
            case "xorpd" -> sseBinary(w, i, 0x66, 0x57, symbols, relocs);
            case "ucomiss" -> sseBinary(w, i, 0, 0x2E, symbols, relocs);
            case "ucomisd" -> sseBinary(w, i, 0x66, 0x2E, symbols, relocs);
            case "addss" -> sseBinary(w, i, 0xF3, 0x58, symbols, relocs);
            case "addsd" -> sseBinary(w, i, 0xF2, 0x58, symbols, relocs);
            case "subss" -> sseBinary(w, i, 0xF3, 0x5C, symbols, relocs);
            case "subsd" -> sseBinary(w, i, 0xF2, 0x5C, symbols, relocs);
            case "mulss" -> sseBinary(w, i, 0xF3, 0x59, symbols, relocs);
            case "mulsd" -> sseBinary(w, i, 0xF2, 0x59, symbols, relocs);
            case "divss" -> sseBinary(w, i, 0xF3, 0x5E, symbols, relocs);
            case "divsd" -> sseBinary(w, i, 0xF2, 0x5E, symbols, relocs);
            case "cvtss2sd" -> sseBinary(w, i, 0xF3, 0x5A, symbols, relocs);
            case "cvtsd2ss" -> sseBinary(w, i, 0xF2, 0x5A, symbols, relocs);
            case "cvtsi2ss" -> intToFloat(w, i, 0xF3, symbols, relocs);
            case "cvtsi2sd" -> intToFloat(w, i, 0xF2, symbols, relocs);
            case "cvttss2si" -> floatToInt(w, i, 0xF3, symbols, relocs);
            case "cvttsd2si" -> floatToInt(w, i, 0xF2, symbols, relocs);
            default -> throw new UnsupportedOperationException("unsupported x64 instruction: " + i);
        }
    }

    private static void noOperands(ByteWriter w, MachineInstruction i, int opcode) { count(i, 0); w.u8(opcode); }

    private void mov(ByteWriter w, MachineInstruction i, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2);
        MachineOperand dst = i.operands().get(0), src = i.operands().get(1);
        if (dst instanceof RegisterOperand && src instanceof ImmediateOperand imm) {
            Register r = reg(dst); requireGpr(r); operandSizePrefix(w, r.width); rex(w, r.width == 64, false, false, r.extended(), r.requiresRex);
            if (r.width == 8) { w.u8(0xB0 + r.low()); w.u8((int) imm.value()); }
            else { w.u8(0xB8 + r.low()); if (r.width == 64) w.i64(imm.value()); else if (r.width == 32) w.i32((int) imm.value()); else if (r.width == 16) w.i16((int) imm.value()); else throw unsupported(i); }
            return;
        }
        if (dst instanceof RegisterOperand) {
            Register r = reg(dst); requireGpr(r); emitRm(w, operandSizePrefix(r.width), r.width == 64, new int[]{r.width == 8 ? 0x8A : 0x8B}, r, src, symbols, relocs); return;
        }
        if (dst instanceof MemoryOperand mem && src instanceof RegisterOperand) {
            Register r = reg(src); requireGpr(r); emitRm(w, operandSizePrefix(r.width), r.width == 64, new int[]{r.width == 8 ? 0x88 : 0x89}, r, mem, symbols, relocs); return;
        }
        if (dst instanceof MemoryOperand mem && src instanceof ImmediateOperand imm) {
            int width = memoryWidth(mem, i); emitRm(w, operandSizePrefix(width), width == 64, new int[]{width == 8 ? 0xC6 : 0xC7}, 0, mem, symbols, relocs);
            if (width == 8) w.u8((int) imm.value()); else if (width == 16) w.i16((int) imm.value()); else w.i32(Math.toIntExact(imm.value())); return;
        }
        throw unsupported(i);
    }

    private void lea(ByteWriter w, MachineInstruction i, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2); Register dst = reg(i.operands().get(0)); MachineOperand src = i.operands().get(1);
        if (src instanceof SymbolOperand s) src = MemoryOperand.ripRelative(0, s.symbol());
        if (!(src instanceof MemoryOperand)) throw unsupported(i);
        emitRm(w, 0, dst.width == 64, new int[]{0x8D}, dst, src, symbols, relocs);
    }

    private void extension(ByteWriter w, MachineInstruction i, int opcode, boolean movsxd, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2); Register dst = reg(i.operands().get(0));
        emitRm(w, 0, dst.width == 64, movsxd ? new int[]{opcode} : new int[]{0x0F, opcode}, dst, i.operands().get(1), symbols, relocs);
    }

    private void extension(ByteWriter w, MachineInstruction i, boolean signed, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2);
        Register dst = reg(i.operands().get(0));
        int sourceWidth = width(i.operands().get(1));
        int opcode = switch (sourceWidth) {
            case 8 -> signed ? 0xBE : 0xB6;
            case 16 -> signed ? 0xBF : 0xB7;
            default -> throw unsupported(i);
        };
        emitRm(w, 0, dst.width == 64, new int[]{0x0F, opcode}, dst, i.operands().get(1), symbols, relocs);
    }

    private void integerBinary(ByteWriter w, MachineInstruction i, int registerOpcode, int immediateGroup, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2); MachineOperand dst = i.operands().get(0), src = i.operands().get(1); int width = width(dst);
        if (src instanceof RegisterOperand) { emitRm(w, operandSizePrefix(width), width == 64, new int[]{registerOpcode}, reg(src), dst, symbols, relocs); return; }
        if (src instanceof ImmediateOperand imm) {
            boolean small = imm.value() >= -128 && imm.value() <= 127; int opcode = width == 8 ? 0x80 : small ? 0x83 : 0x81;
            emitRm(w, operandSizePrefix(width), width == 64, new int[]{opcode}, immediateGroup, dst, symbols, relocs);
            if (width == 8 || small) w.u8((int) imm.value()); else if (width == 16) w.i16((int) imm.value()); else w.i32(Math.toIntExact(imm.value())); return;
        }
        throw unsupported(i);
    }

    private void imul(ByteWriter w, MachineInstruction i, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2); Register dst = reg(i.operands().get(0)); MachineOperand src = i.operands().get(1);
        if (src instanceof ImmediateOperand imm) {
            boolean small = imm.value() >= -128 && imm.value() <= 127; emitRm(w, operandSizePrefix(dst.width), dst.width == 64, new int[]{small ? 0x6B : 0x69}, dst, i.operands().get(0), symbols, relocs);
            if (small) w.u8((int) imm.value()); else if (dst.width == 16) w.i16((int) imm.value()); else w.i32(Math.toIntExact(imm.value())); return;
        }
        emitRm(w, operandSizePrefix(dst.width), dst.width == 64, new int[]{0x0F, 0xAF}, dst, src, symbols, relocs);
    }

    private void unaryGroup(ByteWriter w, MachineInstruction i, int group, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 1); MachineOperand op = i.operands().getFirst(); int width = width(op);
        emitRm(w, operandSizePrefix(width), width == 64, new int[]{width == 8 ? 0xF6 : 0xF7}, group, op, symbols, relocs);
    }

    private void shift(ByteWriter w, MachineInstruction i, int group, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2);
        MachineOperand target = i.operands().get(0); int width = width(target);
        if (i.operands().get(1) instanceof ImmediateOperand immediate) {
            if (width != 8 && width != 16 && width != 32 && width != 64)
                throw new IllegalArgumentException("integer shift target must have width 8, 16, 32 or 64");
            if (immediate.value() < 0 || immediate.value() > 255)
                throw new IllegalArgumentException("immediate shift count must fit an unsigned byte");
            // REL32 is based at the end of disp32; x64 RIP is after the trailing imm8 as well.
            // Adjust the symbol addend for both local layout and the unresolved COFF relocation.
            if (target instanceof MemoryOperand memory && memory.symbol() != null && "rip".equals(memory.base()))
                target = new MemoryOperand(memory.sizeBits(), memory.symbol(), memory.base(), memory.index(), memory.scale(),
                        Math.subtractExact(memory.displacement(), 1));
            emitRm(w, operandSizePrefix(width), width == 64, new int[]{width == 8 ? 0xC0 : 0xC1}, group, target, symbols, relocs);
            w.u8((int) immediate.value());
            return;
        }
        if (!reg(i.operands().get(1)).name.equals("cl")) throw new IllegalArgumentException("variable shift count must use cl");
        emitRm(w, operandSizePrefix(width), width == 64, new int[]{width == 8 ? 0xD2 : 0xD3}, group, target, symbols, relocs);
    }

    private void setcc(ByteWriter w, MachineInstruction i, int opcode, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 1); emitRm(w, 0, false, new int[]{0x0F, opcode}, 0, i.operands().getFirst(), symbols, relocs);
    }

    private static void pushPop(ByteWriter w, MachineInstruction i, int opcode) {
        count(i, 1); Register r = reg(i.operands().getFirst()); if (r.width != 64 || r.kind != Kind.GPR) throw unsupported(i);
        rex(w, false, false, false, r.extended(), false); w.u8(opcode + r.low());
    }

    private void call(ByteWriter w, MachineInstruction i, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 1); MachineOperand op = i.operands().getFirst();
        if (op instanceof RegisterOperand) { emitRm(w, 0, true, new int[]{0xFF}, 2, op, symbols, relocs); return; }
        branch(w, i, 0xE8, null, symbols, relocs);
    }

    private static void branch(ByteWriter w, MachineInstruction i, int first, Integer second, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 1); if (!(i.operands().getFirst() instanceof SymbolOperand s)) throw unsupported(i); w.u8(first); if (second != null) w.u8(second);
        int at = w.size(); Integer target = symbols.get(s.symbol());
        if (target == null) { w.i32(0); relocs.add(new MachineRelocation(at, s.symbol(), MachineRelocationKind.REL32, 0)); }
        else w.i32(target - (at + 4));
    }

    private void sseMove(ByteWriter w, MachineInstruction i, int prefix, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2); MachineOperand dst = i.operands().get(0), src = i.operands().get(1);
        if (dst instanceof RegisterOperand && reg(dst).kind == Kind.XMM) emitRm(w, prefix, false, new int[]{0x0F, 0x10}, reg(dst), src, symbols, relocs);
        else if (src instanceof RegisterOperand && reg(src).kind == Kind.XMM) emitRm(w, prefix, false, new int[]{0x0F, 0x11}, reg(src), dst, symbols, relocs);
        else throw unsupported(i);
    }

    private void movdMovq(ByteWriter w, MachineInstruction i, boolean wide, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2); Register dst = reg(i.operands().get(0)), src = reg(i.operands().get(1));
        if (dst.kind == Kind.XMM && src.kind == Kind.GPR) emitRm(w, 0x66, wide, new int[]{0x0F, 0x6E}, dst, i.operands().get(1), symbols, relocs);
        else if (dst.kind == Kind.GPR && src.kind == Kind.XMM) emitRm(w, 0x66, wide, new int[]{0x0F, 0x7E}, src, i.operands().get(0), symbols, relocs);
        else throw unsupported(i);
    }

    private void sseBinary(ByteWriter w, MachineInstruction i, int prefix, int opcode, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2); Register dst = reg(i.operands().get(0)); if (dst.kind != Kind.XMM) throw unsupported(i);
        emitRm(w, prefix, false, new int[]{0x0F, opcode}, dst, i.operands().get(1), symbols, relocs);
    }

    private void intToFloat(ByteWriter w, MachineInstruction i, int prefix, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2); Register dst = reg(i.operands().get(0)); emitRm(w, prefix, width(i.operands().get(1)) == 64, new int[]{0x0F, 0x2A}, dst, i.operands().get(1), symbols, relocs);
    }

    private void floatToInt(ByteWriter w, MachineInstruction i, int prefix, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        count(i, 2); Register dst = reg(i.operands().get(0)); emitRm(w, prefix, dst.width == 64, new int[]{0x0F, 0x2C}, dst, i.operands().get(1), symbols, relocs);
    }

    private void emitRm(ByteWriter w, int prefix, boolean rexW, int[] opcodes, Register field, MachineOperand rm, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        emitRm(w, prefix, rexW, opcodes, field.code, field.extended(), rm, symbols, relocs);
    }
    private void emitRm(ByteWriter w, int prefix, boolean rexW, int[] opcodes, int field, MachineOperand rm, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        emitRm(w, prefix, rexW, opcodes, field, false, rm, symbols, relocs);
    }
    private void emitRm(ByteWriter w, int prefix, boolean rexW, int[] opcodes, int field, boolean rexR, MachineOperand rm, Map<String, Integer> symbols, List<MachineRelocation> relocs) {
        Address a = Address.of(rm); if (prefix != 0) w.u8(prefix); rex(w, rexW, rexR, a.rexX, a.rexB, a.forceRex);
        for (int opcode : opcodes) w.u8(opcode); w.u8(modRm(a.mod, field, a.rm)); if (a.hasSib) w.u8((a.scaleBits << 6) | (a.indexCode << 3) | a.baseCode);
        int at = w.size();
        if (a.displacementSize == 1) w.u8(a.displacement);
        else if (a.displacementSize == 4) {
            if (a.symbol == null) w.i32(a.displacement);
            else {
                Integer target = symbols.get(a.symbol);
                if (target != null && a.ripRelative) w.i32(target + a.displacement - (at + 4));
                else { w.i32(a.displacement); relocs.add(new MachineRelocation(at, a.symbol, a.ripRelative ? MachineRelocationKind.RIP_REL32 : MachineRelocationKind.ADDR64, 0)); }
            }
        }
    }

    private static int width(MachineOperand operand) {
        if (operand instanceof RegisterOperand) return reg(operand).width;
        if (operand instanceof MemoryOperand m && m.sizeBits() != 0) return m.sizeBits();
        throw new IllegalArgumentException("operand width is not specified: " + operand);
    }
    private static int memoryWidth(MemoryOperand m, MachineInstruction i) { if (m.sizeBits() == 0) throw new IllegalArgumentException("memory width is required in " + i); return m.sizeBits(); }
    private static Register reg(MachineOperand operand) { if (!(operand instanceof RegisterOperand r)) throw new UnsupportedOperationException("register operand required: " + operand); return Register.parse(r.name()); }
    private static void requireGpr(Register r) { if (r.kind != Kind.GPR) throw new IllegalArgumentException("general-purpose register required: " + r.name); }
    private static void count(MachineInstruction i, int n) { if (i.operands().size() != n) throw new IllegalArgumentException(i.mnemonic() + " requires " + n + " operands"); }
    private static UnsupportedOperationException unsupported(MachineInstruction i) { return new UnsupportedOperationException("unsupported x64 instruction form: " + i); }
    private static void rex(ByteWriter w, boolean rw, boolean rr, boolean rx, boolean rb, boolean force) { int value = 0x40 | (rw ? 8 : 0) | (rr ? 4 : 0) | (rx ? 2 : 0) | (rb ? 1 : 0); if (value != 0x40 || force) w.u8(value); }
    private static int modRm(int mod, int reg, int rm) { return (mod << 6) | ((reg & 7) << 3) | (rm & 7); }
    private static int align(int value, int alignment) { return (value + alignment - 1) & -alignment; }
    private static int operandSizePrefix(int width) { return width == 16 ? 0x66 : 0; }
    private static void operandSizePrefix(ByteWriter w, int width) { if (width == 16) w.u8(0x66); }

    private enum Kind { GPR, XMM }
    private record Register(String name, int code, int width, Kind kind, boolean requiresRex) {
        private static final Map<String, Register> ALL = create();
        private static Register parse(String name) { Register r = ALL.get(name.toLowerCase(Locale.ROOT)); if (r == null) throw new IllegalArgumentException("unsupported x64 register: " + name); return r; }
        private int low() { return code & 7; }
        private boolean extended() { return code >= 8; }
        private static Map<String, Register> create() {
            String[] n64={"rax","rcx","rdx","rbx","rsp","rbp","rsi","rdi","r8","r9","r10","r11","r12","r13","r14","r15"};
            String[] n32={"eax","ecx","edx","ebx","esp","ebp","esi","edi","r8d","r9d","r10d","r11d","r12d","r13d","r14d","r15d"};
            String[] n16={"ax","cx","dx","bx","sp","bp","si","di","r8w","r9w","r10w","r11w","r12w","r13w","r14w","r15w"};
            String[] n8={"al","cl","dl","bl","spl","bpl","sil","dil","r8b","r9b","r10b","r11b","r12b","r13b","r14b","r15b"};
            LinkedHashMap<String,Register> map=new LinkedHashMap<>();
            for(int x=0;x<16;x++){map.put(n64[x],new Register(n64[x],x,64,Kind.GPR,false));map.put(n32[x],new Register(n32[x],x,32,Kind.GPR,false));map.put(n16[x],new Register(n16[x],x,16,Kind.GPR,false));map.put(n8[x],new Register(n8[x],x,8,Kind.GPR,x>=4));String q="xmm"+x;map.put(q,new Register(q,x,128,Kind.XMM,false));}
            return Map.copyOf(map);
        }
    }

    private static final class Address {
        final int mod,rm,scaleBits,indexCode,baseCode,displacementSize,displacement; final boolean hasSib,rexX,rexB,forceRex,ripRelative; final String symbol;
        private Address(int mod,int rm,boolean hasSib,int scaleBits,int indexCode,int baseCode,boolean rexX,boolean rexB,boolean forceRex,int displacementSize,int displacement,String symbol,boolean ripRelative){this.mod=mod;this.rm=rm;this.hasSib=hasSib;this.scaleBits=scaleBits;this.indexCode=indexCode;this.baseCode=baseCode;this.rexX=rexX;this.rexB=rexB;this.forceRex=forceRex;this.displacementSize=displacementSize;this.displacement=displacement;this.symbol=symbol;this.ripRelative=ripRelative;}
        static Address of(MachineOperand operand){
            if(operand instanceof RegisterOperand){Register r=reg(operand);return new Address(3,r.low(),false,0,0,0,false,r.extended(),r.requiresRex,0,0,null,false);}
            if(!(operand instanceof MemoryOperand m))throw new UnsupportedOperationException("r/m operand required: "+operand);
            if("rip".equals(m.base()))return new Address(0,5,false,0,0,0,false,false,false,4,m.displacement(),m.symbol(),true);
            Register base=m.base()==null?null:Register.parse(m.base()), index=m.index()==null?null:Register.parse(m.index());
            if((base!=null&&base.kind!=Kind.GPR)||(index!=null&&index.kind!=Kind.GPR))throw new IllegalArgumentException("memory addressing requires GPR registers");
            if(index!=null&&index.low()==4)throw new IllegalArgumentException("rsp/r12 cannot be used as an index register");
            boolean sib=index!=null||base==null||base.low()==4; int mod,ds;
            if(base==null){mod=0;ds=4;}else if(m.displacement()==0&&base.low()!=5){mod=0;ds=0;}else if(m.displacement()>=-128&&m.displacement()<=127){mod=1;ds=1;}else{mod=2;ds=4;}
            int scale=switch(m.scale()){case 1->0;case 2->1;case 4->2;case 8->3;default->throw new IllegalArgumentException("invalid SIB scale: "+m.scale());};
            return new Address(mod,sib?4:base.low(),sib,scale,index==null?4:index.low(),base==null?5:base.low(),index!=null&&index.extended(),base!=null&&base.extended(),false,ds,m.displacement(),m.symbol(),false);
        }
    }

    private static final class ByteWriter {
        final ByteArrayOutputStream out=new ByteArrayOutputStream(); int size(){return out.size();} void align(int a){while((size()&(a-1))!=0)u8(0);} void u8(int v){out.write(v&255);} void i16(int v){u8(v);u8(v>>>8);} void i32(int v){u8(v);u8(v>>>8);u8(v>>>16);u8(v>>>24);} void i64(long v){i32((int)v);i32((int)(v>>>32));} void bytes(byte[] b){out.writeBytes(b);} byte[] toByteArray(){return out.toByteArray();}
    }
}
