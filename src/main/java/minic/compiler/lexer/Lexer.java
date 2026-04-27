package minic.compiler.lexer;

import minic.source.SourceFile;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MiniC 词法分析器。
 */
public final class Lexer {
    private final SourceFile sourceFile;
    private final List<Token> tokens = new ArrayList<>();
    private int currentOffset;

    /**
     * 创建词法分析器。
     *
     * @param sourceFile 待分析源码文件
     */
    public Lexer(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
    }

    /**
     * 执行词法分析。
     *
     * @return 词法分析结果
     */
    public LexResult lex() {
        while (!isAtEnd()) {
            int startOffset = currentOffset;
            char character = advance();
            switch (character) {
                case ' ', '\r', '\t', '\n' -> {
                    // 空白由 lexer 跳过，不产出 token。
                }
                case '+' -> addToken(TokenKind.PLUS, startOffset);
                case '-' -> addToken(TokenKind.MINUS, startOffset);
                case '*' -> addToken(TokenKind.STAR, startOffset);
                case '/' -> addToken(TokenKind.SLASH, startOffset);
                case '=' -> addToken(TokenKind.EQUAL, startOffset);
                case '(' -> addToken(TokenKind.LEFT_PAREN, startOffset);
                case ')' -> addToken(TokenKind.RIGHT_PAREN, startOffset);
                case '{' -> addToken(TokenKind.LEFT_BRACE, startOffset);
                case '}' -> addToken(TokenKind.RIGHT_BRACE, startOffset);
                case ';' -> addToken(TokenKind.SEMICOLON, startOffset);
                case ',' -> addToken(TokenKind.COMMA, startOffset);
                default -> {
                    // 非法字符诊断在 A024 添加；当前阶段仅保证已支持 token 可被识别。
                }
            }
        }

        tokens.add(new Token(
                TokenKind.EOF,
                "",
                new SourceRange(sourceFile, currentOffset, currentOffset)
        ));
        return new LexResult(tokens, List.of());
    }

    private boolean isAtEnd() {
        return currentOffset >= sourceFile.content().length();
    }

    private char advance() {
        return sourceFile.content().charAt(currentOffset++);
    }

    private void addToken(TokenKind kind, int startOffset) {
        tokens.add(new Token(
                kind,
                sourceFile.content().substring(startOffset, currentOffset),
                new SourceRange(sourceFile, startOffset, currentOffset)
        ));
    }
}
