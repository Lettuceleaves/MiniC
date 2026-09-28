package minic.compiler.nativebuild.runtime;

import minic.compiler.nativebuild.machine.ImmediateOperand;
import minic.compiler.nativebuild.machine.MachineInstruction;
import minic.compiler.nativebuild.machine.MachineItem;
import minic.compiler.nativebuild.machine.MachineLabel;
import minic.compiler.nativebuild.machine.MachineModule;
import minic.compiler.nativebuild.machine.MachineOperand;
import minic.compiler.nativebuild.machine.MachineSection;
import minic.compiler.nativebuild.machine.MemoryOperand;
import minic.compiler.nativebuild.machine.RegisterOperand;
import minic.compiler.nativebuild.machine.SymbolOperand;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 以结构化 x64 指令提供的 MiniC 最小 Windows 运行时。
 */
public final class MiniCWindowsRuntime {
    private static final String PRINTF = "printf";

    /**
     * 按模块实际需要注入运行时函数，并返回新的不可变机器模块。
     */
    public MachineModule attach(MachineModule module) {
        if (!module.externalSymbols().contains(PRINTF)) {
            return module;
        }
        ArrayList<MachineSection> sections = new ArrayList<>();
        boolean attached = false;
        for (MachineSection section : module.sections()) {
            if (!attached && ".text".equals(section.name())) {
                ArrayList<MachineItem> items = new ArrayList<>(section.items());
                items.addAll(runtimeItems());
                sections.add(new MachineSection(section.name(), section.kind(), section.alignment(), items));
                attached = true;
            } else {
                sections.add(section);
            }
        }
        if (!attached) {
            throw new IllegalArgumentException("printf runtime requires a .text section");
        }
        LinkedHashSet<String> externals = new LinkedHashSet<>(module.externalSymbols());
        externals.remove(PRINTF);
        externals.add("GetStdHandle");
        externals.add("WriteFile");
        return new MachineModule(module.entrySymbol(), sections, List.copyOf(externals));
    }

    private static List<MachineItem> runtimeItems() {
        ArrayList<MachineItem> out = new ArrayList<>();
        emitPutChar(out);
        emitPrintInt64(out);
        emitPrintf(out);
        return List.copyOf(out);
    }

    private static void emitPutChar(List<MachineItem> out) {
        label(out, "minic$putc", true);
        ins(out, "push", r("rbp"));
        ins(out, "mov", r("rbp"), r("rsp"));
        ins(out, "sub", r("rsp"), n(64));
        ins(out, "mov", mem(8, "rbp", -1), r("cl"));
        ins(out, "mov", r("ecx"), n(-11));
        ins(out, "call", s("GetStdHandle"));
        ins(out, "mov", r("rcx"), r("rax"));
        ins(out, "lea", r("rdx"), mem(0, "rbp", -1));
        ins(out, "mov", r("r8d"), n(1));
        ins(out, "lea", r("r9"), mem(0, "rbp", -8));
        ins(out, "mov", mem(64, "rsp", 32), n(0));
        ins(out, "call", s("WriteFile"));
        ins(out, "mov", r("eax"), n(1));
        ins(out, "mov", r("rsp"), r("rbp"));
        ins(out, "pop", r("rbp"));
        ins(out, "ret");
    }

