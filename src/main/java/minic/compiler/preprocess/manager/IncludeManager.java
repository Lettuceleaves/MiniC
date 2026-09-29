package minic.compiler.preprocess;

import minic.compiler.lexer.LexerResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParserResult;
import minic.compiler.parser.Parser;
import minic.compiler.SourceFile;
import minic.compiler.library.SystemLibraryCatalog;
import minic.source.SourceRange;

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
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*#\\s*include\\s+\"([^\"]+)\"\\s*$");
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
            expandInclude(
                    sourceFile,
                    currentDirectory,
                    includeStack,
                    work,
                    output,
                    startOffset,
                    endOffset,
                    matcher.group(1),
                    mapToThisSource
            );
            return true;
        }
        if (INCLUDE_DIRECTIVE_PATTERN.matcher(line).matches()) {
            work.diagnostics.add(Preprocessor.diagnostic(
                    sourceFile,
                    startOffset,
                    endOffset,
                    "include 指令必须使用双引号路径，例如 #include \"name.mh\""
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
            boolean mapToThisSource
    ) {
        SourceRange directiveRange = sourceFile.range(startOffset, endOffset);
        if (!requestedPath.endsWith(".mh")) {
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
            resolved = resolveInclude(currentDirectory, requestedPath, work.options.includeRoots());
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
        LexerResult lexResult = new Lexer(headerSource).lex();
        if (!lexResult.diagnostics().isEmpty()) {
            work.diagnostics.add(Preprocessor.diagnostic(
                    originalHeader,
                    0,
                    originalHeader.content().length(),
                    "头文件包含非法词法内容"
            ));
            return;
        }
        ParserResult parseResult = new Parser(lexResult.tokens()).parse();
        if (!parseResult.diagnostics().isEmpty()) {
            work.diagnostics.add(Preprocessor.diagnostic(
                    originalHeader,
                    0,
                    originalHeader.content().length(),
                    "头文件包含无法解析的顶层声明"
            ));
        }
    }

    private ResolvedInclude resolveInclude(Path currentDirectory, String requestedPath, List<Path> includeRoots) {
        ArrayList<Path> candidates = new ArrayList<>();
        if (currentDirectory != null) {
            candidates.add(currentDirectory.resolve(requestedPath));
        }
        includeRoots.stream()
                .map(root -> root.resolve(requestedPath))
                .forEach(candidates::add);
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
        return systemLibraries.header(requestedPath)
                .map(header -> {
                    Path identity = Path.of("minic-system-include", header.name())
                            .toAbsolutePath()
                            .normalize();
                    return new ResolvedInclude(identity, header.resourceName(), header.content(), null);
                })
                .orElse(null);
    }

    private record ResolvedInclude(Path identity, String sourceName, String content, Path currentDirectory) {
    }

    private static final class IncludeReadException extends RuntimeException {
        private IncludeReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
