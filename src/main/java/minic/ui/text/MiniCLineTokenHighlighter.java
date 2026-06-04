package minic.ui.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MiniCLineTokenHighlighter {
    private static final Pattern TOKEN = Pattern.compile(
            "[-+]?0x[0-9A-Fa-f]+|[-+]?[0-9]+(?:\\.[0-9]+)?|[%@$&.]?[A-Za-z0-9_][A-Za-z0-9_.$]*|\\S"
    );

    private MiniCLineTokenHighlighter() {}

    static List<MiniCStyledTextSegment> highlight(String line, TokenClassifier classifier) {
        String text = line == null || line.isEmpty() ? " " : line;
        ArrayList<MiniCStyledTextSegment> segments = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                add(segments, text.substring(cursor, matcher.start()), MiniCTextStyleRole.CODE_PLAIN);
            }
            String token = matcher.group();
            add(segments, token, classifier.roleFor(token, matcher.start(), text));
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            add(segments, text.substring(cursor), MiniCTextStyleRole.CODE_PLAIN);
        }
        return List.copyOf(segments);
    }

    static void add(List<MiniCStyledTextSegment> segments, String text, MiniCTextStyleRole role) {
        if (text.isEmpty()) {
            return;
        }
        if (!segments.isEmpty()) {
            MiniCStyledTextSegment previous = segments.getLast();
            if (previous.role() == role) {
                segments.set(segments.size() - 1, new MiniCStyledTextSegment(previous.text() + text, role));
                return;
            }
        }
        segments.add(new MiniCStyledTextSegment(text, role));
    }

    interface TokenClassifier {
        MiniCTextStyleRole roleFor(String token, int startOffset, String fullLine);
    }
}
