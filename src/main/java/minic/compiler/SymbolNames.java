package minic.compiler;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts bound identifiers at presentation boundaries without changing executable symbols. */
public final class SymbolNames {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z_0-9$]*");

    private SymbolNames() {}

    public static String displayText(String text, Map<String, String> displayNames) {
        if (text == null || displayNames.isEmpty()) return text;
        return IDENTIFIER.matcher(text).replaceAll(match ->
                Matcher.quoteReplacement(displayNames.getOrDefault(match.group(), match.group())));
    }
}
