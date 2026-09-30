package minic.compiler.preprocess;

import minic.compiler.lexer.Lexer;
import minic.compiler.SourceFile;
import minic.compiler.library.SystemLibraryCatalog;
import minic.SourceRange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 处理 include 指令、路径解析、循环检测和头文件展开。
 */
final class IncludeManager {
    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "^\\s*#\\s*include\\s*(?:\"([^\"]+)\"|<([^>]+)>)\\s*$"
    );
    private static final Pattern INCLUDE_DIRECTIVE_PATTERN = Pattern.compile("^\\s*#\\s*include\\b.*$");

    private final Preprocessor preprocessor;
    private final SystemLibraryCatalog systemLibraries = SystemLibraryCatalog.defaults();

    IncludeManager(Preprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }

    boolean handleDirective(
            SourceFile sourceFile,
            Path currentDirectory,
            Set<Path> includeStack,
            Preprocessor.Work work,
            StringBuilder output,
            int startOffset,
            int endOffset,
            String line,
            boolean mapToThisSource
    ) {
        Matcher matcher = INCLUDE_PATTERN.matcher(line);
        if (matcher.matches()) {
            boolean systemHeader = matcher.group(2) != null;
            String requestedPath = systemHeader ? matcher.group(2).strip() : matcher.group(1);
            String resolvedName = requestedPath;
            if (systemHeader) {
                if (!requestedPath.matches("[A-Za-z0-9_.-]+\\.h")) {
                    work.includes.add(new PreprocessResult.IncludeSummary(
                            requestedPath,
                            null,
                            sourceFile.range(startOffset, endOffset),
                            false
                    ));
                    work.diagnostics.add(Preprocessor.diagnostic(
                            sourceFile,
                            startOffset,
                            endOffset,
                            "尖括号 include 必须是 .h 标准头文件名：" + requestedPath
                    ));
                    return true;
                }
                resolvedName = requestedPath.substring(0, requestedPath.length() - 2) + ".mh";
            }
            expandInclude(
                    sourceFile,
                    currentDirectory,
                    includeStack,
                    work,
                    output,
                    startOffset,
                    endOffset,
                    requestedPath,
                    resolvedName,
                    systemHeader,
                    mapToThisSource
            );
            return true;
        }
        if (INCLUDE_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(Preprocessor.diagnostic(
                    sourceFile,
                    startOffset,
                    endOffset,
                    "include 指令必须使用 \"name.mh\" 或 <name.h>"
            ));
            return true;
        }
        return false;
    }

    private void expandInclude(
            SourceFile sourceFile,
            Path currentDirectory,
            Set<Path> includeStack,
            Preprocessor.Work work,
            StringBuilder output,
            int startOffset,
            int endOffset,
            String requestedPath,
            String resolvedName,
            boolean systemHeader,
            boolean mapToThisSource
    ) {
        SourceRange directiveRange = sourceFile.range(startOffset, endOffset);
        if (!systemHeader && !requestedPath.endsWith(".mh")) {
            work.includes.add(new PreprocessResult.IncludeSummary(requestedPath, null, directiveRange, false));
            work.diagnostics.add(Preprocessor.diagnostic(
                    sourceFile,
                    startOffset,
                    endOffset,
                    "include 目标必须使用 .mh 后缀：" + requestedPath
            ));
            return;
        }

        ResolvedInclude resolved;
        try {
            resolved = resolveInclude(
                    currentDirectory,
                    resolvedName,
                    work.options.includeRoots(),
                    systemHeader
            );
        } catch (IncludeReadException exception) {
            work.includes.add(new PreprocessResult.IncludeSummary(requestedPath, null, directiveRange, false));
            work.diagnostics.add(Preprocessor.diagnostic(
                    sourceFile,
                    startOffset,
                    endOffset,
                    exception.getMessage()
            ));
            return;
        }
        if (resolved == null) {
            work.includes.add(new PreprocessResult.IncludeSummary(requestedPath, null, directiveRange, false));
            work.diagnostics.add(Preprocessor.diagnostic(
                    sourceFile,
                    startOffset,
                    endOffset,
                    "include 文件不存在：" + requestedPath
            ));
            return;
        }
        if (includeStack.contains(resolved.identity())) {
            work.includes.add(new PreprocessResult.IncludeSummary(requestedPath, resolved.identity(), directiveRange, false));
            work.diagnostics.add(Preprocessor.diagnostic(
                    sourceFile,
                    startOffset,
                    endOffset,
                    "检测到 include 循环：" + requestedPath
            ));
            return;
        }

        SourceFile includeFile = new SourceFile(resolved.sourceName(), resolved.content());

        work.includes.add(new PreprocessResult.IncludeSummary(requestedPath, resolved.identity(), directiveRange, true));
        includeStack.add(resolved.identity());
        int sourceMapStart = work.sourceMap.size();
        StringBuilder includeOutput = new StringBuilder();
        preprocessor.expandSource(includeFile, resolved.currentDirectory(), includeStack, work, includeOutput, false);
        if (mapToThisSource) {
            for (int index = sourceMapStart; index < work.sourceMap.size(); index++) {
                work.sourceMap.set(index, startOffset);
            }
        }
        String includeContent = includeOutput.toString();
        validateHeader(includeFile, includeContent, work);
        output.append(includeContent);
        if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
            output.append('\n');
            work.sourceMap.add(mapToThisSource ? startOffset : -1);
        }
        includeStack.remove(resolved.identity());
    }

    private void validateHeader(SourceFile originalHeader, String content, Preprocessor.Work work) {
        SourceFile headerSource = new SourceFile(originalHeader.path(), content);
        Lexer lexer = new Lexer(headerSource, work.options.languageMode());
        lexer.lex();
        if (!lexer.errors().isEmpty()) {
            work.diagnostics.add(Preprocessor.diagnostic(
                    originalHeader,
                    0,
                    originalHeader.content().length(),
                    "头文件包含非法词法内容"
            ));
            return;
        }
        // Header expansion is textual: a header may depend on declarations that
        // appeared before the include, or the include itself may occur inside a
        // declaration/function body. Parsing this isolated fragment here would
        // therefore reject valid translation units. The complete preprocessed
        // source is parsed by the normal compiler pipeline.
    }

    private ResolvedInclude resolveInclude(
            Path currentDirectory,
            String requestedPath,
            List<Path> includeRoots,
            boolean systemHeader
    ) {
        ArrayList<Path> candidates = new ArrayList<>();
        if (!systemHeader) {
            if (currentDirectory != null) {
                candidates.add(currentDirectory.resolve(requestedPath));
            }
            includeRoots.stream()
                    .map(root -> root.resolve(requestedPath))
                    .forEach(candidates::add);
        }
        Path file = candidates.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
        if (file != null) {
            try {
                return new ResolvedInclude(file, file.toString(), Files.readString(file), file.getParent());
            } catch (IOException exception) {
                throw new IncludeReadException("读取 include 文件失败：" + requestedPath, exception);
            }
        }
        try {
            return systemLibraries.header(requestedPath)
                    .map(header -> {
                        Path identity = Path.of(header.resourceName()).toAbsolutePath().normalize();
                        return new ResolvedInclude(
                                identity,
                                identity.toString(),
                                header.content(),
                                identity.getParent()
                        );
                    })
                    .orElse(null);
        } catch (IllegalStateException exception) {
            throw new IncludeReadException("读取 include 文件失败：" + requestedPath, exception);
        }
    }

    private record ResolvedInclude(Path identity, String sourceName, String content, Path currentDirectory) {
    }

    private static final class IncludeReadException extends RuntimeException {
        private IncludeReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
