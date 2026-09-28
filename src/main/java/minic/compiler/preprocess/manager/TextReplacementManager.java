package minic.compiler.preprocess;

import minic.compiler.SourceFile;
import minic.source.SourceRange;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 处理对象宏定义、取消定义和普通源码文本替换。
 */
final class TextReplacementManager {
    private static final Pattern DEFINE_PATTERN = Pattern.compile("^\\s*#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+(.*))?\\s*$");
    private static final Pattern UNDEF_PATTERN = Pattern.compile("^\\s*#\\s*undef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
    private static final Pattern DEFINE_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*define\\b.*$");
    private static final Pattern UNDEF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*undef\\b.*$");

    boolean handleDirective(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            String line
    ) {
        Matcher defineMatcher = DEFINE_PATTERN.matcher(line);
        Matcher undefMatcher = UNDEF_PATTERN.matcher(line);
        if (defineMatcher.matches()) {
            define(sourceFile, work, startOffset, endOffset, defineMatcher.group(1), defineMatcher.group(2));
            return true;
        }
        if (undefMatcher.matches()) {
            undefine(sourceFile, work, startOffset, endOffset, undefMatcher.group(1));
            return true;
        }
        if (DEFINE_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(Preprocessor.diagnostic(
                    sourceFile,
                    startOffset,
                    endOffset,
                    "define 指令必须使用对象宏名称"
            ));
            return true;
        }
        if (UNDEF_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(Preprocessor.diagnostic(
                    sourceFile,
                    startOffset,
                    endOffset,
                    "undef 指令必须使用对象宏名称"
            ));
            return true;
        }
        return false;
    }

    void appendExpandedLine(
            StringBuilder output,
            Preprocessor.Work work,
            String line,
            int lineStartOffset,
            boolean mapToThisSource
    ) {
        int index = 0;
        while (index < line.length()) {
            char character = line.charAt(index);
            if (character == '"') {
                index = copyQuotedLiteral(line, index, output, work, lineStartOffset, '"', mapToThisSource);
            } else if (character == '\'') {
                index = copyQuotedLiteral(line, index, output, work, lineStartOffset, '\'', mapToThisSource);
            } else if (isIdentifierStart(character)) {
                int end = index + 1;
                while (end < line.length() && isIdentifierPart(line.charAt(end))) {
                    end++;
                }
                String identifier = line.substring(index, end);
                MacroDefinition macro = work.macros.get(identifier);
                appendMapped(
                        output,
                        work,
                        macro == null ? identifier : macro.replacement(),
                        lineStartOffset + index,
                        end - index,
                        mapToThisSource
                );
                index = end;
            } else {
                appendMapped(output, work, Character.toString(character), lineStartOffset + index, mapToThisSource);
                index++;
            }
        }
    }

    private void define(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            String name,
            String replacement
    ) {
        String normalizedReplacement = replacement == null ? "" : replacement.stripTrailing();
        SourceRange range = sourceFile.range(startOffset, endOffset);
        if (containsIdentifier(normalizedReplacement, name)) {
            work.diagnostics.add(Preprocessor.diagnostic(
                    sourceFile,
                    startOffset,
                    endOffset,
                    "宏不能直接自引用：" + name
            ));
            return;
        }
        work.macros.put(name, new MacroDefinition(name, normalizedReplacement, range));
        work.macroSummaries.add(new PreprocessResult.MacroSummary(name, normalizedReplacement, range, true));
    }

    private void undefine(SourceFile sourceFile, Preprocessor.Work work, int startOffset, int endOffset, String name) {
        SourceRange range = sourceFile.range(startOffset, endOffset);
        work.macros.remove(name);
        work.macroSummaries.add(new PreprocessResult.MacroSummary(name, "", range, false));
    }

    private int copyQuotedLiteral(
            String line,
            int start,
            StringBuilder output,
            Preprocessor.Work work,
            int lineStartOffset,
            char quote,
            boolean mapToThisSource
    ) {
        appendMapped(output, work, Character.toString(quote), lineStartOffset + start, mapToThisSource);
        int index = start + 1;
        while (index < line.length()) {
            int characterOffset = index;
            char character = line.charAt(index++);
            appendMapped(output, work, Character.toString(character), lineStartOffset + characterOffset, mapToThisSource);
            if (character == '\\' && index < line.length()) {
                int escapedOffset = index;
                appendMapped(output, work, Character.toString(line.charAt(index++)), lineStartOffset + escapedOffset, mapToThisSource);
            } else if (character == quote) {
                break;
            }
        }
        return index;
    }

    private void appendMapped(
            StringBuilder output,
            Preprocessor.Work work,
            String text,
            int originalOffset,
            boolean mapToThisSource
    ) {
        appendMapped(output, work, text, originalOffset, 1, mapToThisSource);
    }

    private void appendMapped(
            StringBuilder output,
            Preprocessor.Work work,
            String text,
            int originalOffset,
            int originalLength,
            boolean mapToThisSource
    ) {
        output.append(text);
        for (int index = 0; index < text.length(); index++) {
            work.sourceMap.add(mapToThisSource
                    ? originalOffset + Math.min(index, Math.max(0, originalLength - 1))
                    : -1);
        }
    }

    private boolean containsIdentifier(String text, String identifier) {
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '"' || character == '\'') {
                index = skipQuotedLiteral(text, index, character);
            } else if (isIdentifierStart(character)) {
                int end = index + 1;
                while (end < text.length() && isIdentifierPart(text.charAt(end))) {
                    end++;
                }
                if (text.substring(index, end).equals(identifier)) {
                    return true;
                }
                index = end;
            } else {
                index++;
            }
        }
        return false;
    }

    private int skipQuotedLiteral(String text, int start, char quote) {
        int index = start + 1;
        while (index < text.length()) {
            char character = text.charAt(index++);
            if (character == '\\' && index < text.length()) {
                index++;
            } else if (character == quote) {
                break;
            }
        }
        return index;
    }

    private boolean isIdentifierStart(char character) {
        return character == '_'
                || (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z');
    }

    private boolean isIdentifierPart(char character) {
        return isIdentifierStart(character) || (character >= '0' && character <= '9');
    }

    record MacroDefinition(String name, String replacement, SourceRange sourceRange) {
        MacroDefinition {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(replacement, "replacement");
            Objects.requireNonNull(sourceRange, "sourceRange");
        }
    }
}
