package minic.uilocal.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lightweight highlighter for Windows x64 assembly rows.
 */
public final class MiniCAssemblyTextHighlighter {
    private static final Set<String> MNEMONICS = Set.of(
            "mov", "movsx", "movzx", "lea", "push", "pop", "sub", "add", "imul", "idiv",
            "cqo", "xor", "or", "and", "not", "neg", "shl", "sar", "cmp", "sete",
            "setne", "setl", "setle", "setg", "setge", "jmp", "je", "jne", "jl",
            "jle", "jg", "jge", "call", "ret", "leave", "test"
    );
    private static final Set<String> REGISTERS = Set.of(
            "rax", "rbx", "rcx", "rdx", "rsi", "rdi", "rbp", "rsp",
            "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15",
            "eax", "ebx", "ecx", "edx", "esi", "edi", "ebp", "esp",
            "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d",
            "ax", "bx", "cx", "dx", "al", "bl", "cl", "dl",
            "xmm0", "xmm1", "xmm2", "xmm3", "xmm4", "xmm5"
    );
    private static final Set<String> DIRECTIVES = Set.of(
            ".text", ".data", ".code", ".const", "text", "data", "code", "const",
            "proc", "endp", "public", "extern", "extrn", "segment", "ends",
            "db", "dw", "dd", "dq", "flat"
    );
    private static final Set<String> TYPE_WORDS = Set.of(
            "qword", "dword", "word", "byte", "ptr", "offset"
    );

    public List<MiniCStyledTextSegment> highlight(String line) {
        String text = line == null || line.isEmpty() ? " " : line;
        int commentStart = text.indexOf(';');
        if (commentStart < 0) {
            return MiniCLineTokenHighlighter.highlight(text, this::roleFor);
        }
        ArrayList<MiniCStyledTextSegment> segments = new ArrayList<>(
                MiniCLineTokenHighlighter.highlight(text.substring(0, commentStart), this::roleFor)
        );
        MiniCLineTokenHighlighter.add(segments, text.substring(commentStart), MiniCTextStyleRole.CODE_COMMENT);
        return List.copyOf(segments);
    }

    private MiniCTextStyleRole roleFor(String token, int startOffset, String fullLine) {
        String normalized = token.toLowerCase();
        if (DIRECTIVES.contains(normalized)) {
            return MiniCTextStyleRole.CODE_DIRECTIVE;
        }
        if (startsLabel(token, startOffset, fullLine) || token.startsWith("$") || token.startsWith(".")) {
            return MiniCTextStyleRole.CODE_LABEL;
        }
        if (MNEMONICS.contains(normalized)) {
            return MiniCTextStyleRole.CODE_FUNCTION;
        }
        if (REGISTERS.contains(normalized)) {
            return MiniCTextStyleRole.CODE_REGISTER;
        }
        if (TYPE_WORDS.contains(normalized)) {
            return MiniCTextStyleRole.CODE_TYPE;
        }
        if (isNumber(token)) {
            return MiniCTextStyleRole.CODE_LITERAL;
        }
        if (followsCallTarget(token, startOffset, fullLine)) {
            return MiniCTextStyleRole.CODE_FUNCTION;
        }
        if (isIdentifier(token)) {
            return MiniCTextStyleRole.CODE_VARIABLE;
        }
        if (isPunctuation(token)) {
            return MiniCTextStyleRole.CODE_PUNCTUATION;
        }
        return MiniCTextStyleRole.CODE_OPERATOR;
    }

    private boolean startsLabel(String token, int startOffset, String fullLine) {
        int index = startOffset + token.length();
        return index < fullLine.length() && fullLine.charAt(index) == ':';
    }

    private boolean isIdentifier(String token) {
        return token.matches("[A-Za-z_.$][A-Za-z0-9_.$]*");
    }

    private boolean isNumber(String token) {
        return token.matches("[-+]?0x[0-9A-Fa-f]+|[-+]?[0-9]+(?:\\.[0-9]+)?");
    }

    private boolean followsCallTarget(String token, int startOffset, String fullLine) {
        String prefix = fullLine.substring(0, Math.max(0, startOffset)).stripTrailing().toLowerCase();
        return isIdentifier(token) && prefix.endsWith("call");
    }

    private boolean isPunctuation(String token) {
        return token.length() == 1 && "(),:[]{}".contains(token);
    }
}
