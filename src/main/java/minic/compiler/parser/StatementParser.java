package minic.compiler.parser;

import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.source.SourceRange;

import java.util.ArrayList;

final class StatementParser {
    private final ParserState state;
    private final ExpressionParser expressionParser;

    StatementParser(ParserState state, ExpressionParser expressionParser) {
        this.state = state;
        this.expressionParser = expressionParser;
    }

    BlockStmt parseBlock() {
        Token startToken = state.consume(TokenKind.LEFT_BRACE, "期望 '{'");
        if (startToken == null) {
            return null;
        }

        ArrayList<Statement> statements = new ArrayList<>();
        while (!state.check(TokenKind.RIGHT_BRACE) && !state.isAtEnd()) {
            Statement statement = parseStatement();
            if (statement != null) {
                statements.add(statement);
            } else {
                state.synchronizeStatement();
            }
        }

        Token endToken = state.consume(TokenKind.RIGHT_BRACE, "期望 '}'");
        if (endToken == null) {
            return null;
        }
        return new BlockStmt(
                statements,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endToken.range().endOffset()
                )
        );
    }

    private Statement parseStatement() {
        if (state.check(TokenKind.LEFT_BRACE)) {
            return parseBlock();
        }
        if (state.check(TokenKind.INT)) {
            return parseVarDeclStmt();
        }
        if (state.check(TokenKind.RETURN)) {
            return parseReturnStmt();
        }
        if (state.check(TokenKind.IF)) {
            return parseIfStmt();
        }
        return parseExprStmt();
    }

    private IfStmt parseIfStmt() {
        Token startToken = state.consume(TokenKind.IF, "期望 if");
        state.consume(TokenKind.LEFT_PAREN, "期望 '('");
        Expression condition = expressionParser.parseExpression();
        state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        Statement thenBranch = parseStatement();
        Statement elseBranch = null;
        if (state.match(TokenKind.ELSE)) {
            elseBranch = parseStatement();
        }

        if (startToken == null || condition == null || thenBranch == null) {
            return null;
        }
        Statement endBranch = elseBranch != null ? elseBranch : thenBranch;
        return new IfStmt(
                condition,
                thenBranch,
                elseBranch,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endBranch.range().endOffset()
                )
        );
    }

    private VarDeclStmt parseVarDeclStmt() {
        Token startToken = state.consume(TokenKind.INT, "期望变量类型 int");
        Token nameToken = state.consume(TokenKind.IDENTIFIER, "期望变量名");
        Expression initializer = null;
        if (state.match(TokenKind.EQUAL)) {
            initializer = expressionParser.parseExpression();
        }
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");

        if (startToken == null || nameToken == null || semicolonToken == null) {
            return null;
        }
        return new VarDeclStmt(
                nameToken.lexeme(),
                initializer,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        semicolonToken.range().endOffset()
                )
        );
    }

    private ReturnStmt parseReturnStmt() {
        Token startToken = state.consume(TokenKind.RETURN, "期望 return");
        Expression expression = null;
        if (!state.check(TokenKind.SEMICOLON)) {
            expression = expressionParser.parseExpression();
        }
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");

        if (startToken == null || semicolonToken == null) {
            return null;
        }
        return new ReturnStmt(
                expression,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        semicolonToken.range().endOffset()
                )
        );
    }

    private ExprStmt parseExprStmt() {
        Token startToken = state.peek();
        Expression expression = expressionParser.parseExpression();
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");
        if (expression == null || semicolonToken == null) {
            return null;
        }
        return new ExprStmt(
                expression,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        semicolonToken.range().endOffset()
                )
        );
    }
}
