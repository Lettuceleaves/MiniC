package minic.uilocal;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiStageVisualDto;

import java.util.ArrayList;
import java.util.List;

final class MiniCVisualSourceRows {
    private MiniCVisualSourceRows() {
    }

    static List<HBox> rows(String fallbackSource, UiStageVisualDto visual) {
        String source = visual == null || visual.sourceText().isBlank()
                ? fallbackSource
                : visual.sourceText();
        String[] lines = source.split("\\R", -1);
        UiLexerTokenVisualDto activeToken = activeSourceToken(visual);
        ArrayList<HBox> rows = new ArrayList<>();
        int offset = 0;
        for (int index = 0; index < lines.length; index++) {
            HBox row = new HBox();
            row.getStyleClass().add("lexer-row");
            Label number = new Label(Integer.toString(index + 1));
            number.getStyleClass().add("lexer-line-number");
            HBox text = sourceLineFlow(lines[index], offset, activeToken);
            offset += lines[index].length() + lineSeparatorLength(source, offset + lines[index].length());
            row.getChildren().addAll(number, text);
            rows.add(row);
        }
        return rows;
    }

    private static UiLexerTokenVisualDto activeSourceToken(UiStageVisualDto visual) {
        if (visual == null) {
            return null;
        }
        return visual.lexerTokens().stream()
                .filter(UiLexerTokenVisualDto::active)
                .filter(token -> token.startOffset() >= 0 && token.endOffset() > token.startOffset())
                .findFirst()
                .orElse(null);
    }

    private static int lineSeparatorLength(String source, int separatorOffset) {
        if (separatorOffset >= source.length()) {
            return 0;
        }
        if (source.charAt(separatorOffset) == '\r'
                && separatorOffset + 1 < source.length()
                && source.charAt(separatorOffset + 1) == '\n') {
            return 2;
        }
        return 1;
    }

    private static HBox sourceLineFlow(String line, int lineStartOffset, UiLexerTokenVisualDto activeToken) {
        HBox flow = new HBox(0);
        flow.getStyleClass().add("source-flow-line");
        if (line.isEmpty()) {
            Label blank = new Label(" ");
            blank.getStyleClass().add("source-flow-text");
            flow.getChildren().add(blank);
            return flow;
        }
        for (int index = 0; index < line.length(); index++) {
            Label text = new Label(line.substring(index, index + 1));
            text.getStyleClass().add("source-flow-text");
            if (isMaskedSourceOffset(lineStartOffset + index, activeToken)) {
                text.getStyleClass().add("source-token-mask");
            }
            flow.getChildren().add(text);
        }
        return flow;
    }

    private static boolean isMaskedSourceOffset(int offset, UiLexerTokenVisualDto activeToken) {
        return activeToken != null
                && offset >= activeToken.startOffset()
                && offset < activeToken.endOffset();
    }
}
