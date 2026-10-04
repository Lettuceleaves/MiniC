package craken.compiler.preprocess;

import craken.compiler.SourceFile;
import craken.SourceRange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 处理对象宏、函数式宏、宏运算符和预定义位置宏。
 */
final class TextReplacementManager {
    private static final int MAX_EXPANSION_DEPTH = 64;
    private static final String FILE_MACRO = "__FILE__";
    private static final String LINE_MACRO = "__LINE__";
    private static final String VARIADIC_PARAMETER = "__VA_ARGS__";
    private static final Pattern FUNCTION_DEFINE_PATTERN = Pattern.compile(
            "^\\s*#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)\\(([^)]*)\\)(.*)$"
    );
    private static final Pattern OBJECT_DEFINE_PATTERN = Pattern.compile(
            "^\\s*#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+(.*))?\\s*$"
    );
    private static final Pattern UNDEF_PATTERN = Pattern.compile(
            "^\\s*#\\s*undef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$"
    );
    private static final Pattern DEFINE_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*define\\b.*$");
    private static final Pattern UNDEF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*undef\\b.*$");
    private static final List<String> MULTI_CHARACTER_TOKENS = List.of(
            "%:%:", ">>=", "<<=", "...", "##", "->", "++", "--", "<<", ">>", "<=", ">=",
            "==", "!=", "&&", "||", "*=", "/=", "%=", "+=", "-=", "&=", "^=", "|=", "<:",
            ":>", "<%", "%>", "%:"
    );

