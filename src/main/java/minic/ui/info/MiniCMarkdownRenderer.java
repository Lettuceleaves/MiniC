package minic.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MiniCMarkdownRenderer {
    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+)$");
    private static final Pattern UNORDERED_LIST = Pattern.compile("^[-*]\\s+(.+)$");
    private static final Pattern ORDERED_LIST = Pattern.compile("^\\d+[.)]\\s+(.+)$");

    VBox render(String markdown) {
        VBox content = new VBox(10);
        content.getStyleClass().add("info-markdown");
        List<String> paragraph = new ArrayList<>();
        boolean inCodeBlock = false;
        StringBuilder codeBlock = new StringBuilder();
        for (String rawLine : normalize(markdown).split("\n", -1)) {
            String line = rawLine.stripTrailing();
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    addCodeBlock(content, codeBlock.toString());
                    codeBlock.setLength(0);
                    inCodeBlock = false;
                } else {
                    flushParagraph(content, paragraph);
                    inCodeBlock = true;
                }
                continue;
            }
            if (inCodeBlock) {
                codeBlock.append(rawLine).append('\n');
                continue;
            }
            if (trimmed.isBlank()) {
                flushParagraph(content, paragraph);
                continue;
            }
            Matcher heading = HEADING.matcher(trimmed);
            if (heading.matches()) {
                flushParagraph(content, paragraph);
                addHeading(content, heading.group(1).length(), heading.group(2));
                continue;
            }
            Matcher unordered = UNORDERED_LIST.matcher(trimmed);
            Matcher ordered = ORDERED_LIST.matcher(trimmed);
            if (unordered.matches()) {
                flushParagraph(content, paragraph);
                addListItem(content, "•", unordered.group(1));
                continue;
            }
            if (ordered.matches()) {
                flushParagraph(content, paragraph);
                addListItem(content, "•", ordered.group(1));
                continue;
            }
            paragraph.add(trimmed);
        }
        if (inCodeBlock) {
            addCodeBlock(content, codeBlock.toString());
        }
        flushParagraph(content, paragraph);
        return content;
    }

    private static String normalize(String markdown) {
        return markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void addHeading(VBox content, int level, String text) {
        Label heading = new Label(text.strip());
        heading.getStyleClass().add("info-heading-" + level);
        heading.setWrapText(true);
        heading.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(heading);
    }

    private static void addCodeBlock(VBox content, String code) {
        Label block = new Label(code.stripTrailing());
        block.getStyleClass().add("info-code-block");
        block.setWrapText(true);
        block.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(block);
    }

    private static void addListItem(VBox content, String marker, String text) {
        HBox item = new HBox(8);
        item.getStyleClass().add("info-list-item");
        Label bullet = new Label(marker);
        bullet.getStyleClass().add("info-list-marker");
        TextFlow body = inlineFlow(text);
        HBox.setHgrow(body, Priority.ALWAYS);
        item.getChildren().addAll(bullet, body);
        content.getChildren().add(item);
    }

    private static void flushParagraph(VBox content, List<String> paragraph) {
        if (paragraph.isEmpty()) {
            return;
        }
        TextFlow flow = inlineFlow(String.join(" ", paragraph));
        flow.getStyleClass().add("info-paragraph");
        content.getChildren().add(flow);
        paragraph.clear();
    }

    private static TextFlow inlineFlow(String markdown) {
        TextFlow flow = new TextFlow();
        flow.setMaxWidth(Double.MAX_VALUE);
        int index = 0;
        while (index < markdown.length()) {
            InlineToken token = nextToken(markdown, index);
            if (token.start > index) {
                addText(flow, markdown.substring(index, token.start), "info-text");
            }
            if (token.closed()) {
                addText(flow, token.value, token.styleClass);
                index = token.end;
            } else {
                addText(flow, markdown.substring(token.start), "info-text");
                index = markdown.length();
            }
        }
        return flow;
    }

    private static InlineToken nextToken(String markdown, int from) {
        int codeStart = markdown.indexOf('`', from);
        int strongStart = markdown.indexOf("**", from);
        if (codeStart < 0 && strongStart < 0) {
            return new InlineToken(markdown.length(), markdown.length(), "", "info-text");
        }
        if (codeStart >= 0 && (strongStart < 0 || codeStart < strongStart)) {
            int end = markdown.indexOf('`', codeStart + 1);
            return end < 0
                    ? new InlineToken(codeStart, markdown.length(), "", "info-text")
                    : new InlineToken(codeStart, end + 1, markdown.substring(codeStart + 1, end), "info-inline-code");
        }
        int end = markdown.indexOf("**", strongStart + 2);
        return end < 0
                ? new InlineToken(strongStart, markdown.length(), "", "info-text")
                : new InlineToken(strongStart, end + 2, markdown.substring(strongStart + 2, end), "info-strong");
    }

    private static void addText(TextFlow flow, String value, String styleClass) {
        if (value.isEmpty()) {
            return;
        }
        Text text = new Text(value);
        text.getStyleClass().add(styleClass);
        flow.getChildren().add(text);
    }

    private record InlineToken(int start, int end, String value, String styleClass) {
        boolean closed() {
            return end > start && !value.isEmpty();
        }
    }
}
