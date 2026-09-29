package minic.compiler.preprocess;

import minic.compiler.SourceFile;
import minic.source.SourceRange;

import java.util.Objects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 处理对象宏定义、取消定义和普通源码文本替换。
 */
final class TextReplacementManager {
    private static final Pattern FUNCTION_DEFINE_PATTERN = Pattern.compile(
            "^\\s*#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)\\(([^)]*)\\)(?:\\s+(.*))?\\s*$"
    );
    private static final Pattern OBJECT_DEFINE_PATTERN = Pattern.compile(
            "^\\s*#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+(.*))?\\s*$"
    );
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
        Matcher functionDefineMatcher = FUNCTION_DEFINE_PATTERN.matcher(line);
        Matcher objectDefineMatcher = OBJECT_DEFINE_PATTERN.matcher(line);
        Matcher undefMatcher = UNDEF_PATTERN.matcher(line);
        if (functionDefineMatcher.matches()) {
            defineFunction(
                    sourceFile,
                    work,
                    startOffset,
                    endOffset,
                    functionDefineMatcher.group(1),
                    functionDefineMatcher.group(2),
                    functionDefineMatcher.group(3)
            );
            return true;
        }
        if (objectDefineMatcher.matches()) {
            defineObject(sourceFile, work, startOffset, endOffset,
                    objectDefineMatcher.group(1), objectDefineMatcher.group(2));
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
                if (macro != null && macro.functionLike()) {
                    int openParen = skipWhitespace(line, end);
                    if (openParen < line.length() && line.charAt(openParen) == '(') {
                        MacroInvocation invocation = parseInvocation(line, openParen);
                        if (invocation != null && invocation.arguments().size() == macro.parameters().size()) {
                            String replacement = expandFunction(macro, invocation.arguments(), work, Set.of(), 0);
                            appendMapped(
                                    output,
                                    work,
                                    replacement,
                                    lineStartOffset + index,
                                    invocation.endOffset() - index,
                                    mapToThisSource
                            );
                            index = invocation.endOffset();
                            continue;
                        }
                    }
                    appendMapped(output, work, identifier, lineStartOffset + index, end - index, mapToThisSource);
                    index = end;
                    continue;
                }
                appendMapped(
                        output,
                        work,
                        macro == null ? identifier : expandObject(macro, work, Set.of(), 0),
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

    private void defineObject(
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
        work.macros.put(name, new MacroDefinition(name, List.of(), normalizedReplacement, range, false));
        work.macroSummaries.add(new PreprocessResult.MacroSummary(name, normalizedReplacement, range, true));
    }

    private void defineFunction(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            String name,
            String parameterText,
            String replacement
    ) {
        ArrayList<String> parameters = new ArrayList<>();
        HashSet<String> unique = new HashSet<>();
        String normalizedParameters = parameterText == null ? "" : parameterText.strip();
        if (!normalizedParameters.isEmpty()) {
            for (String rawParameter : normalizedParameters.split(",", -1)) {
                String parameter = rawParameter.strip();
                if (!parameter.matches("[A-Za-z_][A-Za-z0-9_]*") || !unique.add(parameter)) {
                    work.diagnostics.add(Preprocessor.diagnostic(
                            sourceFile,
                            startOffset,
                            endOffset,
                            "函数式宏参数非法或重复：" + parameter
                    ));
                    return;
                }
                parameters.add(parameter);
            }
        }
        String normalizedReplacement = replacement == null ? "" : replacement.stripTrailing();
        SourceRange range = sourceFile.range(startOffset, endOffset);
        work.macros.put(name, new MacroDefinition(name, parameters, normalizedReplacement, range, true));
        work.macroSummaries.add(new PreprocessResult.MacroSummary(name, normalizedReplacement, range, true));
    }

    private String expandObject(
            MacroDefinition macro,
            Preprocessor.Work work,
            Set<String> disabled,
            int depth
    ) {
        if (depth >= 64 || disabled.contains(macro.name())) {
            return macro.name();
        }
        HashSet<String> nestedDisabled = new HashSet<>(disabled);
        nestedDisabled.add(macro.name());
        return expandText(macro.replacement(), work, nestedDisabled, depth + 1);
    }

    private String expandFunction(
            MacroDefinition macro,
            List<String> arguments,
            Preprocessor.Work work,
            Set<String> disabled,
            int depth
    ) {
        if (depth >= 64 || disabled.contains(macro.name())) {
            return macro.name();
        }
        java.util.LinkedHashMap<String, String> replacements = new java.util.LinkedHashMap<>();
        for (int index = 0; index < macro.parameters().size(); index++) {
            replacements.put(
                    macro.parameters().get(index),
                    expandText(arguments.get(index).strip(), work, disabled, depth + 1)
            );
        }
        String substituted = replaceIdentifiers(macro.replacement(), replacements);
        HashSet<String> nestedDisabled = new HashSet<>(disabled);
        nestedDisabled.add(macro.name());
        return expandText(substituted, work, nestedDisabled, depth + 1);
    }

    private String expandText(String text, Preprocessor.Work work, Set<String> disabled, int depth) {
        if (depth >= 64 || text.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '"' || character == '\'') {
                int end = skipQuotedLiteral(text, index, character);
                result.append(text, index, end);
                index = end;
                continue;
            }
            if (!isIdentifierStart(character)) {
                result.append(character);
                index++;
                continue;
            }
            int end = index + 1;
            while (end < text.length() && isIdentifierPart(text.charAt(end))) {
                end++;
            }
            String identifier = text.substring(index, end);
            MacroDefinition macro = work.macros.get(identifier);
            if (macro == null || disabled.contains(identifier)) {
                result.append(identifier);
                index = end;
                continue;
            }
            if (!macro.functionLike()) {
                result.append(expandObject(macro, work, disabled, depth));
                index = end;
                continue;
            }
            int openParen = skipWhitespace(text, end);
            MacroInvocation invocation = openParen < text.length() && text.charAt(openParen) == '('
                    ? parseInvocation(text, openParen)
                    : null;
            if (invocation == null || invocation.arguments().size() != macro.parameters().size()) {
                result.append(identifier);
                index = end;
                continue;
            }
            result.append(expandFunction(macro, invocation.arguments(), work, disabled, depth));
            index = invocation.endOffset();
        }
        return result.toString();
    }

    private String replaceIdentifiers(String text, java.util.Map<String, String> replacements) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '"' || character == '\'') {
                int end = skipQuotedLiteral(text, index, character);
                result.append(text, index, end);
                index = end;
                continue;
            }
            if (!isIdentifierStart(character)) {
                result.append(character);
                index++;
                continue;
            }
            int end = index + 1;
            while (end < text.length() && isIdentifierPart(text.charAt(end))) {
                end++;
            }
            String identifier = text.substring(index, end);
            result.append(replacements.getOrDefault(identifier, identifier));
            index = end;
        }
        return result.toString();
    }

    private MacroInvocation parseInvocation(String text, int openParen) {
        ArrayList<String> arguments = new ArrayList<>();
        int depth = 1;
        int argumentStart = openParen + 1;
        int index = argumentStart;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '"' || character == '\'') {
                index = skipQuotedLiteral(text, index, character);
                continue;
            }
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth == 0) {
                    String finalArgument = text.substring(argumentStart, index);
                    if (!finalArgument.isBlank() || !arguments.isEmpty()) {
                        arguments.add(finalArgument);
                    }
                    return new MacroInvocation(arguments, index + 1);
                }
            } else if (character == ',' && depth == 1) {
                arguments.add(text.substring(argumentStart, index));
                argumentStart = index + 1;
            }
            index++;
        }
        return null;
    }

    private int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
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

    record MacroDefinition(
            String name,
            List<String> parameters,
            String replacement,
            SourceRange sourceRange,
            boolean functionLike
    ) {
        MacroDefinition {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(parameters, "parameters");
            Objects.requireNonNull(replacement, "replacement");
            Objects.requireNonNull(sourceRange, "sourceRange");
            parameters = List.copyOf(parameters);
        }
    }

    private record MacroInvocation(List<String> arguments, int endOffset) {
        private MacroInvocation {
            arguments = List.copyOf(arguments);
        }
    }
}
