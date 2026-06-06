package minic.uilocal;

import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.StyleClassedTextArea;

final class MiniCEditorFormatter {
    private static final String TAB_TEXT = "    ";

    private final StyleClassedTextArea input;

    MiniCEditorFormatter(StyleClassedTextArea input) {
        this.input = input;
    }

    void handleTypedText(KeyEvent event) {
        String text = event.getCharacter();
        if (text == null || text.isEmpty() || text.charAt(0) < 32 || event.isControlDown() || event.isAltDown()) {
            return;
        }
        applyEdit(MiniCEditorTyping.type(
                input.getText(),
                input.getSelection().getStart(),
                input.getSelection().getEnd(),
                text
        ));
        event.consume();
    }

    void insertTab() {
        input.replaceSelection(TAB_TEXT);
    }

    void deleteBackward() {
        applyEdit(MiniCEditorTyping.backspace(
                input.getText(),
                input.getSelection().getStart(),
                input.getSelection().getEnd()
        ));
    }

    void insertNewlineWithIndent() {
        int caret = input.getCaretPosition();
        String source = input.getText();
        int lineStart = source.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        String lineBefore = source.substring(lineStart, caret);
        String formatted = formatLine(lineBefore);
        int formatDelta = formatted.length() - lineBefore.length();
        String currentIndent = leadingWhitespace(formatted);
        boolean afterOpeningBrace = !formatted.isEmpty() && formatted.charAt(formatted.length() - 1) == '{';
        String afterCaret = source.substring(caret);
        boolean beforeClosingBrace = !afterCaret.isEmpty() && afterCaret.charAt(0) == '}';
        boolean needsClosingBrace = afterOpeningBrace && !beforeClosingBrace
                && !braceBalancedAfter(source, caret);
        String insertion;
        int cursorOffset;
        if (afterOpeningBrace && (beforeClosingBrace || needsClosingBrace)) {
            String innerIndent = currentIndent + TAB_TEXT;
            String closingPart = needsClosingBrace ? "\n" + currentIndent + "}" : "\n" + currentIndent;
            insertion = "\n" + innerIndent + closingPart;
            cursorOffset = 1 + innerIndent.length();
        } else {
            String nextIndent = currentIndent + (afterOpeningBrace ? TAB_TEXT : "");
            insertion = "\n" + nextIndent;
            cursorOffset = insertion.length();
        }
        input.replaceText(lineStart, caret, formatted + insertion);
        input.moveTo(lineStart + formatted.length() + cursorOffset);
    }

    private void applyEdit(MiniCEditorTyping.EditResult result) {
        if (!result.replacement().isEmpty() || result.replaceStart() != result.replaceEnd()) {
            input.replaceText(result.replaceStart(), result.replaceEnd(), result.replacement());
        }
        input.selectRange(result.selectionStart(), result.selectionEnd());
    }

    private boolean braceBalancedAfter(String source, int from) {
        int depth = 1;
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
            if (depth == 0) {
                return true;
            }
        }
        return false;
    }

    private String formatLine(String line) {
        String indent = leadingWhitespace(line);
        String content = line.substring(indent.length()).stripTrailing();
        if (content.isEmpty()) {
            return indent;
        }
        return indent + formatOutsideLiterals(content);
    }

    private String formatOutsideLiterals(String text) {
        StringBuilder result = new StringBuilder();
        StringBuilder segment = new StringBuilder();
        char quote = 0;
        boolean escaping = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (quote != 0) {
                result.append(value);
                if (escaping) {
                    escaping = false;
                } else if (value == '\\') {
                    escaping = true;
                } else if (value == quote) {
                    quote = 0;
                }
                continue;
            }
            if (value == '"' || value == '\'') {
                result.append(formatCodeSegment(segment.toString()));
                segment.setLength(0);
                result.append(value);
                quote = value;
            } else {
                segment.append(value);
            }
        }
        result.append(formatCodeSegment(segment.toString()));
        return result.toString().replaceAll("\\s+", " ").trim();
    }

    private String formatCodeSegment(String content) {
        return content
                .replaceAll("\\s+([,;\\)\\]\\}])", "$1")
                .replaceAll("([\\(\\[\\{])\\s+", "$1")
                .replaceAll("\\s*([+\\-*/%<>=!&|]=?|==|!=|<=|>=|&&|\\|\\|)\\s*", " $1 ")
                .replaceAll("\\s*,\\s*", ", ")
                .replaceAll("\\)\\s*\\{", ") {")
                .replaceAll("\\b(if|for|while)\\s*\\(", "$1 (")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String leadingWhitespace(String text) {
        int index = 0;
        while (index < text.length()) {
            char value = text.charAt(index);
            if (value != ' ' && value != '\t') {
                break;
            }
            index++;
        }
        return text.substring(0, index);
    }
}
