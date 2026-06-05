package minic.uiapi;

import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.MiniCPreprocessor;
import minic.compiler.preprocess.PreprocessResult;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.diagnostics.Diagnostic;
import minic.source.SourceFile;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JavaFX-free realtime analysis facade for editor and web callers.
 */
public final class MiniCRealtimeAnalysisApi {
    private MiniCRealtimeAnalysisApi() {
    }

    public static UiRealtimeAnalysisDto analyzeNow(String sourceName, String sourceText, long version) {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(sourceText, "sourceText");
        SourceFile sourceFile = new SourceFile(sourceName, sourceText);
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        LexResult lexResult = new Lexer(sourceFile).lex();
        List<UiLexerTokenVisualDto> tokens = lexResult.tokens().stream()
                .map(token -> new UiLexerTokenVisualDto(
                        token.kind().name(),
                        token.lexeme(),
                        UiSourceSpanDto.from(token.range()),
                        false
                ))
                .toList();
        PreprocessResult preprocessResult = new MiniCPreprocessor().preprocess(sourceFile);
        diagnostics.addAll(preprocessResult.diagnostics());
        if (diagnostics.isEmpty()) {
            LexResult preprocessedLexResult = new Lexer(preprocessResult.sourceFile()).lex();
            diagnostics.addAll(mapDiagnostics(preprocessedLexResult.diagnostics(), sourceFile, preprocessResult));
            if (!diagnostics.isEmpty()) {
                return realtimeResult(sourceName, sourceText, diagnostics, tokens, version);
            }
            ParseResult parseResult = new Parser(preprocessedLexResult.tokens()).parse();
            diagnostics.addAll(mapDiagnostics(parseResult.diagnostics(), sourceFile, preprocessResult));
            if (diagnostics.isEmpty()) {
                SemanticResult semanticResult = new SemanticAnalyzer().analyze(parseResult.program());
                diagnostics.addAll(mapDiagnostics(semanticResult.diagnostics(), sourceFile, preprocessResult));
            }
        }
        return realtimeResult(sourceName, sourceText, diagnostics, tokens, version);
    }

    private static UiRealtimeAnalysisDto realtimeResult(
            String sourceName,
            String sourceText,
            List<Diagnostic> diagnostics,
            List<UiLexerTokenVisualDto> tokens,
            long version
    ) {
        return new UiRealtimeAnalysisDto(
                sourceName,
                sourceText,
                diagnostics.stream().map(UiDiagnosticDto::from).toList(),
                tokens,
                version
        );
    }

    private static List<Diagnostic> mapDiagnostics(
            List<Diagnostic> diagnostics,
            SourceFile originalSource,
            PreprocessResult preprocessResult
    ) {
        if (diagnostics.isEmpty()) {
            return diagnostics;
        }
        int[] sourceMap = preprocessResult.sourceMap();
        return diagnostics.stream()
                .map(diagnostic -> mapDiagnostic(diagnostic, originalSource, sourceMap))
                .toList();
    }

    private static Diagnostic mapDiagnostic(Diagnostic diagnostic, SourceFile originalSource, int[] sourceMap) {
        SourceRange mappedRange = mapRange(diagnostic.range(), originalSource, sourceMap);
        return new Diagnostic(
                diagnostic.code(),
                diagnostic.severity(),
                diagnostic.message(),
                mappedRange
        );
    }

    private static SourceRange mapRange(SourceRange range, SourceFile originalSource, int[] sourceMap) {
        int sourceLength = originalSource.content().length();
        if (sourceLength == 0) {
            return new SourceRange(originalSource, 0, 0);
        }
        int start = mappedOffset(sourceMap, range.startOffset());
        int end = mappedOffset(sourceMap, Math.max(range.startOffset(), range.endOffset() - 1));
        if (start < 0 && end < 0) {
            return new SourceRange(originalSource, 0, Math.min(sourceLength, 1));
        }
        if (start < 0) {
            start = end;
        }
        if (end < 0) {
            end = start;
        }
        start = Math.max(0, Math.min(start, sourceLength));
        end = Math.max(start + 1, Math.min(Math.max(start, end) + 1, sourceLength));
        return new SourceRange(originalSource, start, end);
    }

    private static int mappedOffset(int[] sourceMap, int offset) {
        if (sourceMap.length == 0) {
            return -1;
        }
        int index = Math.max(0, Math.min(offset, sourceMap.length - 1));
        return sourceMap[index];
    }
}
