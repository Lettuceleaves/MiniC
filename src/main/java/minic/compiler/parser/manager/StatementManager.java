package minic.compiler.parser.manager;

import minic.compiler.parser.Parser;

import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.AggregateInitExpr;
import minic.compiler.parser.node.Expression.DesignatedInitExpr;
import minic.compiler.parser.node.Expression.Designator;
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
import minic.compiler.parser.node.Statement.TypedefStmt;
import minic.compiler.parser.node.Statement.WhileStmt;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.SourceRange;

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
        return parseBlock(java.util.List.of());
    }

    public BlockStmt parseFunctionBlock(java.util.List<String> parameterNames) {
        return parseBlock(parameterNames);
    }

    private BlockStmt parseBlock(java.util.List<String> initialOrdinaryNames) {
        state.enter("block");
        Token startToken = state.consume(TokenType.LEFT_BRACE, "期望 '{'");
        if (startToken == null) {
            return null;
        }
        typeReader.enterScope(initialOrdinaryNames);
        try {
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
        } finally {
            typeReader.exitScope();
        }
    }

    private Statement parseStatement() {
        if (typeReader.isCpp() && state.check(TokenType.USING)) {
            var declaration = minic.compiler.parser.CppNameParser.parseUsing(state);
            if (declaration != null) typeReader.registerUsing(declaration);
            return declaration;
        }
        if (state.check(TokenType.LEFT_BRACE)) {
            return parseBlock();
        }
        if (state.check(TokenType.TYPEDEF)) {
            return parseTypedefStmt();
        }
        if (isDeclarationStart()) {
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
        Statement thenBranch = parseControlledStatement();
        Statement elseBranch = null;
        if (state.match(TokenType.ELSE)) {
            elseBranch = parseControlledStatement();
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
        Statement body = parseControlledStatement();

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
        Statement body = parseControlledStatement();
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
        typeReader.enterScope(java.util.List.of());
        try {
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
            Statement body = parseControlledStatement();

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
        } finally {
            typeReader.exitScope();
        }
    }

    private SwitchStmt parseSwitchStmt() {
        Token startToken = state.consume(TokenType.SWITCH, "期望 switch");
        state.consume(TokenType.LEFT_PAREN, "期望 '('");
        Expression selector = expressionManager.parseExpression();
        state.consume(TokenType.RIGHT_PAREN, "期望 ')'");
        state.consume(TokenType.LEFT_BRACE, "期望 '{'");
        if (typeReader.isCpp()) typeReader.enterScope(java.util.List.of());
        try {
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
        } finally {
            if (typeReader.isCpp()) typeReader.exitScope();
        }
    }

    /** A C++ controlled declaration has block scope even without explicit braces. */
    private Statement parseControlledStatement() {
        if (!typeReader.isCpp() || state.check(TokenType.LEFT_BRACE)) return parseStatement();
        typeReader.enterScope(java.util.List.of());
        try {
            return parseStatement();
        } finally {
            typeReader.exitScope();
        }
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
        if (state.check(TokenType.TYPEDEF)) {
            return parseTypedefStmt();
        }
        if (isDeclarationStart()) {
            return parseVarDeclStmt();
        }
        return parseExprStmt();
    }

    private VarDeclStmt parseVarDeclStmt() {
        Parser.ParsedNamedType declaration = typeReader.parseNamedType("期望变量类型", "期望变量名");
        if (typeReader.isCpp() && declaration != null) {
            typeReader.declareOrdinaryName(declaration.name(), declaration.range());
        }
        Expression initializer = null;
        if (state.match(TokenType.EQUAL)) {
            if (state.check(TokenType.LEFT_BRACE)) {
                initializer = parseAggregateInitializer();
            } else {
                initializer = expressionManager.parseAssignmentExpression();
            }
        }
        Token semicolonToken = state.consume(TokenType.SEMICOLON, "期望 ';'");

        if (declaration == null || semicolonToken == null) {
            return null;
        }
        VarDeclStmt varDeclStmt = new VarDeclStmt(
                declaration.name(),
                declaration.type(),
                initializer,
                declaration.alignmentSpecs(),
                SourceRange.span(declaration.range(), semicolonToken.range())
        );
        if (!typeReader.isCpp()) typeReader.declareOrdinaryName(varDeclStmt.name(), varDeclStmt.range());
        state.build(varDeclStmt, "VarDeclStmt " + varDeclStmt.name(), varDeclStmt.range());
        return varDeclStmt;
    }

    private TypedefStmt parseTypedefStmt() {
        Token start = state.consume(TokenType.TYPEDEF, "期望 typedef");
        Parser.ParsedNamedType declaration = typeReader.parseNamedType("期望 typedef 类型", "期望 typedef 名称");
        Token semicolon = state.consume(TokenType.SEMICOLON, "期望 ';'");
        if (start == null || declaration == null || semicolon == null) {
            return null;
        }
        if (!declaration.alignmentSpecs().isEmpty()) {
            state.report(declaration.range(), "typedef 声明不能使用 alignas");
        }
        SourceRange range = SourceRange.span(start.range(), semicolon.range());
        typeReader.defineTypedef(declaration.name(), declaration.type(), range);
        TypedefStmt statement = new TypedefStmt(declaration.name(), declaration.type(), range);
        state.build(statement, "TypedefStmt " + statement.name(), range);
        return statement;
    }

    public Expression parseInitializer() {
        return state.check(TokenType.LEFT_BRACE)
                ? parseAggregateInitializer()
                : expressionManager.parseAssignmentExpression();
    }

    private Expression parseAggregateInitializer() {
        Token startToken = state.advance();
        ArrayList<Expression> values = new ArrayList<>();
        if (!state.check(TokenType.RIGHT_BRACE)) {
            values.add(parseInitializerElement());
            while (state.match(TokenType.COMMA)) {
                if (state.check(TokenType.RIGHT_BRACE)) break;
                values.add(parseInitializerElement());
            }
        }
        Token endToken = state.consume(TokenType.RIGHT_BRACE, "期望 '}'");
        if (endToken == null) {
            return null;
        }
        return new AggregateInitExpr(
                values,
                SourceRange.span(startToken.range(), endToken.range())
        );
    }

    private Expression parseInitializerElement() {
        if (!state.check(TokenType.DOT) && !state.check(TokenType.LEFT_BRACKET)) {
            return state.check(TokenType.LEFT_BRACE)
                    ? parseAggregateInitializer()
                    : expressionManager.parseAssignmentExpression();
        }
        ArrayList<Designator> designators = new ArrayList<>();
        SourceRange start = state.peek().range();
        while (state.match(TokenType.DOT) || state.match(TokenType.LEFT_BRACKET)) {
            Token opener = state.previous();
            if (opener.type() == TokenType.DOT) {
                Token field = state.consume(TokenType.IDENTIFIER, "期望指定初始化字段名");
                if (field == null) return null;
                designators.add(new Designator.Field(field.lexeme(), SourceRange.span(opener.range(), field.range())));
            } else {
                Token index = state.consume(TokenType.INTEGER_LITERAL, "期望指定初始化数组下标");
                Token close = state.consume(TokenType.RIGHT_BRACKET, "期望 ']'");
                if (index == null || close == null || !(index.literalValue() instanceof Integer integer) || integer < 0) {
                    state.report(opener, "指定初始化数组下标必须是非负 int 常量");
                    return null;
                }
                designators.add(new Designator.Index(integer, SourceRange.span(opener.range(), close.range())));
            }
        }
        state.consume(TokenType.EQUAL, "指定初始化器需要 '='");
        Expression value = state.check(TokenType.LEFT_BRACE)
                ? parseAggregateInitializer()
                : expressionManager.parseAssignmentExpression();
        if (value == null) return null;
        return new DesignatedInitExpr(designators, value, SourceRange.span(start, value.range()));
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

    private boolean isDeclarationStart() {
        return state.check(TokenType.ALIGNAS) || typeReader.canStartType();
    }
}
