package minic.compiler.parser.manager;

import minic.compiler.parser.Parser;

import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.StructInitExpr;
import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.parser.node.Statement.BreakStmt;
import minic.compiler.parser.node.Statement.ContinueStmt;
import minic.compiler.parser.node.Statement.DoWhileStmt;
import minic.compiler.parser.node.Statement.ExprStmt;
import minic.compiler.parser.node.Statement.ForStmt;
import minic.compiler.parser.node.Statement.IfStmt;
import minic.compiler.parser.node.Statement.ReturnStmt;
import minic.compiler.parser.node.Statement;
import minic.compiler.parser.node.Statement.SwitchCase;
import minic.compiler.parser.node.Statement.SwitchStmt;
import minic.compiler.parser.node.Statement.VarDeclStmt;
import minic.compiler.parser.node.Statement.WhileStmt;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.ArrayList;

public final class StatementManager {
    private final Parser.Context state;
    private final ExpressionManager expressionManager;
    private final Parser.TypeReader typeReader;

    public StatementManager(
            Parser.Context state,
            ExpressionManager expressionManager,
            Parser.TypeReader typeReader
    ) {
        this.state = state;
        this.expressionManager = expressionManager;
        this.typeReader = typeReader;
    }

    public BlockStmt parseBlock() {
        state.enter("block");
        Token startToken = state.consume(TokenType.LEFT_BRACE, "期望 '{'");
        if (startToken == null) {
            return null;
        }

        ArrayList<Statement> statements = new ArrayList<>();
        while (!state.check(TokenType.RIGHT_BRACE) && !state.isAtEnd()) {
            Statement statement = parseStatement();
            if (statement != null) {
                statements.add(statement);
            } else {
                state.synchronizeStatement();
            }
        }

        Token endToken = state.consume(TokenType.RIGHT_BRACE, "期望 '}'");
        if (endToken == null) {
            state.report(startToken.range(), "未闭合的 '{'，期望匹配的 '}'");
            return null;
        }
        BlockStmt blockStmt = new BlockStmt(
                statements,
                SourceRange.span(startToken.range(), endToken.range())
        );
        state.build(blockStmt, "BlockStmt", blockStmt.range());
        state.exit("block", blockStmt.range());
        return blockStmt;
    }

    private Statement parseStatement() {
        if (state.check(TokenType.LEFT_BRACE)) {
            return parseBlock();
        }
        if (isTypeStart()) {
            return parseVarDeclStmt();
        }
        if (state.check(TokenType.RETURN)) {
            return parseReturnStmt();
        }
        if (state.check(TokenType.BREAK)) {
            return parseBreakStmt();
        }
        if (state.check(TokenType.CONTINUE)) {
            return parseContinueStmt();
        }
        if (state.check(TokenType.IF)) {
            return parseIfStmt();
        }
        if (state.check(TokenType.WHILE)) {
            return parseWhileStmt();
        }
        if (state.check(TokenType.DO)) {
            return parseDoWhileStmt();
        }
        if (state.check(TokenType.FOR)) {
            return parseForStmt();
        }
        if (state.check(TokenType.SWITCH)) {
            return parseSwitchStmt();
        }
        return parseExprStmt();
    }

    private IfStmt parseIfStmt() {
        state.enter("ifStmt");
        Token startToken = state.consume(TokenType.IF, "期望 if");
        state.consume(TokenType.LEFT_PAREN, "期望 '('");
        Expression condition = expressionManager.parseExpression();
        state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
        Statement thenBranch = parseStatement();
        Statement elseBranch = null;
        if (state.match(TokenType.ELSE)) {
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
                SourceRange.span(startToken.range(), endBranch.range())
        );
        state.build(ifStmt, "IfStmt", ifStmt.range());
        state.exit("ifStmt", ifStmt.range());
        return ifStmt;
    }

    private BreakStmt parseBreakStmt() {
        Token startToken = state.consume(TokenType.BREAK, "期望 break");
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (startToken == null || semicolonToken == null) {
            return null;
        }
        BreakStmt breakStmt = new BreakStmt(SourceRange.span(startToken.range(), semicolonToken.range()));
        state.build(breakStmt, "BreakStmt", breakStmt.range());
        return breakStmt;
    }

