package minic.uilocal;

import minic.uiapi.UiLexerTokenVisualDto;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MiniCCompletionSuggester {
    private static final List<String> KEYWORDS = List.of(
            "bool", "char", "int", "long", "float", "double", "extern", "struct",
            "return", "if", "else", "while", "for", "break", "continue", "true", "false", "null"
    );
    private static final List<String> COMMON_EXTERNALS = List.of(
            "printf", "scanf", "puts", "getchar", "putchar", "malloc", "free", "memset", "memcpy", "strlen"
    );
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");
    private static final Pattern DECLARED_NAME_PATTERN = Pattern.compile(
            "\\b(?:extern\\s+)?(?:bool|char|int|long|float|double|struct\\s+[A-Za-z_][A-Za-z0-9_]*)(?:\\s*\\*)*\\s+([A-Za-z_][A-Za-z0-9_]*)"
    );

    private MiniCCompletionSuggester() {
    }

    static List<String> suggestions(String prefix, String source, List<UiLexerTokenVisualDto> tokens) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.addAll(KEYWORDS);
        candidates.addAll(extractDeclaredNames(source, tokens));
        candidates.addAll(COMMON_EXTERNALS);
        return candidates.stream()
                .filter(candidate -> prefix.isEmpty() || candidate.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .filter(candidate -> !candidate.equals(prefix))
                .limit(9)
                .toList();
    }

    private static Set<String> extractDeclaredNames(String source, List<UiLexerTokenVisualDto> tokens) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        tokens.stream()
                .filter(token -> "IDENTIFIER".equals(token.kind()))
                .map(UiLexerTokenVisualDto::text)
                .forEach(names::add);
        Matcher declaredMatcher = DECLARED_NAME_PATTERN.matcher(source);
        while (declaredMatcher.find()) {
            names.add(declaredMatcher.group(1));
        }
        Matcher identifierMatcher = IDENTIFIER_PATTERN.matcher(source);
        while (identifierMatcher.find()) {
            String identifier = identifierMatcher.group();
            if (!KEYWORDS.contains(identifier)) {
                names.add(identifier);
            }
        }
        return names;
    }
}
