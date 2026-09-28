package minic.uiapi;

import minic.compiler.lexer.LexerResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParserResult;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.PreprocessResult;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.diagnostics.Diagnostic;
import minic.compiler.SourceFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * UI clients use this facade for realtime MiniC source analysis and source tokenization.
 *
 * <p>The API is JavaFX-free so the local JavaFX shell can depend on a stable in-process DTO boundary.</p>
 */
public final class MiniCRealtimeAnalysisApi {
    /**
     * Analyze source text for editor diagnostics and syntax tokens.
     *
     * @param sourceName source name
     * @param sourceText source text
     * @param version caller supplied version
     * @return realtime analysis DTO
     */
    public UiRealtimeAnalysisDto analyze(String sourceName, String sourceText, long version) {
        return analyzeInterruptibly(sourceName, sourceText, version, () -> false).orElseThrow();
    }

    /**
     * Analyze source text while allowing a realtime caller to abandon an obsolete version between compiler stages.
     *
     * @param sourceName source name
     * @param sourceText source text
     * @param version caller supplied version
     * @param invalidated returns {@code true} when a newer source version exists
     * @return the completed analysis, or empty when the source changed during analysis
     */
    public Optional<UiRealtimeAnalysisDto> analyzeInterruptibly(
            String sourceName,
            String sourceText,
            long version,
            BooleanSupplier invalidated
    ) {
        Objects.requireNonNull(invalidated, "invalidated");
        SourceFile sourceFile = new SourceFile(sourceName, sourceText);
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        LexerResult lexResult = new Lexer(sourceFile).lex();
        if (invalidated.getAsBoolean()) {
            return Optional.empty();
        }
        List<UiLexerTokenVisualDto> tokens = tokensFrom(sourceFile, lexResult);
        Preprocessor preprocessor = new Preprocessor();
        PreprocessResult preprocessResult = preprocessor.preprocess(sourceFile);
        if (invalidated.getAsBoolean()) {
            return Optional.empty();
        }
        diagnostics.addAll(preprocessResult.diagnostics());
        if (diagnostics.isEmpty()) {
            LexerResult preprocessedLexerResult = new Lexer(preprocessor).lex();
            if (invalidated.getAsBoolean()) {
                return Optional.empty();
            }
            diagnostics.addAll(preprocessedLexerResult.diagnostics());
            if (!diagnostics.isEmpty()) {
                return Optional.of(realtimeResult(sourceName, sourceText, diagnostics, tokens, version));
            }
            ParserResult parseResult = new Parser(preprocessedLexerResult.tokens()).parse();
            if (invalidated.getAsBoolean()) {
                return Optional.empty();
            }
            diagnostics.addAll(parseResult.diagnostics());
            if (diagnostics.isEmpty()) {
                SemanticResult semanticResult = new SemanticAnalyzer().analyze(parseResult.program());
                if (invalidated.getAsBoolean()) {
                    return Optional.empty();
                }
                diagnostics.addAll(semanticResult.diagnostics());
            }
        }
        if (invalidated.getAsBoolean()) {
            return Optional.empty();
        }
        return Optional.of(realtimeResult(sourceName, sourceText, diagnostics, tokens, version));
    }

    /**
     * Tokenize source text for UI-only syntax presentation such as guide snippets and hover panels.
     *
     * @param sourceName source name
     * @param sourceText source text
     * @return lexer tokens as UI DTOs
     */
    public List<UiLexerTokenVisualDto> tokenize(String sourceName, String sourceText) {
        SourceFile sourceFile = new SourceFile(sourceName, sourceText);
        return tokensFrom(sourceFile, new Lexer(sourceFile).lex());
    }

    private static List<UiLexerTokenVisualDto> tokensFrom(SourceFile sourceFile, LexerResult lexResult) {
        return lexResult.tokens().stream()
                .map(token -> new UiLexerTokenVisualDto(
                        token.type().name(),
                        token.lexeme(),
                        UiSourceSpanDto.from(sourceFile, token.range()),
                        false
                ))
                .toList();
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
                diagnostics.stream().map(diagnostic -> UiDiagnosticDto.from(
                        new SourceFile(sourceName, sourceText),
                        diagnostic
                )).toList(),
                tokens,
                version
        );
    }

}