    static boolean isPredefinedMacro(String name) {
        return FILE_MACRO.equals(name) || LINE_MACRO.equals(name);
    }

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
            defineObject(
                    sourceFile,
                    work,
                    startOffset,
                    endOffset,
                    objectDefineMatcher.group(1),
                    objectDefineMatcher.group(2)
            );
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
                    "define 指令格式非法"
            ));
            return true;
        }
        if (UNDEF_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(Preprocessor.diagnostic(
                    sourceFile,
                    startOffset,
                    endOffset,
                    "undef 指令必须使用宏名称"
            ));
            return true;
        }
        return false;
    }

    void appendExpandedLine(
            StringBuilder output,
            Preprocessor.Work work,
            SourceFile sourceFile,
            String line,
            int[] sourceOffsets,
            boolean mapToThisSource
    ) {
        if (sourceOffsets.length != line.length()) {
            throw new IllegalArgumentException("source offsets must match logical line length");
        }
        int index = 0;
        while (index < line.length()) {
            char character = line.charAt(index);
            if (character == '"' || character == '\'') {
                int end = skipQuotedLiteral(line, index, character);
                appendDirect(output, work, line, index, end, sourceOffsets, mapToThisSource);
                index = end;
                continue;
            }
            if (!isIdentifierStart(character)) {
                appendDirect(output, work, line, index, index + 1, sourceOffsets, mapToThisSource);
                index++;
                continue;
            }

            int identifierEnd = identifierEnd(line, index);
            int sourceOffset = sourceOffsets[index];
            ExpansionContext context = new ExpansionContext(
                    sourceFile,
                    sourceOffset,
                    Math.max(1, identifierEnd - index)
            );
            Expansion expansion = expandAt(line, index, work, Set.of(), 0, context, false);
            if (!expansion.expanded()) {
                appendDirect(output, work, line, index, expansion.endOffset(), sourceOffsets, mapToThisSource);
            } else {
                appendGenerated(output, work, expansion.text(), sourceOffset, mapToThisSource);
            }
            index = expansion.endOffset();
        }
    }

    /** Expand macros in a #if expression while preserving operands of defined. */
    String expandConditionExpression(
            String text,
            Preprocessor.Work work,
            SourceFile sourceFile,
            int invocationOffset
    ) {
        return expandText(
                text,
                work,
                Set.of(),
                0,
                new ExpansionContext(sourceFile, invocationOffset, Math.max(1, text.length())),
                true
        );
    }

    private void defineObject(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            String name,
            String replacement
    ) {
        if (rejectReservedName(sourceFile, work, startOffset, endOffset, name)) {
            return;
        }
        String normalizedReplacement = replacement == null ? "" : replacement.strip();
        SourceRange range = sourceFile.range(startOffset, endOffset);
        MacroDefinition definition = new MacroDefinition(
                name,
                List.of(),
                false,
                normalizedReplacement,
                range,
                false
        );
        if (!validateReplacement(sourceFile, work, startOffset, endOffset, definition)) {
            return;
        }
        recordDefinition(work, definition);
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
        if (rejectReservedName(sourceFile, work, startOffset, endOffset, name)) {
            return;
        }
        ArrayList<String> parameters = new ArrayList<>();
        HashSet<String> unique = new HashSet<>();
        boolean variadic = false;
        String normalizedParameters = parameterText == null ? "" : parameterText.strip();
        if (!normalizedParameters.isEmpty()) {
            String[] rawParameters = normalizedParameters.split(",", -1);
            for (int index = 0; index < rawParameters.length; index++) {
                String parameter = rawParameters[index].strip();
                if (parameter.equals("...")) {
                    if (index != rawParameters.length - 1) {
                        addDefinitionDiagnostic(sourceFile, work, startOffset, endOffset,
                                "可变参数 ... 必须是最后一个宏参数");
                        return;
                    }
                    variadic = true;
                    continue;
                }
                if (!parameter.matches("[A-Za-z_][A-Za-z0-9_]*")
                        || parameter.equals(VARIADIC_PARAMETER)
                        || !unique.add(parameter)) {
                    addDefinitionDiagnostic(sourceFile, work, startOffset, endOffset,
                            "函数式宏参数非法或重复：" + parameter);
                    return;
                }
                parameters.add(parameter);
            }
        }

        String normalizedReplacement = replacement == null ? "" : replacement.strip();
        SourceRange range = sourceFile.range(startOffset, endOffset);
        MacroDefinition definition = new MacroDefinition(
                name,
                parameters,
                variadic,
                normalizedReplacement,
                range,
                true
        );
        if (!validateReplacement(sourceFile, work, startOffset, endOffset, definition)) {
            return;
        }
        recordDefinition(work, definition);
    }

    private boolean rejectReservedName(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            String name
    ) {
        if (!isPredefinedMacro(name)) {
            return false;
        }
        addDefinitionDiagnostic(sourceFile, work, startOffset, endOffset,
                "不能重新定义预定义宏 " + name);
        return true;
    }

    private void recordDefinition(Preprocessor.Work work, MacroDefinition definition) {
        work.macros.put(definition.name(), definition);
        work.macroSummaries.add(new PreprocessResult.MacroSummary(
                definition.name(),
                definition.replacement(),
                definition.sourceRange(),
                true
        ));
    }

    private boolean validateReplacement(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            MacroDefinition definition
    ) {
        List<Piece> pieces = tokenize(definition.replacement());
        Set<String> parameterNames = new HashSet<>(definition.parameters());
        if (definition.variadic()) {
            parameterNames.add(VARIADIC_PARAMETER);
        }
        boolean valid = true;
        for (int index = 0; index < pieces.size(); index++) {
            Piece piece = pieces.get(index);
            if (piece.kind() == PieceKind.PASTE) {
                int left = previousNonWhitespace(pieces, index - 1);
                int right = nextNonWhitespace(pieces, index + 1);
                if (left < 0 || right < 0
                        || pieces.get(left).kind() == PieceKind.PASTE
                        || pieces.get(right).kind() == PieceKind.PASTE) {
                    addDefinitionDiagnostic(sourceFile, work, startOffset, endOffset,
                            "## 不能位于替换列表边界");
                    valid = false;
                }
                continue;
            }
            if (piece.kind() == PieceKind.TOKEN && piece.text().equals("#")) {
                int operand = nextNonWhitespace(pieces, index + 1);
                if (!definition.functionLike()
                        || operand < 0
                        || pieces.get(operand).kind() != PieceKind.TOKEN
                        || !parameterNames.contains(pieces.get(operand).text())) {
                    addDefinitionDiagnostic(sourceFile, work, startOffset, endOffset,
                            "# 后必须是宏参数");
                    valid = false;
                }
            }
            if (piece.kind() == PieceKind.TOKEN
                    && piece.text().equals(VARIADIC_PARAMETER)
                    && !definition.variadic()) {
                addDefinitionDiagnostic(sourceFile, work, startOffset, endOffset,
                        VARIADIC_PARAMETER + " 只能出现在可变参数宏中");
                valid = false;
            }
            if (piece.kind() == PieceKind.TOKEN && piece.text().equals("__VA_OPT__")) {
                addDefinitionDiagnostic(sourceFile, work, startOffset, endOffset,
                        "暂不支持 __VA_OPT__，不能静默近似其语义");
                valid = false;
            }
        }
        return valid;
    }

    private void addDefinitionDiagnostic(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            String message
    ) {
        work.diagnostics.add(Preprocessor.diagnostic(sourceFile, startOffset, endOffset, message));
    }

    private void undefine(
            SourceFile sourceFile,
            Preprocessor.Work work,
            int startOffset,
            int endOffset,
            String name
    ) {
        if (isPredefinedMacro(name)) {
            addDefinitionDiagnostic(sourceFile, work, startOffset, endOffset,
                    "不能取消预定义宏 " + name);
            return;
        }
        SourceRange range = sourceFile.range(startOffset, endOffset);
        work.macros.remove(name);
        work.macroSummaries.add(new PreprocessResult.MacroSummary(name, "", range, false));
    }

    private Expansion expandAt(
            String text,
            int start,
            Preprocessor.Work work,
            Set<String> disabled,
            int depth,
            ExpansionContext context,
            boolean preserveDefined
    ) {
        int end = identifierEnd(text, start);
        String identifier = text.substring(start, end);
        if (preserveDefined && identifier.equals("defined")) {
            return new Expansion(text.substring(start, end), end, false);
        }
        if (identifier.equals(FILE_MACRO)) {
            return new Expansion(quote(context.sourceFile().path()), end, true);
        }
        if (identifier.equals(LINE_MACRO)) {
            int line = context.sourceFile().range(context.sourceOffset(), context.sourceOffset()).startLine();
            return new Expansion(Integer.toString(line), end, true);
        }

        MacroDefinition macro = work.macros.get(identifier);
        if (macro == null || disabled.contains(identifier)) {
            return new Expansion(identifier, end, false);
        }
        if (depth >= MAX_EXPANSION_DEPTH) {
            diagnose(work, context, "宏展开层级超过 " + MAX_EXPANSION_DEPTH + " 层");
            return new Expansion(identifier, end, true);
        }
        if (!macro.functionLike()) {
            return new Expansion(
                    expandObject(macro, work, disabled, depth, context, preserveDefined),
                    end,
                    true
            );
        }

        int openParen = skipWhitespace(text, end);
        if (openParen >= text.length() || text.charAt(openParen) != '(') {
            return new Expansion(identifier, end, false);
        }
        MacroInvocation invocation = parseInvocation(text, openParen);
        int invocationEnd = invocation.endOffset();
        if (!invocation.terminated()) {
            diagnose(work, context, "函数式宏 " + identifier + " 调用缺少 ')'");
            return new Expansion(text.substring(start, invocationEnd), invocationEnd, false);
        }

        List<String> arguments = normalizeArguments(invocation.arguments(), macro);
        if (!acceptsArity(macro, arguments.size())) {
            String expectation = macro.variadic()
                    ? "至少 " + macro.parameters().size()
                    : Integer.toString(macro.parameters().size());
            diagnose(work, context,
                    "函数式宏 " + identifier + " 参数数量错误：期望 " + expectation
                            + "，实际 " + arguments.size());
            return new Expansion(text.substring(start, invocationEnd), invocationEnd, false);
        }
        return new Expansion(
                expandFunction(macro, arguments, work, disabled, depth, context, preserveDefined),
                invocationEnd,
                true
        );
    }

    private String expandObject(
            MacroDefinition macro,
            Preprocessor.Work work,
            Set<String> disabled,
            int depth,
            ExpansionContext context,
            boolean preserveDefined
    ) {
        HashSet<String> nestedDisabled = new HashSet<>(disabled);
        nestedDisabled.add(macro.name());
        String substituted = applyReplacementOperators(macro, Map.of(), Map.of(), work, context);
        return expandText(substituted, work, nestedDisabled, depth + 1, context, preserveDefined);
    }

    private String expandFunction(
            MacroDefinition macro,
            List<String> arguments,
            Preprocessor.Work work,
            Set<String> disabled,
            int depth,
            ExpansionContext context,
            boolean preserveDefined
    ) {
        LinkedHashMap<String, String> rawArguments = new LinkedHashMap<>();
        LinkedHashMap<String, String> expandedArguments = new LinkedHashMap<>();
        for (int index = 0; index < macro.parameters().size(); index++) {
            String name = macro.parameters().get(index);
            String raw = arguments.get(index);
            rawArguments.put(name, raw);
            expandedArguments.put(name,
                    expandText(raw.strip(), work, disabled, depth + 1, context, preserveDefined));
        }
        if (macro.variadic()) {
            List<String> trailing = arguments.subList(macro.parameters().size(), arguments.size());
            String raw = String.join(", ", trailing);
            ArrayList<String> expandedTrailing = new ArrayList<>();
            for (String argument : trailing) {
                expandedTrailing.add(expandText(
                        argument.strip(), work, disabled, depth + 1, context, preserveDefined));
            }
            rawArguments.put(VARIADIC_PARAMETER, raw);
            expandedArguments.put(VARIADIC_PARAMETER, String.join(", ", expandedTrailing));
        }

        String substituted = applyReplacementOperators(
                macro,
                rawArguments,
                expandedArguments,
                work,
                context
        );
        HashSet<String> nestedDisabled = new HashSet<>(disabled);
        nestedDisabled.add(macro.name());
        return expandText(substituted, work, nestedDisabled, depth + 1, context, preserveDefined);
    }

    private String applyReplacementOperators(
            MacroDefinition macro,
            Map<String, String> rawArguments,
            Map<String, String> expandedArguments,
            Preprocessor.Work work,
            ExpansionContext context
    ) {
        List<Piece> sourcePieces = tokenize(macro.replacement());
        ArrayList<Piece> substituted = new ArrayList<>();
        for (int index = 0; index < sourcePieces.size(); index++) {
            Piece piece = sourcePieces.get(index);
            if (piece.kind() == PieceKind.TOKEN && piece.text().equals("#")) {
                int operand = nextNonWhitespace(sourcePieces, index + 1);
                if (operand >= 0) {
                    Piece parameter = sourcePieces.get(operand);
                    String raw = rawArguments.get(parameter.text());
                    if (raw != null) {
                        substituted.add(new Piece(PieceKind.TOKEN, stringify(raw)));
                        index = operand;
                        continue;
                    }
                }
            }
            if (piece.kind() == PieceKind.TOKEN && rawArguments.containsKey(piece.text())) {
                boolean pasted = adjacentToPaste(sourcePieces, index);
                String argument = pasted ? rawArguments.get(piece.text()).strip() : expandedArguments.get(piece.text());
                List<Piece> argumentPieces = tokenize(argument);
                if (argumentPieces.isEmpty() && pasted) {
                    substituted.add(new Piece(PieceKind.PLACEMARKER, ""));
                } else {
                    substituted.addAll(argumentPieces);
                }
                continue;
            }
            substituted.add(piece);
        }

        while (true) {
            int pasteIndex = firstPaste(substituted);
            if (pasteIndex < 0) {
                break;
            }
            int left = previousNonWhitespace(substituted, pasteIndex - 1);
            int right = nextNonWhitespace(substituted, pasteIndex + 1);
            if (left < 0 || right < 0) {
                diagnose(work, context, "## 不能位于替换列表边界");
                substituted.remove(pasteIndex);
                continue;
            }
            Piece pasted = paste(substituted.get(left), substituted.get(right), work, context);
            for (int remove = right; remove >= left; remove--) {
                substituted.remove(remove);
            }
            substituted.add(left, pasted);
        }

        StringBuilder result = new StringBuilder();
        for (Piece piece : substituted) {
            if (piece.kind() != PieceKind.PLACEMARKER) {
                result.append(piece.text());
            }
        }
        return result.toString();
    }

    private Piece paste(
            Piece left,
            Piece right,
            Preprocessor.Work work,
            ExpansionContext context
    ) {
        if (left.kind() == PieceKind.PLACEMARKER) {
            return right;
        }
        if (right.kind() == PieceKind.PLACEMARKER) {
            return left;
        }
        String combined = left.text() + right.text();
        List<Piece> tokens = tokenize(combined).stream()
                .filter(piece -> piece.kind() != PieceKind.WHITESPACE)
                .toList();
        if (tokens.size() != 1
                || tokens.getFirst().kind() != PieceKind.TOKEN
                || !tokens.getFirst().text().equals(combined)) {
            diagnose(work, context, "拼接结果不是单个预处理记号：" + combined);
        }
        return new Piece(PieceKind.TOKEN, combined);
    }

    private String expandText(
            String text,
            Preprocessor.Work work,
            Set<String> disabled,
            int depth,
            ExpansionContext context,
            boolean preserveDefined
    ) {
        if (text.isEmpty()) {
            return text;
        }
        if (depth >= MAX_EXPANSION_DEPTH) {
            diagnose(work, context, "宏展开层级超过 " + MAX_EXPANSION_DEPTH + " 层");
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

            int identifierEnd = identifierEnd(text, index);
            String identifier = text.substring(index, identifierEnd);
            if (preserveDefined && identifier.equals("defined")) {
                int preservedEnd = preserveDefinedOperand(text, identifierEnd);
                result.append(text, index, preservedEnd);
                index = preservedEnd;
                continue;
            }
            Expansion expansion = expandAt(text, index, work, disabled, depth, context, preserveDefined);
            result.append(expansion.text());
            index = expansion.endOffset();
        }
        return result.toString();
    }

    private int preserveDefinedOperand(String text, int start) {
        int index = skipWhitespace(text, start);
        if (index < text.length() && text.charAt(index) == '(') {
            index = skipWhitespace(text, index + 1);
            if (index < text.length() && isIdentifierStart(text.charAt(index))) {
                index = identifierEnd(text, index);
            }
            index = skipWhitespace(text, index);
            if (index < text.length() && text.charAt(index) == ')') {
                index++;
            }
            return index;
        }
        if (index < text.length() && isIdentifierStart(text.charAt(index))) {
            return identifierEnd(text, index);
        }
        return start;
    }

    private List<String> normalizeArguments(List<String> arguments, MacroDefinition macro) {
        if (!arguments.isEmpty()) {
            return arguments;
        }
        if (!macro.parameters().isEmpty()) {
            return List.of("");
        }
        return arguments;
    }

    private boolean acceptsArity(MacroDefinition macro, int count) {
        return macro.variadic()
                ? count >= macro.parameters().size()
                : count == macro.parameters().size();
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
                    return new MacroInvocation(arguments, index + 1, true);
                }
            } else if (character == ',' && depth == 1) {
                arguments.add(text.substring(argumentStart, index));
                argumentStart = index + 1;
            }
            index++;
        }
        return new MacroInvocation(arguments, text.length(), false);
    }

    private List<Piece> tokenize(String text) {
        ArrayList<Piece> pieces = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (Character.isWhitespace(character)) {
                int end = index + 1;
                while (end < text.length() && Character.isWhitespace(text.charAt(end))) {
                    end++;
                }
                pieces.add(new Piece(PieceKind.WHITESPACE, text.substring(index, end)));
                index = end;
                continue;
            }
            int literalPrefixLength = quotedPrefixLength(text, index);
            if (literalPrefixLength >= 0) {
                int quoteOffset = index + literalPrefixLength;
                int end = skipQuotedLiteral(text, quoteOffset, text.charAt(quoteOffset));
                pieces.add(new Piece(PieceKind.TOKEN, text.substring(index, end)));
                index = end;
                continue;
            }
            if (isIdentifierStart(character)) {
                int end = identifierEnd(text, index);
                pieces.add(new Piece(PieceKind.TOKEN, text.substring(index, end)));
                index = end;
                continue;
            }
            if (Character.isDigit(character)
                    || (character == '.' && index + 1 < text.length()
                    && Character.isDigit(text.charAt(index + 1)))) {
                int end = preprocessingNumberEnd(text, index);
                pieces.add(new Piece(PieceKind.TOKEN, text.substring(index, end)));
                index = end;
                continue;
            }
            String multiCharacter = longestPunctuatorAt(text, index);
            if (multiCharacter != null) {
                pieces.add(new Piece(
                        multiCharacter.equals("##") || multiCharacter.equals("%:%:")
                                ? PieceKind.PASTE
                                : PieceKind.TOKEN,
                        multiCharacter
                ));
                index += multiCharacter.length();
                continue;
            }
            pieces.add(new Piece(PieceKind.TOKEN, Character.toString(character)));
            index++;
        }
        return pieces;
    }

    private int quotedPrefixLength(String text, int index) {
        if (text.charAt(index) == '"' || text.charAt(index) == '\'') {
            return 0;
        }
        if (text.startsWith("u8\"", index)) {
            return 2;
        }
        if (index + 1 < text.length()
                && (text.charAt(index) == 'L' || text.charAt(index) == 'u' || text.charAt(index) == 'U')
                && (text.charAt(index + 1) == '"' || text.charAt(index + 1) == '\'')) {
            return 1;
        }
        return -1;
    }

    private int preprocessingNumberEnd(String text, int start) {
        int index = start + 1;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (Character.isLetterOrDigit(character) || character == '_' || character == '.') {
                index++;
                continue;
            }
            if ((character == '+' || character == '-') && index > start) {
                char previous = text.charAt(index - 1);
                if (previous == 'e' || previous == 'E' || previous == 'p' || previous == 'P') {
                    index++;
                    continue;
                }
            }
            break;
        }
        return index;
    }

    private String longestPunctuatorAt(String text, int index) {
        for (String token : MULTI_CHARACTER_TOKENS) {
            if (text.startsWith(token, index)) {
                return token;
            }
        }
        return null;
    }

    private int firstPaste(List<Piece> pieces) {
        for (int index = 0; index < pieces.size(); index++) {
            if (pieces.get(index).kind() == PieceKind.PASTE) {
                return index;
            }
        }
        return -1;
    }

    private boolean adjacentToPaste(List<Piece> pieces, int index) {
        int left = previousNonWhitespace(pieces, index - 1);
        int right = nextNonWhitespace(pieces, index + 1);
        return (left >= 0 && pieces.get(left).kind() == PieceKind.PASTE)
                || (right >= 0 && pieces.get(right).kind() == PieceKind.PASTE);
    }

    private int previousNonWhitespace(List<Piece> pieces, int index) {
        while (index >= 0 && pieces.get(index).kind() == PieceKind.WHITESPACE) {
            index--;
        }
        return index;
    }

    private int nextNonWhitespace(List<Piece> pieces, int index) {
        while (index < pieces.size() && pieces.get(index).kind() == PieceKind.WHITESPACE) {
            index++;
        }
        return index < pieces.size() ? index : -1;
    }

    private String stringify(String raw) {
        String normalized = normalizeWhitespace(raw.strip());
        return quote(normalized);
    }

    private String normalizeWhitespace(String text) {
        StringBuilder result = new StringBuilder();
        boolean pendingSpace = false;
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '"' || character == '\'') {
                if (pendingSpace && !result.isEmpty()) {
                    result.append(' ');
                }
                pendingSpace = false;
                int end = skipQuotedLiteral(text, index, character);
                result.append(text, index, end);
                index = end;
            } else if (Character.isWhitespace(character)) {
                pendingSpace = true;
                index++;
            } else {
                if (pendingSpace && !result.isEmpty()) {
                    result.append(' ');
                }
                pendingSpace = false;
                result.append(character);
                index++;
            }
        }
        return result.toString();
    }

    private String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void diagnose(Preprocessor.Work work, ExpansionContext context, String message) {
        int start = Math.max(0, Math.min(context.sourceOffset(), context.sourceFile().content().length()));
        int end = Math.min(context.sourceFile().content().length(), start + context.sourceLength());
        work.diagnostics.add(Preprocessor.diagnostic(context.sourceFile(), start, end, message));
    }

    private void appendDirect(
            StringBuilder output,
            Preprocessor.Work work,
            String line,
            int start,
            int end,
            int[] sourceOffsets,
            boolean mapToThisSource
    ) {
        output.append(line, start, end);
        for (int index = start; index < end; index++) {
            work.sourceMap.add(mapToThisSource ? sourceOffsets[index] : -1);
        }
    }

    private void appendGenerated(
            StringBuilder output,
            Preprocessor.Work work,
            String text,
            int sourceOffset,
            boolean mapToThisSource
    ) {
        output.append(text);
        for (int index = 0; index < text.length(); index++) {
            work.sourceMap.add(mapToThisSource ? sourceOffset : -1);
        }
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

    private int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private int identifierEnd(String text, int start) {
        int end = start + 1;
        while (end < text.length() && isIdentifierPart(text.charAt(end))) {
            end++;
        }
        return end;
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
            boolean variadic,
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

    private enum PieceKind {
        TOKEN,
        WHITESPACE,
        PASTE,
        PLACEMARKER
    }

    private record Piece(PieceKind kind, String text) {
    }

    private record MacroInvocation(List<String> arguments, int endOffset, boolean terminated) {
        private MacroInvocation {
            arguments = List.copyOf(arguments);
        }
    }

    private record Expansion(String text, int endOffset, boolean expanded) {
    }

    private record ExpansionContext(SourceFile sourceFile, int sourceOffset, int sourceLength) {
    }
}
