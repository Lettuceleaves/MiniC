package minic.ui;

/**
 * 代码编辑器可打印字符输入规则。
 */
final class MiniCEditorTyping {
    private MiniCEditorTyping() {
    }

    static EditResult type(String source, int selectionStart, int selectionEnd, String text) {
        int start = Math.max(0, Math.min(selectionStart, source.length()));
        int end = Math.max(start, Math.min(selectionEnd, source.length()));
        return switch (text) {
            case "(" -> wrapOrInsert(source, start, end, "(", ")");
            case "[" -> wrapOrInsert(source, start, end, "[", "]");
            case "{" -> wrapOrInsert(source, start, end, "{", "}");
            case "\"", "'" -> quoteOrSkip(source, start, end, text);
            case ")", "]", "}" -> skipOrInsert(source, start, end, text);
            default -> replace(source, start, end, text, start + text.length());
        };
    }

    static EditResult backspace(String source, int selectionStart, int selectionEnd) {
        int start = Math.max(0, Math.min(selectionStart, source.length()));
        int end = Math.max(start, Math.min(selectionEnd, source.length()));
        if (end > start) {
            return replace(source, start, end, "", start);
        }
        if (start > 0 && start < source.length() && isEmptyPair(source.charAt(start - 1), source.charAt(start))) {
            return replace(source, start - 1, start + 1, "", start - 1);
        }
        if (start > 0) {
            return replace(source, start - 1, start, "", start - 1);
        }
        return new EditResult(source, start, end, "", start, start);
    }

    private static EditResult wrapOrInsert(String source, int start, int end, String opening, String closing) {
        if (end > start) {
            String selected = source.substring(start, end);
            return replace(source, start, end, opening + selected + closing, end + opening.length());
        }
        return replace(source, start, end, opening + closing, start + opening.length());
    }

    private static EditResult skipOrInsert(String source, int start, int end, String closing) {
        if (start == end && source.startsWith(closing, start)) {
            return new EditResult(source, start, end, "", start + closing.length(), start + closing.length());
        }
        return replace(source, start, end, closing, start + closing.length());
    }

    private static EditResult quoteOrSkip(String source, int start, int end, String quote) {
        if (start == end && source.startsWith(quote, start)) {
            return new EditResult(source, start, end, "", start + quote.length(), start + quote.length());
        }
        return wrapOrInsert(source, start, end, quote, quote);
    }

    private static EditResult replace(String source, int start, int end, String replacement, int caret) {
        return new EditResult(source.substring(0, start) + replacement + source.substring(end), start, end, replacement, caret, caret);
    }

    private static boolean isEmptyPair(char opening, char closing) {
        return (opening == '(' && closing == ')')
                || (opening == '[' && closing == ']')
                || (opening == '{' && closing == '}')
                || (opening == '"' && closing == '"')
                || (opening == '\'' && closing == '\'');
    }

    record EditResult(String source, int replaceStart, int replaceEnd, String replacement, int selectionStart, int selectionEnd) {
    }
}