    private static void emitPrintInt64(List<MachineItem> out) {
        label(out, "minic$print_i64", true);
        ins(out, "push", r("rbp"));
        ins(out, "mov", r("rbp"), r("rsp"));
        ins(out, "push", r("rbx"));
        ins(out, "push", r("rsi"));
        ins(out, "push", r("rdi"));
        ins(out, "sub", r("rsp"), n(104));
        ins(out, "mov", r("rbx"), r("rcx"));
        ins(out, "xor", r("esi"), r("esi"));
        ins(out, "cmp", r("rbx"), n(0));
        ins(out, "jge", s("minic$print_i64$digits"));
        ins(out, "mov", r("ecx"), n('-'));
        ins(out, "call", s("minic$putc"));
        ins(out, "add", r("esi"), r("eax"));
        ins(out, "neg", r("rbx"));
        label(out, "minic$print_i64$digits", false);
        ins(out, "xor", r("edi"), r("edi"));
        label(out, "minic$print_i64$divide", false);
        ins(out, "mov", r("rax"), r("rbx"));
        ins(out, "xor", r("edx"), r("edx"));
        ins(out, "mov", r("ecx"), n(10));
        ins(out, "idiv", r("rcx"));
        ins(out, "add", r("edx"), n('0'));
        ins(out, "mov", indexed(8, "rsp", "rdi", 1, 32), r("dl"));
        ins(out, "add", r("rdi"), n(1));
        ins(out, "mov", r("rbx"), r("rax"));
        ins(out, "cmp", r("rbx"), n(0));
        ins(out, "jne", s("minic$print_i64$divide"));
        label(out, "minic$print_i64$write", false);
        ins(out, "sub", r("rdi"), n(1));
        ins(out, "movzx", r("ecx"), indexed(8, "rsp", "rdi", 1, 32));
        ins(out, "call", s("minic$putc"));
        ins(out, "add", r("esi"), r("eax"));
        ins(out, "cmp", r("rdi"), n(0));
        ins(out, "jne", s("minic$print_i64$write"));
        ins(out, "mov", r("eax"), r("esi"));
        ins(out, "add", r("rsp"), n(104));
        ins(out, "pop", r("rdi"));
        ins(out, "pop", r("rsi"));
        ins(out, "pop", r("rbx"));
        ins(out, "pop", r("rbp"));
        ins(out, "ret");
    }

