package craken.compiler.lexer;

import craken.compiler.lexer.token.Token;
import craken.compiler.Stage;

import java.util.List;
import java.util.Objects;

/**
 * 表示一次词法分析的结果。
 *
 * @param tokens 产出的 token 列表
 */
public record LexerResult(List<Token> tokens) implements Stage.Context {
    /**
     * 创建词法分析结果，并防御性复制列表。
     *
     * @param tokens 产出的 token 列表
     */
    public LexerResult {
        Objects.requireNonNull(tokens, "tokens");
        tokens = List.copyOf(tokens);
    }
}
