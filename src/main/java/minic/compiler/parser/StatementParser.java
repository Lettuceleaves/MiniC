package minic.compiler.parser;

import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.StructInitExpr;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.stmt.BreakStmt;
import minic.compiler.ast.stmt.ContinueStmt;
import minic.compiler.ast.stmt.DoWhileStmt;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.stmt.ForStmt;
import minic.compiler.ast.stmt.IfStmt;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.SwitchCase;
import minic.compiler.ast.stmt.SwitchStmt;
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
        state.enter("block");
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
            state.report(startToken.range(), "未闭合的 '{'，期望匹配的 '}'");
            return null;
        }
        BlockStmt blockStmt = new BlockStmt(
                statements,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endToken.range().endOffset()
                )
        );
        state.build(blockStmt, "BlockStmt", blockStmt.range());
        state.exit("block", blockStmt.range());
        return blockStmt;
    }

    private Statement parseStatement() {
        if (state.check(TokenKind.LEFT_BRACE)) {
            return parseBlock();
        }
        if (isTypeStart()) {
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
        if (state.check(TokenKind.DO)) {
            return parseDoWhileStmt();
        }
        if (state.check(TokenKind.FOR)) {
            return parseForStmt();
        }
        if (state.check(TokenKind.SWITCH)) {
            return parseSwitchStmt();
        }
        return parseExprStmt();
    }

    private IfStmt parseIfStmt() {
        state.enter("ifStmt");
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
        IfStmt ifStmt = new IfStmt(
                condition,
                thenBranch,
                elseBranch,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endBranch.range().endOffset()
                )
        );
        state.build(ifStmt, "IfStmt", ifStmt.range());
        state.exit("ifStmt", ifStmt.range());
        return ifStmt;
    }

    private BreakStmt parseBreakStmt() {
        Token startToken = state.consume(TokenKind.BREAK, "期望 break");
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");
        if (startToken == null || semicolonToken == null) {
            return null;
        }
        BreakStmt breakStmt = new BreakStmt(new SourceRange(
                startToken.range().sourceFile(),
                startToken.range().startOffset(),
                semicolonToken.range().endOffset()
        ));
        state.build(breakStmt, "BreakStmt", breakStmt.range());
        return breakStmt;
    }

    private ContinueStmt parseContinueStmt() {
        Token startToken = state.consume(TokenKind.CONTINUE, "期望 continue");
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");
        if (startToken == null || semicolonToken == null) {
            return null;
        }
        ContinueStmt continueStmt = new ContinueStmt(new SourceRange(
                startToken.range().sourceFile(),
                startToken.range().startOffset(),
                semicolonToken.range().endOffset()
        ));
        state.build(continueStmt, "ContinueStmt", continueStmt.range());
        return continueStmt;
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
        WhileStmt whileStmt = new WhileStmt(
                condition,
                body,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        body.range().endOffset()
                )
        );
        state.build(whileStmt, "WhileStmt", whileStmt.range());
        return whileStmt;
    }

    private DoWhileStmt parseDoWhileStmt() {
        Token startToken = state.consume(TokenKind.DO, "期望 do");
        Statement body = parseStatement();
        state.consume(TokenKind.WHILE, "期望 while");
        state.consume(TokenKind.LEFT_PAREN, "期望 '('");
        Expression condition = expressionParser.parseExpression();
        state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");

        if (startToken == null || body == null || condition == null || semicolonToken == null) {
            return null;
        }
        DoWhileStmt doWhileStmt = new DoWhileStmt(
                body,
                condition,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        semicolonToken.range().endOffset()
                )
        );
        state.build(doWhileStmt, "DoWhileStmt", doWhileStmt.range());
        return doWhileStmt;
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
        ForStmt forStmt = new ForStmt(
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
        state.build(forStmt, "ForStmt", forStmt.range());
        return forStmt;
    }

    private SwitchStmt parseSwitchStmt() {
        Token startToken = state.consume(TokenKind.SWITCH, "期望 switch");
        state.consume(TokenKind.LEFT_PAREN, "期望 '('");
        Expression selector = expressionParser.parseExpression();
        state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        state.consume(TokenKind.LEFT_BRACE, "期望 '{'");
        ArrayList<SwitchCase> cases = new ArrayList<>();
        while (!state.check(TokenKind.RIGHT_BRACE) && !state.isAtEnd()) {
            SwitchCase switchCase = parseSwitchCase();
            if (switchCase != null) {
                cases.add(switchCase);
            } else {
                state.synchronizeStatement();
            }
        }
        Token endToken = state.consume(TokenKind.RIGHT_BRACE, "期望 '}'");
        if (startToken == null || selector == null || endToken == null) {
            return null;
        }
        SwitchStmt switchStmt = new SwitchStmt(
                selector,
                cases,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endToken.range().endOffset()
                )
        );
        state.build(switchStmt, "SwitchStmt", switchStmt.range());
        return switchStmt;
    }

    private SwitchCase parseSwitchCase() {
        Token startToken;
        Expression value = null;
        if (state.match(TokenKind.CASE)) {
            startToken = state.previous();
            value = expressionParser.parseExpression();
            state.consume(TokenKind.COLON, "期望 ':'");
        } else if (state.match(TokenKind.DEFAULT)) {
            startToken = state.previous();
            state.consume(TokenKind.COLON, "期望 ':'");
        } else {
            state.report(state.peek(), "期望 case 或 default");
            return null;
        }
        ArrayList<Statement> statements = new ArrayList<>();
        while (!state.check(TokenKind.CASE)
                && !state.check(TokenKind.DEFAULT)
                && !state.check(TokenKind.RIGHT_BRACE)
                && !state.isAtEnd()) {
            Statement statement = parseStatement();
            if (statement != null) {
                statements.add(statement);
            } else {
                state.synchronizeStatement();
            }
        }
        int endOffset = statements.isEmpty()
                ? startToken.range().endOffset()
                : statements.getLast().range().endOffset();
        return new SwitchCase(
                value,
                statements,
                new SourceRange(startToken.range().sourceFile(), startToken.range().startOffset(), endOffset)
        );
    }

    private Statement parseForInitializer() {
        if (state.match(TokenKind.SEMICOLON)) {
            return null;
        }
        if (isTypeStart()) {
            return parseVarDeclStmt();
        }
        return parseExprStmt();
    }

    private VarDeclStmt parseVarDeclStmt() {
        ParsedType type = typeParser.parseType("期望变量类型 int");
        ParsedNamedType functionPointer = null;
        Token nameToken = null;
        MiniType declaredType = null;
        SourceRange declarationRange = null;
        if (type != null && state.check(TokenKind.LEFT_PAREN)) {
            functionPointer = typeParser.parseFunctionPointerDeclarator(type, "期望变量名");
            if (functionPointer != null) {
                declaredType = functionPointer.type();
                declarationRange = functionPointer.range();
            }
        } else {
            nameToken = state.consume(TokenKind.IDENTIFIER, "期望变量名");
            declaredType = type != null ? parseArraySuffix(type.type()) : null;
        }
        Expression initializer = null;
        if (state.match(TokenKind.EQUAL)) {
            if (state.check(TokenKind.LEFT_BRACE)) {
                initializer = parseStructInitializer();
            } else {
                initializer = expressionParser.parseExpression();
            }
        }
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");

        if (type == null || (nameToken == null && functionPointer == null) || semicolonToken == null) {
            return null;
        }
        String name = functionPointer != null ? functionPointer.name() : nameToken.lexeme();
        SourceRange range = declarationRange != null
                ? new SourceRange(
                        declarationRange.sourceFile(),
                        declarationRange.startOffset(),
                        semicolonToken.range().endOffset()
                )
                : new SourceRange(
                        type.range().sourceFile(),
                        type.range().startOffset(),
                        semicolonToken.range().endOffset()
                );
        VarDeclStmt varDeclStmt = new VarDeclStmt(
                name,
                declaredType,
                initializer,
                range
        );
        state.build(varDeclStmt, "VarDeclStmt " + varDeclStmt.name(), varDeclStmt.range());
        return varDeclStmt;
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

    private Expression parseStructInitializer() {
        Token startToken = state.advance();
        ArrayList<Expression> values = new ArrayList<>();
        if (!state.check(TokenKind.RIGHT_BRACE)) {
            values.add(expressionParser.parseExpression());
            while (state.match(TokenKind.COMMA)) {
                values.add(expressionParser.parseExpression());
            }
        }
        Token endToken = state.consume(TokenKind.RIGHT_BRACE, "期望 '}'");
        if (endToken == null) {
            return null;
        }
        return new StructInitExpr(
                values,
                new SourceRange(startToken.range().sourceFile(), startToken.range().startOffset(), endToken.range().endOffset())
        );
    }

    private ReturnStmt parseReturnStmt() {
        state.enter("returnStmt");
        Token startToken = state.consume(TokenKind.RETURN, "期望 return");
        Expression expression = null;
        if (!state.check(TokenKind.SEMICOLON)) {
            expression = expressionParser.parseExpression();
        }
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");

        if (startToken == null || semicolonToken == null) {
            return null;
        }
        ReturnStmt returnStmt = new ReturnStmt(
                expression,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        semicolonToken.range().endOffset()
                )
        );
        state.build(returnStmt, "ReturnStmt", returnStmt.range());
        state.exit("returnStmt", returnStmt.range());
        return returnStmt;
    }

    private ExprStmt parseExprStmt() {
        Token startToken = state.peek();
        Expression expression = expressionParser.parseExpression();
        Token semicolonToken = state.consume(TokenKind.SEMICOLON, "期望 ';'");
        if (expression == null || semicolonToken == null) {
            return null;
        }
        ExprStmt exprStmt = new ExprStmt(
                expression,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        semicolonToken.range().endOffset()
                )
        );
        state.build(exprStmt, "ExprStmt", exprStmt.range());
        return exprStmt;
    }

    private boolean isTypeStart() {
        return state.check(TokenKind.BOOL)
                || state.check(TokenKind.CHAR)
                || state.check(TokenKind.INT)
                || state.check(TokenKind.LONG)
                || state.check(TokenKind.FLOAT)
                || state.check(TokenKind.DOUBLE)
                || state.check(TokenKind.STRUCT);
    }
}
