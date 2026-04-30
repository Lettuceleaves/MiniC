package minic.compiler.parser;

import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.BreakStmt;
import minic.compiler.ast.stmt.ContinueStmt;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.ForStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.ast.stmt.WhileStmt;
import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.ArrayList;

final class StatementParser {
    private final ParserState state;
    private final ExpressionParser expressionParser;
    private final TypeParser typeParser;

    StatementParser(ParserState state, ExpressionParser expressionParser) {
        this.state = state;
        this.expressionParser = expressionParser;
        typeParser = new TypeParser(state);
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
        if (state.check(TokenKind.INT) || state.check(TokenKind.STRUCT)) {
            return parseVarDeclStmt();
        }
        if (state.check(TokenKind.RETURN)) {
            return parseReturnStmt();
        }
        if (state.check(TokenKind.BREAK)) {
            return parseBreakStmt();
        }
        if (state.check(TokenKind.CONTINUE)) {
            return parseContinueStmt();
        }
        if (state.check(TokenKind.IF)) {
            return parseIfStmt();
        }
        if (state.check(TokenKind.WHILE)) {
            return parseWhileStmt();
        }
        if (state.check(TokenKind.FOR)) {
            return parseForStmt();
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

    private BreakStmt parseBreakStmt() {
        Token startToken = state.consume(TokenKind.BREAK, "期望 break");
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");
        if (startToken == null || semicolonToken == null) {
            return null;
        }
        return new BreakStmt(new SourceRange(
                startToken.range().sourceFile(),
                startToken.range().startOffset(),
                semicolonToken.range().endOffset()
        ));
    }

    private ContinueStmt parseContinueStmt() {
        Token startToken = state.consume(TokenKind.CONTINUE, "期望 continue");
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");
        if (startToken == null || semicolonToken == null) {
            return null;
        }
        return new ContinueStmt(new SourceRange(
                startToken.range().sourceFile(),
                startToken.range().startOffset(),
                semicolonToken.range().endOffset()
        ));
    }

    private WhileStmt parseWhileStmt() {
        Token startToken = state.consume(TokenKind.WHILE, "期望 while");
        state.consume(TokenKind.LEFT_PAREN, "期望 '('");
        Expression condition = expressionParser.parseExpression();
        state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        Statement body = parseStatement();

        if (startToken == null || condition == null || body == null) {
            return null;
        }
        return new WhileStmt(
                condition,
                body,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        body.range().endOffset()
                )
        );
    }

    private ForStmt parseForStmt() {
        Token startToken = state.consume(TokenKind.FOR, "期望 for");
        state.consume(TokenKind.LEFT_PAREN, "期望 '('");
        Statement initializer = parseForInitializer();
        Expression condition = null;
        if (!state.check(TokenKind.SEMICOLON)) {
            condition = expressionParser.parseExpression();
        }
        state.consume(TokenKind.SEMICOLON, "期望 ';'");
        Expression step = null;
        if (!state.check(TokenKind.RIGHT_PAREN)) {
            step = expressionParser.parseExpression();
        }
        state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        Statement body = parseStatement();

        if (startToken == null || body == null) {
            return null;
        }
        return new ForStmt(
                initializer,
                condition,
                step,
                body,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        body.range().endOffset()
                )
        );
    }

    private Statement parseForInitializer() {
        if (state.match(TokenKind.SEMICOLON)) {
            return null;
        }
        if (state.check(TokenKind.INT) || state.check(TokenKind.STRUCT)) {
            return parseVarDeclStmt();
        }
        return parseExprStmt();
    }

    private VarDeclStmt parseVarDeclStmt() {
        ParsedType type = typeParser.parseType("期望变量类型 int");
        Token nameToken = state.consume(TokenKind.IDENTIFIER, "期望变量名");
        MiniType declaredType = type != null ? parseArraySuffix(type.type()) : null;
        Expression initializer = null;
        if (state.match(TokenKind.EQUAL)) {
            initializer = expressionParser.parseExpression();
        }
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");

        if (type == null || nameToken == null || semicolonToken == null) {
            return null;
        }
        return new VarDeclStmt(
                nameToken.lexeme(),
                declaredType,
                initializer,
                new SourceRange(
                        type.range().sourceFile(),
                        type.range().startOffset(),
                        semicolonToken.range().endOffset()
                )
        );
    }

    private MiniType parseArraySuffix(MiniType baseType) {
        if (!state.match(TokenKind.LEFT_BRACKET)) {
            return baseType;
        }
        Token lengthToken = state.consume(TokenKind.INTEGER_LITERAL, "期望数组长度");
        Token endToken = state.consume(TokenKind.RIGHT_BRACKET, "期望 ']'");
        if (lengthToken == null || endToken == null) {
            return baseType;
        }
        int length = (Integer) lengthToken.literalValue();
        if (length <= 0) {
            state.report(lengthToken, "数组长度必须大于 0");
            return baseType;
        }
        return baseType.arrayOf(length);
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
