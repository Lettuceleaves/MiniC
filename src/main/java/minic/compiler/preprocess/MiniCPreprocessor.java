package minic.compiler.preprocess;

import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.source.SourceFile;
import minic.source.SourceRange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * MiniC 轻量预编译器。
 */
public final class MiniCPreprocessor implements Preprocessor {
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*#\\s*include\\s+\"([^\"]+)\"\\s*$");
    private static final Pattern INCLUDE_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*include\\b.*$");
    private static final Pattern DEFINE_PATTERN = Pattern.compile("^\\s*#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+(.*))?\\s*$");
    private static final Pattern UNDEF_PATTERN = Pattern.compile("^\\s*#\\s*undef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
    private static final Pattern IFDEF_PATTERN = Pattern.compile("^\\s*#\\s*ifdef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
    private static final Pattern IFNDEF_PATTERN = Pattern.compile("^\\s*#\\s*ifndef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
    private static final Pattern ELSE_PATTERN = Pattern.compile("^\\s*#\\s*else\\s*$");
    private static final Pattern ENDIF_PATTERN = Pattern.compile("^\\s*#\\s*endif\\s*$");
    private static final Pattern DEFINE_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*define\\b.*$");
    private static final Pattern UNDEF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*undef\\b.*$");
    private static final Pattern IFDEF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*ifdef\\b.*$");
    private static final Pattern IFNDEF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*ifndef\\b.*$");
    private static final Pattern ELSE_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*else\\b.*$");
    private static final Pattern ENDIF_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*endif\\b.*$");

    /**
     * 对源码执行默认预编译。
     *
     * @param sourceFile 原始源码
     * @return 预编译结果
     */
    @Override
    public PreprocessResult preprocess(SourceFile sourceFile) {
        return preprocess(sourceFile, PreprocessOptions.defaults());
    }

    /**
     * 对源码执行预编译。
     *
     * @param sourceFile 原始源码
     * @param options 预编译选项
     * @return 预编译结果
     */
    @Override
    public PreprocessResult preprocess(SourceFile sourceFile, PreprocessOptions options) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(options, "options");
        Work work = new Work(options);
        String content = expandSource(sourceFile, sourceDirectory(sourceFile), new HashSet<>(), work);
        SourceFile preprocessedSource = new SourceFile(sourceFile.path(), content);
        return new PreprocessResult(
                preprocessedSource,
                work.diagnostics,
                work.includes,
                work.macroSummaries
        );
    }

    private String expandSource(SourceFile sourceFile, Path currentDirectory, Set<Path> includeStack, Work work) {
        StringBuilder output = new StringBuilder();
        int initialConditionDepth = work.conditionStack.size();
        int lineStart = 0;
        String content = sourceFile.content();
        while (lineStart < content.length()) {
            int lineEnd = content.indexOf('\n', lineStart);
            int nextLineStart = lineEnd < 0 ? content.length() : lineEnd + 1;
            String line = content.substring(lineStart, lineEnd < 0 ? content.length() : lineEnd);
            Matcher matcher = INCLUDE_PATTERN.matcher(line);
            if (handleConditionDirective(sourceFile, work, lineStart, nextLineStart, line)) {
                // 条件编译指令本身不进入输出源码。
            } else if (!work.isActive()) {
                // 被排除分支不输出，也不触发普通源码诊断。
            } else if (matcher.matches()) {
                expandInclude(sourceFile, currentDirectory, includeStack, work, output, lineStart, nextLineStart, matcher.group(1));
            } else {
                Matcher defineMatcher = DEFINE_PATTERN.matcher(line);
                Matcher undefMatcher = UNDEF_PATTERN.matcher(line);
                if (defineMatcher.matches()) {
                    defineMacro(sourceFile, work, lineStart, nextLineStart, defineMatcher.group(1), defineMatcher.group(2));
                } else if (undefMatcher.matches()) {
                    undefineMacro(sourceFile, work, lineStart, nextLineStart, undefMatcher.group(1));
                } else {
                    if (INCLUDE_DIRECTIVE_PATTERN.matcher(line).matches()) {
                        work.diagnostics.add(diagnostic(
                                sourceFile,
                                lineStart,
                                nextLineStart,
                                "include 指令必须使用双引号路径，例如 #include \"name.mh\""
                        ));
                    } else if (DEFINE_DIRECTIVE_PATTERN.matcher(line).matches()) {
                        work.diagnostics.add(diagnostic(
                                sourceFile,
                                lineStart,
                                nextLineStart,
                                "define 指令必须使用对象宏名称"
                        ));
                    } else if (UNDEF_DIRECTIVE_PATTERN.matcher(line).matches()) {
                        work.diagnostics.add(diagnostic(
                                sourceFile,
                                lineStart,
                                nextLineStart,
                                "undef 指令必须使用对象宏名称"
                        ));
                    } else {
                        output.append(replaceMacros(content.substring(lineStart, nextLineStart), work));
                    }
                }
            }
            lineStart = nextLineStart;
        }
        while (work.conditionStack.size() > initialConditionDepth) {
            ConditionFrame frame = work.conditionStack.removeLast();
            work.diagnostics.add(diagnostic(
                    frame.sourceFile(),
                    frame.startOffset(),
                    frame.endOffset(),
                    "条件编译块缺少 #endif"
            ));
        }
        if (content.isEmpty()) {
            return "";
        }
        return output.toString();
    }

    private boolean handleConditionDirective(SourceFile sourceFile, Work work, int startOffset, int endOffset, String line) {
        Matcher ifdefMatcher = IFDEF_PATTERN.matcher(line);
        Matcher ifndefMatcher = IFNDEF_PATTERN.matcher(line);
        if (ifdefMatcher.matches()) {
            pushCondition(sourceFile, work, startOffset, endOffset, work.macros.containsKey(ifdefMatcher.group(1)));
            return true;
        }
        if (ifndefMatcher.matches()) {
            pushCondition(sourceFile, work, startOffset, endOffset, !work.macros.containsKey(ifndefMatcher.group(1)));
            return true;
        }
        if (ELSE_PATTERN.matcher(line).matches()) {
            switchConditionElse(sourceFile, work, startOffset, endOffset);
            return true;
        }
        if (ENDIF_PATTERN.matcher(line).matches()) {
            popCondition(sourceFile, work, startOffset, endOffset);
            return true;
        }
        if (IFDEF_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "ifdef 指令必须使用宏名称"));
            return true;
        }
        if (IFNDEF_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "ifndef 指令必须使用宏名称"));
            return true;
        }
        if (ELSE_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "else 指令不能带参数"));
            return true;
        }
        if (ENDIF_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "endif 指令不能带参数"));
            return true;
        }
        return false;
    }

    private void pushCondition(SourceFile sourceFile, Work work, int startOffset, int endOffset, boolean conditionActive) {
        boolean parentActive = work.isActive();
        work.conditionStack.add(new ConditionFrame(sourceFile, startOffset, endOffset, parentActive, conditionActive, false));
    }

    private void switchConditionElse(SourceFile sourceFile, Work work, int startOffset, int endOffset) {
        if (work.conditionStack.isEmpty()) {
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "孤立的 #else"));
            return;
        }
        ConditionFrame frame = work.conditionStack.removeLast();
        if (frame.elseSeen()) {
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "同一条件编译块不能出现多个 #else"));
            work.conditionStack.add(frame);
            return;
        }
        work.conditionStack.add(new ConditionFrame(
                frame.sourceFile(),
                frame.startOffset(),
                frame.endOffset(),
                frame.parentActive(),
                !frame.branchActive(),
                true
        ));
    }

    private void popCondition(SourceFile sourceFile, Work work, int startOffset, int endOffset) {
        if (work.conditionStack.isEmpty()) {
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "多余的 #endif"));
            return;
        }
        work.conditionStack.removeLast();
    }

    private void expandInclude(
            SourceFile sourceFile,
            Path currentDirectory,
            Set<Path> includeStack,
            Work work,
            StringBuilder output,
            int startOffset,
            int endOffset,
            String requestedPath
    ) {
        SourceRange directiveRange = new SourceRange(sourceFile, startOffset, endOffset);
        if (!requestedPath.endsWith(".mh")) {
            work.includes.add(new IncludeSummary(requestedPath, null, directiveRange, false));
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "include 目标必须使用 .mh 后缀：" + requestedPath));
            return;
        }

        Path resolvedPath = resolveInclude(currentDirectory, requestedPath, work.options.includeRoots());
        if (resolvedPath == null) {
            work.includes.add(new IncludeSummary(requestedPath, null, directiveRange, false));
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "include 文件不存在：" + requestedPath));
            return;
        }
        if (includeStack.contains(resolvedPath)) {
            work.includes.add(new IncludeSummary(requestedPath, resolvedPath, directiveRange, false));
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "检测到 include 循环：" + requestedPath));
            return;
        }

        SourceFile includeFile;
        try {
            includeFile = new SourceFile(resolvedPath.toString(), Files.readString(resolvedPath));
        } catch (IOException exception) {
            work.includes.add(new IncludeSummary(requestedPath, resolvedPath, directiveRange, false));
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "读取 include 文件失败：" + exception.getMessage()));
            return;
        }

        work.includes.add(new IncludeSummary(requestedPath, resolvedPath, directiveRange, true));
        includeStack.add(resolvedPath);
        String includeContent = expandSource(includeFile, resolvedPath.getParent(), includeStack, work);
        validateHeader(includeFile, includeContent, work);
        output.append(includeContent);
        if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
            output.append('\n');
        }
        includeStack.remove(resolvedPath);
    }

    private void validateHeader(SourceFile originalHeader, String content, Work work) {
        SourceFile headerSource = new SourceFile(originalHeader.path(), content);
        LexResult lexResult = new Lexer(headerSource).lex();
        if (!lexResult.diagnostics().isEmpty()) {
            work.diagnostics.add(diagnostic(originalHeader, 0, originalHeader.content().length(), "头文件包含非法词法内容"));
            return;
        }
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        if (!parseResult.diagnostics().isEmpty()) {
            work.diagnostics.add(diagnostic(originalHeader, 0, originalHeader.content().length(), "头文件只能包含函数声明、外部函数声明和结构体声明"));
            return;
        }
        parseResult.program().functions().stream()
                .filter(FunctionDecl::hasBody)
                .findFirst()
                .ifPresent(function -> work.diagnostics.add(diagnostic(
                        originalHeader,
                        0,
                        originalHeader.content().length(),
                        "头文件不能包含函数定义：" + function.name()
                )));
    }

    private void defineMacro(
            SourceFile sourceFile,
            Work work,
            int startOffset,
            int endOffset,
            String name,
            String replacement
    ) {
        String normalizedReplacement = replacement == null ? "" : replacement.stripTrailing();
        SourceRange range = new SourceRange(sourceFile, startOffset, endOffset);
        if (containsIdentifier(normalizedReplacement, name)) {
            work.diagnostics.add(diagnostic(sourceFile, startOffset, endOffset, "宏不能直接自引用：" + name));
            return;
        }
        work.macros.put(name, new MacroDefinition(name, normalizedReplacement, range));
        work.macroSummaries.add(new MacroSummary(name, normalizedReplacement, range, true));
    }

    private void undefineMacro(SourceFile sourceFile, Work work, int startOffset, int endOffset, String name) {
        SourceRange range = new SourceRange(sourceFile, startOffset, endOffset);
        work.macros.remove(name);
        work.macroSummaries.add(new MacroSummary(name, "", range, false));
    }

    private String replaceMacros(String line, Work work) {
        StringBuilder output = new StringBuilder();
        int index = 0;
        while (index < line.length()) {
            char character = line.charAt(index);
            if (character == '"') {
                int end = copyQuotedLiteral(line, index, output, '"');
                index = end;
            } else if (character == '\'') {
                int end = copyQuotedLiteral(line, index, output, '\'');
                index = end;
            } else if (isIdentifierStart(character)) {
                int end = index + 1;
                while (end < line.length() && isIdentifierPart(line.charAt(end))) {
                    end++;
                }
                String identifier = line.substring(index, end);
                MacroDefinition macro = work.macros.get(identifier);
                output.append(macro == null ? identifier : macro.replacement());
                index = end;
            } else {
                output.append(character);
                index++;
            }
        }
        return output.toString();
    }

    private int copyQuotedLiteral(String line, int start, StringBuilder output, char quote) {
        output.append(quote);
        int index = start + 1;
        while (index < line.length()) {
            char character = line.charAt(index++);
            output.append(character);
            if (character == '\\' && index < line.length()) {
                output.append(line.charAt(index++));
            } else if (character == quote) {
                break;
            }
        }
        return index;
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

    private Path resolveInclude(Path currentDirectory, String requestedPath, List<Path> includeRoots) {
        ArrayList<Path> candidates = new ArrayList<>();
        if (currentDirectory != null) {
            candidates.add(currentDirectory.resolve(requestedPath));
        }
        includeRoots.stream()
                .map(root -> root.resolve(requestedPath))
                .forEach(candidates::add);
        return candidates.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
    }

    private Path sourceDirectory(SourceFile sourceFile) {
        Path sourcePath = Path.of(sourceFile.path());
        Path parent = sourcePath.getParent();
        if (parent == null) {
            return null;
        }
        return parent.toAbsolutePath().normalize();
    }

    private Diagnostic diagnostic(SourceFile sourceFile, int startOffset, int endOffset, String message) {
        return new Diagnostic(
                "PRE001",
                DiagnosticSeverity.ERROR,
                message,
                new SourceRange(sourceFile, startOffset, endOffset)
        );
    }

    private boolean isIdentifierStart(char character) {
        return character == '_' || (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
    }

    private boolean isIdentifierPart(char character) {
        return isIdentifierStart(character) || (character >= '0' && character <= '9');
    }

    private record MacroDefinition(String name, String replacement, SourceRange sourceRange) {
        private MacroDefinition {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(replacement, "replacement");
            Objects.requireNonNull(sourceRange, "sourceRange");
        }
    }

    private record ConditionFrame(
            SourceFile sourceFile,
            int startOffset,
            int endOffset,
            boolean parentActive,
            boolean branchActive,
            boolean elseSeen
    ) {
    }

    private static final class Work {
        private final PreprocessOptions options;
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private final List<IncludeSummary> includes = new ArrayList<>();
        private final Map<String, MacroDefinition> macros = new LinkedHashMap<>();
        private final List<MacroSummary> macroSummaries = new ArrayList<>();
        private final ArrayList<ConditionFrame> conditionStack = new ArrayList<>();

        private Work(PreprocessOptions options) {
            this.options = options;
        }

        private boolean isActive() {
            return conditionStack.stream()
                    .allMatch(frame -> frame.parentActive() && frame.branchActive());
        }
    }
}