    private ContinueStmt parseContinueStmt() {
        Token startToken = state.consume(TokenType.CONTINUE, "期望 continue");
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (startToken == null || semicolonToken == null) {
            return null;
        }
        ContinueStmt continueStmt = new ContinueStmt(SourceRange.span(startToken.range(), semicolonToken.range()));
        state.build(continueStmt, "ContinueStmt", continueStmt.range());
        return continueStmt;
    }

    private WhileStmt parseWhileStmt() {
        Token startToken = state.consume(TokenType.WHILE, "期望 while");
        state.consume(TokenType.LEFT_PAREN, "期望 '('");
        Expression condition = expressionManager.parseExpression();
        state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
        Statement body = parseStatement();

        if (startToken == null || condition == null || body == null) {
            return null;
        }
        WhileStmt whileStmt = new WhileStmt(
                condition,
                body,
                SourceRange.span(startToken.range(), body.range())
        );
        state.build(whileStmt, "WhileStmt", whileStmt.range());
        return whileStmt;
    }

    private DoWhileStmt parseDoWhileStmt() {
        Token startToken = state.consume(TokenType.DO, "期望 do");
        Statement body = parseStatement();
        state.consume(TokenType.WHILE, "期望 while");
        state.consume(TokenType.LEFT_PAREN, "期望 '('");
        Expression condition = expressionManager.parseExpression();
        state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");

        if (startToken == null || body == null || condition == null || semicolonToken == null) {
            return null;
        }
        DoWhileStmt doWhileStmt = new DoWhileStmt(
                body,
                condition,
                SourceRange.span(startToken.range(), semicolonToken.range())
        );
        state.build(doWhileStmt, "DoWhileStmt", doWhileStmt.range());
        return doWhileStmt;
    }

    private ForStmt parseForStmt() {
        Token startToken = state.consume(TokenType.FOR, "期望 for");
        state.consume(TokenType.LEFT_PAREN, "期望 '('");
        Statement initializer = parseForInitializer();
        Expression condition = null;
        if (!state.check(TokenType.SEMICOLON)) {
            condition = expressionManager.parseExpression();
        }
        state.consume(TokenType.SEMICOLON, "期望 ';'");
        Expression step = null;
        if (!state.check(TokenType.RIGHT_PAREN)) {
            step = expressionManager.parseExpression();
        }
        state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
        Statement body = parseStatement();

        if (startToken == null || body == null) {
            return null;
        }
        ForStmt forStmt = new ForStmt(
                initializer,
                condition,
                step,
                body,
                SourceRange.span(startToken.range(), body.range())
        );
        state.build(forStmt, "ForStmt", forStmt.range());
        return forStmt;
    }

    private SwitchStmt parseSwitchStmt() {
        Token startToken = state.consume(TokenType.SWITCH, "期望 switch");
        state.consume(TokenType.LEFT_PAREN, "期望 '('");
        Expression selector = expressionManager.parseExpression();
        state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
        state.consume(TokenType.LEFT_BRACE, "期望 '{'");
        ArrayList<SwitchCase> cases = new ArrayList<>();
        while (!state.check(TokenType.RIGHT_BRACE) && !state.isAtEnd()) {
            SwitchCase switchCase = parseSwitchCase();
            if (switchCase != null) {
                cases.add(switchCase);
            } else {
                state.synchronizeStatement();
            }
        }
        Token endToken = state.consume(TokenType.RIGHT_BRACE, "期望 '}'");
        if (startToken == null || selector == null || endToken == null) {
            return null;
        }
        SwitchStmt switchStmt = new SwitchStmt(
                selector,
                cases,
                SourceRange.span(startToken.range(), endToken.range())
        );
        state.build(switchStmt, "SwitchStmt", switchStmt.range());
        return switchStmt;
    }

    private SwitchCase parseSwitchCase() {
        Token startToken;
        Expression value = null;
        if (state.match(TokenType.CASE)) {
            startToken = state.previous();
            value = expressionManager.parseExpression();
            state.consume(TokenType.COLON, "期望 ':'");
        } else if (state.match(TokenType.DEFAULT)) {
            startToken = state.previous();
            state.consume(TokenType.COLON, "期望 ':'");
        } else {
            state.report(state.peek(), "期望 case 或 default");
            return null;
        }
        ArrayList<Statement> statements = new ArrayList<>();
        while (!state.check(TokenType.CASE)
                && !state.check(TokenType.DEFAULT)
                && !state.check(TokenType.RIGHT_BRACE)
                && !state.isAtEnd()) {
            Statement statement = parseStatement();
            if (statement != null) {
                statements.add(statement);
            } else {
                state.synchronizeStatement();
            }
        }
        SourceRange endRange = statements.isEmpty()
                ? startToken.range()
                : statements.getLast().range();
        return new SwitchCase(
                value,
                statements,
                SourceRange.span(startToken.range(), endRange)
        );
    }

