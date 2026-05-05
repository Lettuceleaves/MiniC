package minic.compiler.preprocess;

import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
                List.of()
        );
    }

    private String expandSource(SourceFile sourceFile, Path currentDirectory, Set<Path> includeStack, Work work) {
        StringBuilder output = new StringBuilder();
        int lineStart = 0;
        String content = sourceFile.content();
        while (lineStart < content.length()) {
            int lineEnd = content.indexOf('\n', lineStart);
            int nextLineStart = lineEnd < 0 ? content.length() : lineEnd + 1;
            String line = content.substring(lineStart, lineEnd < 0 ? content.length() : lineEnd);
            Matcher matcher = INCLUDE_PATTERN.matcher(line);
            if (matcher.matches()) {
                expandInclude(sourceFile, currentDirectory, includeStack, work, output, lineStart, nextLineStart, matcher.group(1));
            } else {
                if (INCLUDE_DIRECTIVE_PATTERN.matcher(line).matches()) {
                    work.diagnostics.add(diagnostic(
                            sourceFile,
                            lineStart,
                            nextLineStart,
                            "include 指令必须使用双引号路径，例如 #include \"name.mh\""
                    ));
                }
                output.append(content, lineStart, nextLineStart);
            }
            lineStart = nextLineStart;
        }
        if (content.isEmpty()) {
            return "";
        }
        return output.toString();
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
        output.append(expandSource(includeFile, resolvedPath.getParent(), includeStack, work));
        if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
            output.append('\n');
        }
        includeStack.remove(resolvedPath);
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

    private static final class Work {
        private final PreprocessOptions options;
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private final List<IncludeSummary> includes = new ArrayList<>();

        private Work(PreprocessOptions options) {
            this.options = options;
        }
    }
}
