package minic.compiler.lexer;

import minic.diagnostics.Diagnostic;

import java.util.Objects;
import java.util.Optional;

/**
 * Lexer 正向推进一步的产物。
 *
 * @param token 本步产出的 token；没有时为 {@code null}
 * @param diagnostic 本步产出的 diagnostic；没有时为 {@code null}
 */
public record LexStep(Token token, Diagnostic diagnostic) {
    /**
     * 创建 lexer 步骤产物。每一步必须恰好产出 token 或 diagnostic。
     *
     * @param token 本步产出的 token；没有时为 {@code null}
     * @param diagnostic 本步产出的 diagnostic；没有时为 {@code null}
     */
    public LexStep {
        if ((token == null) == (diagnostic == null)) {
            throw new IllegalArgumentException("lex step must contain exactly one token or diagnostic");
        }
    }

    /**
     * 创建 token 步骤。
     *
     * @param token token
     * @return lexer 步骤产物
     */
    public static LexStep token(Token token) {
        return new LexStep(Objects.requireNonNull(token, "token"), null);
    }

    /**
     * 创建 diagnostic 步骤。
     *
     * @param diagnostic diagnostic
     * @return lexer 步骤产物
     */
    public static LexStep diagnostic(Diagnostic diagnostic) {
        return new LexStep(null, Objects.requireNonNull(diagnostic, "diagnostic"));
    }

    /**
     * 返回 token。
     *
     * @return token Optional
     */
    public Optional<Token> tokenOptional() {
        return Optional.ofNullable(token);
    }

    /**
     * 返回 diagnostic。
     *
     * @return diagnostic Optional
     */
    public Optional<Diagnostic> diagnosticOptional() {
        return Optional.ofNullable(diagnostic);
    }
}