    private static void emitPrintf(List<MachineItem> out) {
        label(out, PRINTF, true);
        ins(out, "push", r("rbp"));
        ins(out, "mov", r("rbp"), r("rsp"));
        for (String register : List.of("rbx", "rsi", "rdi", "r12", "r13", "r14", "r15")) {
            ins(out, "push", r(register));
        }
        ins(out, "sub", r("rsp"), n(88));
        ins(out, "mov", r("rbx"), r("rcx"));
        ins(out, "mov", mem(64, "rbp", -64), r("rdx"));
        ins(out, "mov", mem(64, "rbp", -72), r("r8"));
        ins(out, "mov", mem(64, "rbp", -80), r("r9"));
        ins(out, "xor", r("esi"), r("esi"));
        ins(out, "xor", r("r12d"), r("r12d"));

        label(out, "printf$loop", false);
        ins(out, "movzx", r("eax"), mem(8, "rbx", 0));
        ins(out, "cmp", r("eax"), n(0));
        ins(out, "je", s("printf$done"));
        ins(out, "add", r("rbx"), n(1));
        ins(out, "cmp", r("eax"), n('%'));
        ins(out, "je", s("printf$percent"));
        ins(out, "mov", r("ecx"), r("eax"));
        ins(out, "call", s("minic$putc"));
        ins(out, "add", r("esi"), r("eax"));
        ins(out, "jmp", s("printf$loop"));

        label(out, "printf$percent", false);
        ins(out, "movzx", r("eax"), mem(8, "rbx", 0));
        ins(out, "cmp", r("eax"), n(0));
        ins(out, "je", s("printf$trailing_percent"));
        ins(out, "add", r("rbx"), n(1));
        ins(out, "cmp", r("eax"), n('%'));
        ins(out, "je", s("printf$literal"));
        ins(out, "cmp", r("eax"), n('l'));
        ins(out, "je", s("printf$long"));
        ins(out, "cmp", r("eax"), n('d'));
        ins(out, "je", s("printf$int"));
        ins(out, "cmp", r("eax"), n('i'));
        ins(out, "je", s("printf$int"));
        ins(out, "cmp", r("eax"), n('c'));
        ins(out, "je", s("printf$char"));
        ins(out, "cmp", r("eax"), n('s'));
        ins(out, "je", s("printf$string"));
        ins(out, "mov", r("edi"), r("eax"));
        ins(out, "mov", r("ecx"), n('%'));
        ins(out, "call", s("minic$putc"));
        ins(out, "add", r("esi"), r("eax"));
        ins(out, "mov", r("ecx"), r("edi"));
        ins(out, "call", s("minic$putc"));
        ins(out, "add", r("esi"), r("eax"));
        ins(out, "jmp", s("printf$loop"));

        label(out, "printf$long", false);
        ins(out, "movzx", r("eax"), mem(8, "rbx", 0));
        ins(out, "cmp", r("eax"), n('d'));
        ins(out, "jne", s("printf$literal"));
        ins(out, "add", r("rbx"), n(1));
        ins(out, "call", s("printf$get_arg"));
        ins(out, "mov", r("rcx"), r("rax"));
        ins(out, "call", s("minic$print_i64"));
        ins(out, "add", r("esi"), r("eax"));
        ins(out, "jmp", s("printf$loop"));

        label(out, "printf$int", false);
        ins(out, "call", s("printf$get_arg"));
        ins(out, "movsxd", r("rax"), r("eax"));
        ins(out, "mov", r("rcx"), r("rax"));
        ins(out, "call", s("minic$print_i64"));
        ins(out, "add", r("esi"), r("eax"));
        ins(out, "jmp", s("printf$loop"));

        label(out, "printf$char", false);
        ins(out, "call", s("printf$get_arg"));
        ins(out, "mov", r("ecx"), r("eax"));
        ins(out, "call", s("minic$putc"));
        ins(out, "add", r("esi"), r("eax"));
        ins(out, "jmp", s("printf$loop"));

        label(out, "printf$string", false);
        ins(out, "call", s("printf$get_arg"));
        ins(out, "mov", r("rdi"), r("rax"));
        label(out, "printf$string_loop", false);
        ins(out, "movzx", r("ecx"), mem(8, "rdi", 0));
        ins(out, "cmp", r("ecx"), n(0));
        ins(out, "je", s("printf$loop"));
        ins(out, "add", r("rdi"), n(1));
        ins(out, "call", s("minic$putc"));
        ins(out, "add", r("esi"), r("eax"));
        ins(out, "jmp", s("printf$string_loop"));

        label(out, "printf$literal", false);
        ins(out, "mov", r("ecx"), r("eax"));
        ins(out, "call", s("minic$putc"));
        ins(out, "add", r("esi"), r("eax"));
        ins(out, "jmp", s("printf$loop"));
        label(out, "printf$trailing_percent", false);
        ins(out, "mov", r("ecx"), n('%'));
        ins(out, "call", s("minic$putc"));
        ins(out, "add", r("esi"), r("eax"));

        label(out, "printf$done", false);
        ins(out, "mov", r("eax"), r("esi"));
        ins(out, "add", r("rsp"), n(88));
        for (String register : List.of("r15", "r14", "r13", "r12", "rdi", "rsi", "rbx")) {
            ins(out, "pop", r(register));
        }
        ins(out, "pop", r("rbp"));
        ins(out, "ret");

        label(out, "printf$get_arg", false);
        ins(out, "cmp", r("r12d"), n(0));
        ins(out, "je", s("printf$arg0"));
        ins(out, "cmp", r("r12d"), n(1));
        ins(out, "je", s("printf$arg1"));
        ins(out, "cmp", r("r12d"), n(2));
        ins(out, "je", s("printf$arg2"));
        ins(out, "mov", r("rax"), r("r12"));
        ins(out, "sub", r("rax"), n(3));
        ins(out, "mov", r("rax"), indexed(64, "rbp", "rax", 8, 48));
        ins(out, "add", r("r12"), n(1));
        ins(out, "ret");
        label(out, "printf$arg0", false);
        ins(out, "mov", r("rax"), mem(64, "rbp", -64));
        ins(out, "add", r("r12"), n(1));
        ins(out, "ret");
        label(out, "printf$arg1", false);
        ins(out, "mov", r("rax"), mem(64, "rbp", -72));
        ins(out, "add", r("r12"), n(1));
        ins(out, "ret");
        label(out, "printf$arg2", false);
        ins(out, "mov", r("rax"), mem(64, "rbp", -80));
        ins(out, "add", r("r12"), n(1));
        ins(out, "ret");
    }

    private static RegisterOperand r(String name) {
        return new RegisterOperand(name);
    }

    private static ImmediateOperand n(long value) {
        return new ImmediateOperand(value);
    }

    private static SymbolOperand s(String name) {
        return new SymbolOperand(name);
    }

    private static MemoryOperand mem(int size, String base, int displacement) {
        return MemoryOperand.base(size, base, displacement);
    }

    private static MemoryOperand indexed(int size, String base, String index, int scale, int displacement) {
        return new MemoryOperand(size, null, base, index, scale, displacement);
    }

    private static void label(List<MachineItem> out, String name, boolean global) {
        out.add(new MachineLabel(name, global));
    }

    private static void ins(List<MachineItem> out, String mnemonic, MachineOperand... operands) {
        out.add(new MachineInstruction(mnemonic, operands));
    }
}
