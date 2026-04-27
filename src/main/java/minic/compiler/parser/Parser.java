package minic.compiler.parser;

import minic.compiler.ast.FunctionDecl;
import minic.compiler.ast.Parameter;
import minic.compiler.ast.Program;
import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MiniC 递归下降语法分析器。
 */
public final class Parser {
    private final List<Token> tokens;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private int currentIndex;

    /**
     * 创建语法分析器。
     *
     * @param tokens lexer 产出的 token 列表，必须包含 EOF token
     */
    public Parser(List<Token> tokens) {
        this.tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
        if (this.tokens.isEmpty()) {
            throw new IllegalArgumentException("tokens must contain EOF");
        }
    }

    /**
     * 解析程序。
     *
     * @return 语法分析结果
     */
    public ParseResult parse() {
        ArrayList<FunctionDecl> functions = new ArrayList<>();
        while (!isAtEnd()) {
            FunctionDecl functionDecl = parseFunctionDecl();
            if (functionDecl != null) {
                functions.add(functionDecl);
            } else {
                synchronize();
            }
        }

        return new ParseResult(new Program(functions, programRange(functions)), diagnostics);
    }

    private FunctionDecl parseFunctionDecl() {
        Token startToken = peek();
        if (!match(TokenKind.INT)) {
            report(peek(), "期望函数声明以 int 开始");
            return null;
        }

        Token nameToken = consume(TokenKind.IDENTIFIER, "期望函数名");
        consume(TokenKind.LEFT_PAREN, "期望 '('");
        List<Parameter> parameters = parseParameters();
        consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        consume(TokenKind.LEFT_BRACE, "期望 '{'");
        Token endToken = consume(TokenKind.RIGHT_BRACE, "期望 '}'");

        if (nameToken == null || endToken == null) {
            return null;
        }
        return new FunctionDecl(
                nameToken.lexeme(),
                parameters,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endToken.range().endOffset()
                )
        );
    }

    private List<Parameter> parseParameters() {
        ArrayList<Parameter> parameters = new ArrayList<>();
        if (check(TokenKind.RIGHT_PAREN)) {
            return parameters;
        }

        do {
            Token startToken = consume(TokenKind.INT, "期望参数类型 int");
            Token nameToken = consume(TokenKind.IDENTIFIER, "期望参数名");
            if (startToken != null && nameToken != null) {
                parameters.add(new Parameter(
                        nameToken.lexeme(),
                        new SourceRange(
                                startToken.range().sourceFile(),
                                startToken.range().startOffset(),
                                nameToken.range().endOffset()
                        )
                ));
            }
        } while (match(TokenKind.COMMA));

        return parameters;
    }

    private SourceRange programRange(List<FunctionDecl> functions) {
        if (functions.isEmpty()) {
            return peek().range();
        }
        SourceRange firstRange = functions.getFirst().range();
        SourceRange lastRange = functions.getLast().range();
        return new SourceRange(firstRange.sourceFile(), firstRange.startOffset(), lastRange.endOffset());
    }

    private boolean match(TokenKind kind) {
        if (!check(kind)) {
            return false;
        }
        advance();
        return true;
    }

    private Token consume(TokenKind kind, String message) {
        if (check(kind)) {
            return advance();
        }
        report(peek(), message);
        return null;
    }

    private boolean check(TokenKind kind) {
        return peek().kind() == kind;
    }

    private Token advance() {
        if (!isAtEnd()) {
            currentIndex++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().kind() == TokenKind.EOF;
    }

    private Token peek() {
        return tokens.get(currentIndex);
    }

    private Token previous() {
        return tokens.get(currentIndex - 1);
    }

    private void report(Token token, String message) {
        diagnostics.add(new Diagnostic("PAR001", DiagnosticSeverity.ERROR, message, token.range()));
    }

    private void synchronize() {
        if (isAtEnd()) {
            return;
        }
        advance();
        while (!isAtEnd() && previous().kind() != TokenKind.RIGHT_BRACE) {
            advance();
        }
    }
}
