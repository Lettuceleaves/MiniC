package craken.ui.editor.completion;

import craken.compiler.SourceFile;
import craken.compiler.lexer.Lexer;
import craken.compiler.lexer.LexerResult;
import craken.compiler.lexer.token.Token;
import craken.compiler.lexer.token.TokenType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯 Java 的编辑器补全计算。不依赖 JavaFX；输入为完整源码与光标字符偏移，
 * 输出前缀与候选项。标识符提取复用编译器 Lexer，因此注释、字符串字面量里的
 * 名字不会进入候选，词法规则也和编译器保持一致。
 */
public final class CompletionEngine {
    private static final int MAX_CANDIDATES = 200;
    private static final Pattern INCLUDE_ANGLE = Pattern.compile("^\\s*#\\s*include\\s*<([^>]*)$");
    private static final Pattern INCLUDE_QUOTE = Pattern.compile("^\\s*#\\s*include\\s*\"([^\"]*)$");

    private CompletionEngine() {
    }

    /** 计算光标处的补全候选；caretOffset 是源码 Java 字符偏移（0..source.length()）。 */
    public static CompletionResult complete(String source, int caretOffset, CompletionCatalog catalog) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(catalog, "catalog");
        int caret = clampCaret(source, caretOffset);
        CompletionPrefix prefix = prefixAt(source, caret);
        LexicalScan scan = scanContext(source, caret);
        List<CompletionCandidate> candidates = switch (prefix.context()) {
            case IDENTIFIER -> scan.inString() || scan.inChar() || scan.inComment()
                    || onDirectiveLine(source, caret)
                    ? List.of()
                    : identifierCandidates(source, prefix, catalog);
            case INCLUDE_ANGLE -> scan.inComment()
                    ? List.of()
                    : headerCandidates(prefix, catalog.angleHeaders());
            case INCLUDE_QUOTE -> scan.inComment()
                    ? List.of()
                    : headerCandidates(prefix, catalog.quoteHeaders());
        };
        return new CompletionResult(candidates, prefix);
    }

    /** 光标左侧待替换的前缀；不含候选筛选，选中候选项时用它确定替换范围。 */
    public static CompletionPrefix prefixAt(String source, int caretOffset) {
        Objects.requireNonNull(source, "source");
        int caret = clampCaret(source, caretOffset);
        int lineStart = caret;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        String line = source.substring(lineStart, caret);
        Matcher angle = INCLUDE_ANGLE.matcher(line);
        if (angle.matches()) {
            return new CompletionPrefix(
                    lineStart + angle.start(1), caret, angle.group(1),
                    CompletionContext.INCLUDE_ANGLE);
        }
        Matcher quote = INCLUDE_QUOTE.matcher(line);
        if (quote.matches()) {
            return new CompletionPrefix(
                    lineStart + quote.start(1), caret, quote.group(1),
                    CompletionContext.INCLUDE_QUOTE);
        }
        int start = caret;
        while (start > 0 && isIdentifierPart(source.charAt(start - 1))) {
            start--;
        }
        return new CompletionPrefix(
                start, caret, source.substring(start, caret), CompletionContext.IDENTIFIER);
    }

    private static List<CompletionCandidate> identifierCandidates(
            String source, CompletionPrefix prefix, CompletionCatalog catalog) {
        String typed = prefix.text();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        catalog.keywords().stream()
                .filter(keyword -> matches(keyword, typed))
                .forEach(names::add);
        identifierNames(source).stream()
                .filter(name -> !catalog.isKeyword(name))
                .filter(name -> matches(name, typed))
                .forEach(names::add);
        ArrayList<CompletionCandidate> candidates = new ArrayList<>();
        for (String name : names) {
            candidates.add(new CompletionCandidate(
                    name,
                    catalog.isKeyword(name) ? CompletionKind.KEYWORD : CompletionKind.VARIABLE));
            if (candidates.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return List.copyOf(candidates);
    }

    private static List<CompletionCandidate> headerCandidates(
            CompletionPrefix prefix, List<String> headers) {
        return headers.stream()
                .filter(header -> matches(header, prefix.text()))
                .limit(MAX_CANDIDATES)
                .map(header -> new CompletionCandidate(header, CompletionKind.HEADER))
                .toList();
    }

    /** 当前源码里出现过的标识符（不含关键词），按首次出现顺序去重。 */
    static Set<String> identifierNames(String source) {
        Set<Integer> directiveLines = directiveLines(source);
        LexerResult result = new Lexer(new SourceFile("completion", source)).lex();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Token token : result.tokens()) {
            if (token.type() != TokenType.IDENTIFIER) {
                continue;
            }
            if (directiveLines.contains(token.range().startLine())) {
                continue;
            }
            names.add(token.lexeme());
        }
        return names;
    }

    private static boolean matches(String candidate, String prefix) {
        return candidate.startsWith(prefix) && !candidate.equals(prefix);
    }

    private static boolean isIdentifierPart(char character) {
        return character == '_'
                || character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9';
    }

    private static int clampCaret(String source, int caretOffset) {
        if (caretOffset < 0 || caretOffset > source.length()) {
            throw new IllegalArgumentException("caret offset out of bounds: " + caretOffset);
        }
        return caretOffset;
    }

    private static boolean onDirectiveLine(String source, int caret) {
        int lineStart = caret;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        int first = lineStart;
        while (first < caret && isHorizontalWhitespace(source.charAt(first))) {
            first++;
        }
        return first < source.length() && source.charAt(first) == '#';
    }

    /** 以 {@code #} 开头的预处理指令行的 1-based 行号集合。 */
    private static Set<Integer> directiveLines(String source) {
        Set<Integer> lines = new HashSet<>();
        int lineNumber = 1;
        int index = 0;
        while (index < source.length()) {
            int lineEnd = source.indexOf('\n', index);
            if (lineEnd < 0) {
                lineEnd = source.length();
            }
            int first = index;
            while (first < lineEnd && isHorizontalWhitespace(source.charAt(first))) {
                first++;
            }
            if (first < lineEnd && source.charAt(first) == '#') {
                lines.add(lineNumber);
            }
            index = lineEnd + 1;
            lineNumber++;
        }
        return lines;
    }

    private static boolean isHorizontalWhitespace(char character) {
        return character == ' ' || character == '\t' || character == '\r';
    }

    /** 从源码开头扫描到光标，判断光标是否落在字符串、字符字面量或注释里。 */
    private static LexicalScan scanContext(String source, int caret) {
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;
        char quote = 0;
        for (int index = 0; index < caret; index++) {
            char character = source.charAt(index);
            if (inLineComment) {
                if (character == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (character == '*' && index + 1 < caret && source.charAt(index + 1) == '/') {
                    inBlockComment = false;
                    index++;
                }
                continue;
            }
            if (inString || inChar) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == quote) {
                    inString = false;
                    inChar = false;
                }
                continue;
            }
            if (character == '/' && index + 1 < caret && source.charAt(index + 1) == '/') {
                inLineComment = true;
                index++;
            } else if (character == '/' && index + 1 < caret && source.charAt(index + 1) == '*') {
                inBlockComment = true;
                index++;
            } else if (character == '"') {
                inString = true;
                quote = '"';
            } else if (character == '\'') {
                inChar = true;
                quote = '\'';
            }
        }
        return new LexicalScan(inString, inChar, inBlockComment, inLineComment);
    }

    private record LexicalScan(
            boolean inString, boolean inChar, boolean inBlockComment, boolean inLineComment) {
        boolean inComment() {
            return inBlockComment || inLineComment;
        }
    }
}
