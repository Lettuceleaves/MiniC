package minic.uilocal.text;

import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight highlighter for explanatory prose that embeds small code fragments.
 */
public final class MiniCExplanationTextHighlighter {
    private static final Pattern TOKEN = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"
                    + "|[-+]?0x[0-9A-Fa-f]+|[-+]?[0-9]+(?:\\.[0-9]+)?"
                    + "|==|!=|<=|>=|->|&&|\\|\\|"
                    + "|[%@$&.]?[A-Za-z0-9_][A-Za-z0-9_.$]*"
                    + "|[-+*/%=&|!<>^~.,:;()\\[\\]{}@]"
    );
    private static final Pattern NUMBER = Pattern.compile("[-+]?0x[0-9A-Fa-f]+|[-+]?[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.$]*");
    private static final Pattern PREFIXED_IDENTIFIER = Pattern.compile("[%@$&.][A-Za-z0-9_][A-Za-z0-9_.$]*");
    private static final Pattern OPERATOR = Pattern.compile("==|!=|<=|>=|->|&&|\\|\\||[-+*/%=&|!<>^~@]");
    private static final Pattern PUNCTUATION = Pattern.compile("[.,:;()\\[\\]{}]");

    private static final Set<String> C_CONTROL_KEYWORDS = Set.of(
            "return", "if", "else", "while", "for", "break", "continue",
            "switch", "case", "default", "do", "goto"
    );
    private static final Set<String> C_KEYWORDS = Set.of(
            "extern", "sizeof"
    );
    private static final Set<String> TYPE_NAMES = Set.of(
            "void", "bool", "char", "int", "long", "float", "double", "struct",
            "ir", "asm", "ast", "token", "qword", "dword", "word", "byte", "ptr", "offset"
    );
    private static final Set<String> LITERALS = Set.of("true", "false", "null", "nullptr");
    private static final Set<String> IR_KEYWORDS = Set.of(
            "function", "block", "declare", "check_initialized", "address_of",
            "load", "store", "load_ptr", "store_ptr", "element_address", "field_address",
            "size", "check_nonzero", "cast", "to", "call", "branch", "jump",
            "add", "subtract", "multiply", "divide", "modulo", "bitwise_and",
            "bitwise_or", "bitwise_xor", "shift_left", "shift_right", "logical_and",
            "logical_or", "equal", "not_equal", "less_than", "less_equal",
            "greater_than", "greater_equal"
    );
    private static final Set<String> ASM_MNEMONICS = Set.of(
            "mov", "movsx", "movzx", "lea", "push", "pop", "sub", "add", "imul", "idiv",
            "cqo", "xor", "or", "and", "not", "neg", "shl", "sar", "cmp", "sete",
            "setne", "setl", "setle", "setg", "setge", "jmp", "je", "jne", "jl",
            "jle", "jg", "jge", "call", "ret", "leave", "test"
    );
    private static final Set<String> ASM_REGISTERS = Set.of(
            "rax", "rbx", "rcx", "rdx", "rsi", "rdi", "rbp", "rsp",
            "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15",
            "eax", "ebx", "ecx", "edx", "esi", "edi", "ebp", "esp",
            "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d",
            "ax", "bx", "cx", "dx", "al", "bl", "cl", "dl",
            "xmm0", "xmm1", "xmm2", "xmm3", "xmm4", "xmm5"
    );

    public List<MiniCStyledTextSegment> highlight(String explanation) {
        String text = explanation == null || explanation.isEmpty() ? " " : explanation;
        java.util.ArrayList<MiniCStyledTextSegment> segments = new java.util.ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                MiniCLineTokenHighlighter.add(segments, text.substring(cursor, matcher.start()), MiniCTextStyleRole.BODY);
            }
            String token = matcher.group();
            MiniCLineTokenHighlighter.add(segments, token, roleFor(token, matcher.start(), text));
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            MiniCLineTokenHighlighter.add(segments, text.substring(cursor), MiniCTextStyleRole.BODY);
        }
        return List.copyOf(segments);
    }

    private MiniCTextStyleRole roleFor(String token, int startOffset, String fullText) {
        String normalized = token.toLowerCase(Locale.ROOT);
        if (isQuoted(token)) {
            return MiniCTextStyleRole.CODE_STRING;
        }
        if (NUMBER.matcher(token).matches() || LITERALS.contains(normalized)) {
            return MiniCTextStyleRole.CODE_LITERAL;
        }
        if (TYPE_NAMES.contains(normalized)) {
            return MiniCTextStyleRole.CODE_TYPE;
        }
        if (C_CONTROL_KEYWORDS.contains(normalized)) {
            return MiniCTextStyleRole.CODE_CONTROL;
        }
        if (C_KEYWORDS.contains(normalized) || IR_KEYWORDS.contains(normalized)) {
            return MiniCTextStyleRole.CODE_KEYWORD;
        }
        if (ASM_MNEMONICS.contains(normalized)) {
            return MiniCTextStyleRole.CODE_FUNCTION;
        }
        if (ASM_REGISTERS.contains(normalized) || isPrefixedIdentifier(token)) {
            if (ASM_REGISTERS.contains(normalized)) {
                return MiniCTextStyleRole.CODE_REGISTER;
            }
            if (token.startsWith(".") || token.startsWith("$")) {
                return MiniCTextStyleRole.CODE_LABEL;
            }
            if (token.startsWith("@")) {
                return MiniCTextStyleRole.CODE_FUNCTION;
            }
            return MiniCTextStyleRole.CODE_VARIABLE;
        }
        if (IDENTIFIER.matcher(token).matches()) {
            if (isLabel(token, startOffset, fullText)) {
                return MiniCTextStyleRole.CODE_LABEL;
            }
            if (isFunctionName(token, startOffset, fullText)) {
                return MiniCTextStyleRole.CODE_FUNCTION;
            }
            if (isLikelyCodeIdentifier(token, startOffset, fullText)) {
                return MiniCTextStyleRole.CODE_VARIABLE;
            }
            return MiniCTextStyleRole.BODY;
        }
        if (PUNCTUATION.matcher(token).matches()) {
            return MiniCTextStyleRole.CODE_PUNCTUATION;
        }
        if (OPERATOR.matcher(token).matches()) {
            return MiniCTextStyleRole.CODE_OPERATOR;
        }
        return MiniCTextStyleRole.BODY;
    }

    private boolean isQuoted(String token) {
        return token.length() >= 2
                && ((token.startsWith("\"") && token.endsWith("\""))
                || (token.startsWith("'") && token.endsWith("'")));
    }

    private boolean isPrefixedIdentifier(String token) {
        return PREFIXED_IDENTIFIER.matcher(token).matches();
    }

    private boolean isLabel(String token, int startOffset, String fullText) {
        int next = nextNonWhitespace(fullText, startOffset + token.length());
        return next >= 0 && fullText.charAt(next) == ':';
    }

    private boolean isFunctionName(String token, int startOffset, String fullText) {
        int next = nextNonWhitespace(fullText, startOffset + token.length());
        return next >= 0 && fullText.charAt(next) == '(';
    }

    private boolean isLikelyCodeIdentifier(String token, int startOffset, String fullText) {
        if (token.length() == 1 || token.indexOf('_') >= 0 || token.indexOf('.') >= 0 || containsDigit(token)) {
            return true;
        }
        int previous = previousNonWhitespace(fullText, startOffset - 1);
        int next = nextNonWhitespace(fullText, startOffset + token.length());
        return isIdentifierBoundary(previous < 0 ? '\0' : fullText.charAt(previous))
                || isIdentifierBoundary(next < 0 ? '\0' : fullText.charAt(next));
    }

    private boolean containsDigit(String token) {
        for (int index = 0; index < token.length(); index++) {
            if (Character.isDigit(token.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private int previousNonWhitespace(String text, int index) {
        for (int i = index; i >= 0; i--) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private int nextNonWhitespace(String text, int index) {
        for (int i = index; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isIdentifierBoundary(char value) {
        return switch (value) {
            case '=', '+', '-', '*', '/', '%', '&', '|', '!', '<', '>', '^', '~', '(', ')', '[', ']', '{', '}', '@' -> true;
            default -> false;
        };
    }
}