    private Statement parseForInitializer() {
        if (state.match(TokenType.SEMICOLON)) {
            return null;
        }
        if (isTypeStart()) {
            return parseVarDeclStmt();
        }
        return parseExprStmt();
    }

    private VarDeclStmt parseVarDeclStmt() {
        Parser.ParsedType type = typeReader.parseType("期望变量类型 int");
        Parser.ParsedNamedType functionPointer = null;
        Token nameToken = null;
        MiniType declaredType = null;
        SourceRange declarationRange = null;
        if (type != null && state.check(TokenType.LEFT_PAREN)) {
            functionPointer = typeReader.parseFunctionPointerDeclarator(type, "期望变量名");
            if (functionPointer != null) {
                declaredType = functionPointer.type();
                declarationRange = functionPointer.range();
            }
        } else {
            nameToken = state.consume(TokenType.IDENTIFIER, "期望变量名");
            declaredType = type != null ? parseArraySuffix(type.type()) : null;
        }
        Expression initializer = null;
        if (state.match(TokenType.EQUAL)) {
            if (state.check(TokenType.LEFT_BRACE)) {
                initializer = parseStructInitializer();
            } else {
                initializer = expressionManager.parseExpression();
            }
        }
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");

        if (type == null || (nameToken == null && functionPointer == null) || semicolonToken == null) {
            return null;
        }
        String name = functionPointer != null ? functionPointer.name() : nameToken.lexeme();
        SourceRange range = declarationRange != null
                ? SourceRange.span(declarationRange, semicolonToken.range())
                : SourceRange.span(type.range(), semicolonToken.range());
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
        if (!state.match(TokenType.LEFT_BRACKET)) {
            return baseType;
        }
        Token lengthToken = state.consume(TokenType.INTEGER_LITERAL, "期望数组长度");
        Token endToken = state.consume(TokenType.RIGHT_BRACKET, "期望 ']'");
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
        if (!state.check(TokenType.RIGHT_BRACE)) {
            values.add(expressionManager.parseExpression());
            while (state.match(TokenType.COMMA)) {
                values.add(expressionManager.parseExpression());
            }
        }
        Token endToken = state.consume(TokenType.RIGHT_BRACE, "期望 '}'");
        if (endToken == null) {
            return null;
        }
        return new StructInitExpr(
                values,
                SourceRange.span(startToken.range(), endToken.range())
        );
    }

    private ReturnStmt parseReturnStmt() {
        state.enter("returnStmt");
        Token startToken = state.consume(TokenType.RETURN, "期望 return");
        Expression expression = null;
        if (!state.check(TokenType.SEMICOLON)) {
            expression = expressionManager.parseExpression();
        }
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");

        if (startToken == null || semicolonToken == null) {
            return null;
        }
        ReturnStmt returnStmt = new ReturnStmt(
                expression,
                SourceRange.span(startToken.range(), semicolonToken.range())
        );
        state.build(returnStmt, "ReturnStmt", returnStmt.range());
        state.exit("returnStmt", returnStmt.range());
        return returnStmt;
    }

    private ExprStmt parseExprStmt() {
        Token startToken = state.peek();
        Expression expression = expressionManager.parseExpression();
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (expression == null || semicolonToken == null) {
            return null;
        }
        ExprStmt exprStmt = new ExprStmt(
                expression,
                SourceRange.span(startToken.range(), semicolonToken.range())
        );
        state.build(exprStmt, "ExprStmt", exprStmt.range());
        return exprStmt;
    }

    private boolean isTypeStart() {
        return state.check(TokenType.BOOL)
                || state.check(TokenType.CHAR)
                || state.check(TokenType.INT)
                || state.check(TokenType.LONG)
                || state.check(TokenType.FLOAT)
                || state.check(TokenType.DOUBLE)
                || state.check(TokenType.STRUCT);
    }
}
