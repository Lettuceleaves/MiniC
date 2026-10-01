package minic.compiler.parser.manager;

import minic.compiler.parser.Parser;

import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.CppInitializer;
import minic.compiler.parser.node.CppRangeForStmt;
import minic.compiler.parser.node.CppStructuredBindingDecl;
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

    public minic.compiler.parser.node.CppStaticAssertDecl parseStaticAssert(){return minic.compiler.parser.CppStaticAssertParser.parse(state,expressionManager);}
    private Statement parseStatement() {
        if(typeReader.isCpp()&&state.check(TokenType.STATIC_ASSERT))return parseStaticAssert();
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

    private Statement parseForStmt() {
        Token startToken = state.consume(TokenType.FOR, "期望 for");
        state.consume(TokenType.LEFT_PAREN, "期望 '('");
        typeReader.enterScope(java.util.List.of());
        try {
            if (typeReader.isCpp() && rangeHeader()) return parseRangeFor(startToken);
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

    /** Only a top-level non-conditional colon can delimit a range declaration. */
    private boolean rangeHeader() {
        if (!isDeclarationStart()) return false;
        int parentheses=0, brackets=0, braces=0, conditional=0;
        for (int offset=0;;offset++) {
            TokenType token=state.peekAt(offset).type();
            if (token==TokenType.EOF) return false;
            if (parentheses==0 && brackets==0 && braces==0) {
                if (token==TokenType.SEMICOLON || token==TokenType.RIGHT_PAREN) return false;
                if (token==TokenType.QUESTION) conditional++;
                if (token==TokenType.COLON) { if (conditional==0) return true; conditional--; }
            }
            switch(token) {
                case LEFT_PAREN -> parentheses++;
                case RIGHT_PAREN -> parentheses--;
                case LEFT_BRACKET -> brackets++;
                case RIGHT_BRACKET -> brackets--;
                case LEFT_BRACE -> braces++;
                case RIGHT_BRACE -> braces--;
                default -> { }
            }
        }
    }

    private Statement parseRangeFor(Token start) {
        CppStructuredBindingDecl binding=typeReader.startsStructuredBinding()?parseStructuredBinding(true):null;
        Parser.ParsedNamedType named=binding==null?typeReader.parseNamedType("期望范围变量类型", "期望范围变量名"):null;
        Token colon=state.consume(TokenType.COLON, "范围 for 声明期望 ':'");
        Expression initializer=state.check(TokenType.LEFT_BRACE)
                ? parseCppInitializer() : expressionManager.parseExpression();
        Token close=state.consume(TokenType.RIGHT_PAREN, "范围 for 期望 ')'");
        // The range initializer is resolved outside the iteration variable's scope.
        if(named!=null)typeReader.declareOrdinaryName(named.name(),named.range());
        if(binding!=null)for(var name:binding.names())typeReader.declareOrdinaryName(name.name(),name.range());
        Statement body=parseControlledStatement();
        if(start==null||(named==null&&binding==null)||colon==null||initializer==null||close==null||body==null)return null;
        if(named!=null&&named.type().isFunction())state.report(named.range(),"范围变量必须声明对象或引用");
        Statement declaration=binding!=null?binding:new VarDeclStmt(named.name(),named.type(),null,named.alignmentSpecs(),named.range());
        var result=new CppRangeForStmt(declaration,initializer,body,SourceRange.span(start.range(),body.range()));
        state.build(result,"CppRangeForStmt",result.range());
        return result;
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

    private Statement parseVarDeclStmt() {
        Token firstSpecifier=state.peek();Token storage=null;boolean constexpr=false;
        while(typeReader.isCpp()&&(state.check(TokenType.STATIC)||state.check(TokenType.CONSTEXPR))){
            Token specifier=state.advance();if(specifier.type()==TokenType.STATIC){if(storage!=null)state.report(specifier,"Repeated static specifier");storage=specifier;}
            else {if(constexpr)state.report(specifier,"Repeated constexpr specifier");constexpr=true;}
        }
        if(typeReader.startsStructuredBinding()) {
            if(storage!=null)state.report(storage,"Static structured bindings require C++20");
            if(constexpr)state.report(state.peek(),"A structured binding cannot be constexpr in C++17");
            return parseStructuredBinding(false);
        }
        var specifiers=typeReader.parseDeclarationSpecifiers("期望变量类型");
        if(specifiers==null)return null;
        var declarations=new ArrayList<Statement>();boolean first=true;
        do {
            Parser.ParsedNamedType declaration=typeReader.parseNamedDeclarator(specifiers,"期望变量名",first);
            if(declaration==null)return null;
            // Each name enters the shared scope at its own declarator, before its initializer.
            typeReader.declareOrdinaryName(declaration.name(),declaration.range());
            if(typeReader.isCpp()){
                if(declaration.type().isFunction())state.unsupportedCpp(declaration.range(),"块作用域函数声明尚未实现；空括号或参数类型列表声明函数，不会默认构造变量");
            }
            ParsedInitializer initialization=parseVariableInitializer(declaration.type(),declaration.range());
            SourceRange end=initialization.cppInitializer()!=null&&initialization.cppInitializer().kind()!=CppInitializer.Kind.DEFAULT
                    ?initialization.cppInitializer().range():initialization.expression()!=null?initialization.expression().range():declaration.range();
            SourceRange start=first?firstSpecifier.range():declaration.range();
            SourceRange range=SourceRange.span(start,end);
            if(!state.check(TokenType.COMMA)){
                Token semicolon=state.consume(TokenType.SEMICOLON,"期望 ';'");if(semicolon==null)return null;
                range=SourceRange.span(start,semicolon.range());
            }
            VarDeclStmt variable=new VarDeclStmt(declaration.name(),declaration.type(),initialization.expression(),
                    declaration.alignmentSpecs(),initialization.cppInitializer(),storage!=null,range).withConstexprSpecifier(constexpr);
            declarations.add(variable);state.build(variable,"VarDeclStmt "+variable.name(),variable.range());first=false;
            if(state.previous().type()==TokenType.SEMICOLON)break;
        }while(state.match(TokenType.COMMA));
        if(declarations.size()==1)return declarations.getFirst();
        var group=new Statement.DeclGroupStmt(declarations,SourceRange.span(firstSpecifier.range(),state.previous().range()));
        state.build(group,"DeclGroupStmt",group.range());return group;
    }

    public CppStructuredBindingDecl parseStructuredBinding(boolean rangeDeclaration) {
        Parser.ParsedType parsed=typeReader.parseStructuredBindingType();
        Token open=state.consume(TokenType.LEFT_BRACKET,"Structured binding requires '['");
        var names=new ArrayList<CppStructuredBindingDecl.BindingName>();
        do {
            Token name=state.consume(TokenType.IDENTIFIER,"Expected a structured binding name");
            if(name==null)break;
            names.add(new CppStructuredBindingDecl.BindingName(name.lexeme(),name.range()));
        } while(state.match(TokenType.COMMA));
        Token close=state.consume(TokenType.RIGHT_BRACKET,"Structured binding requires ']'");
        if(parsed==null||open==null||close==null||names.isEmpty())return null;
        if(rangeDeclaration)return new CppStructuredBindingDecl(parsed.type(),names,null,SourceRange.span(parsed.range(),close.range()));
        for(var name:names)typeReader.declareOrdinaryName(name.name(),name.range());
        ParsedInitializer initialized=parseVariableInitializer(parsed.type(),parsed.range());
        Token end=state.consume(TokenType.SEMICOLON,"Expected ';' after structured binding");
        if(end==null)return null;
        if(initialized.cppInitializer()==null||initialized.cppInitializer().kind()==CppInitializer.Kind.DEFAULT)
            state.report(close,"A structured binding requires an initializer");
        var result=new CppStructuredBindingDecl(parsed.type(),names,initialized.cppInitializer(),SourceRange.span(parsed.range(),end.range()));
        state.build(result,"CppStructuredBindingDecl",result.range());return result;
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

    public record ParsedInitializer(Expression expression, CppInitializer cppInitializer) { }

    /** Retain C++ spelling while keeping existing executable operands and default-null behavior. */
    public ParsedInitializer parseVariableInitializer(minic.compiler.type.MiniType type, SourceRange declaratorRange) {
        if (!typeReader.isCpp() || type == null) return new ParsedInitializer(parseDeclarationInitializer(type), null);
        Token start = state.peek();
        if (state.match(TokenType.EQUAL)) {
            Expression value = parseInitializer();
            if (value == null) return new ParsedInitializer(null, null);
            boolean list = value instanceof AggregateInitExpr;
            var arguments = list ? ((AggregateInitExpr) value).values() : java.util.List.of(value);
            var initializer = new CppInitializer(list ? CppInitializer.Kind.COPY_LIST : CppInitializer.Kind.COPY,
                    arguments, SourceRange.span(start.range(), value.range()));
            return new ParsedInitializer(value, initializer);
        }
        if (state.check(TokenType.LEFT_BRACE) && type.isReference()) {
            var aggregate = parseAggregateInitializer();
            if (!(aggregate instanceof AggregateInitExpr list)) return new ParsedInitializer(null, null);
            return new ParsedInitializer(list, new CppInitializer(CppInitializer.Kind.DIRECT_LIST, list.values(), list.range()));
        }
        if (state.check(TokenType.LEFT_PAREN) || state.check(TokenType.LEFT_BRACE)) {
            var initializer = parseCppInitializer();
            if (initializer == null) return new ParsedInitializer(null, null);
            Expression projection = type.isReference() && initializer.kind() == CppInitializer.Kind.DIRECT_PAREN
                    && initializer.arguments().size() == 1
                    ? new Expression.GroupingExpr(initializer.arguments().getFirst(), initializer.range()) : initializer;
            return new ParsedInitializer(projection, initializer);
        }
        var insertion = new SourceRange(declaratorRange.endLine(), declaratorRange.endByte(),
                declaratorRange.endLine(), declaratorRange.endByte());
        return new ParsedInitializer(null, new CppInitializer(CppInitializer.Kind.DEFAULT, java.util.List.of(), insertion));
    }

    /** Source-only constructor/default-member syntax; ordinary declarations retain their core form. */
    public CppInitializer parseCppInitializer() {
        Token start = state.peek();
        boolean copy = state.match(TokenType.EQUAL);
        if (copy && !state.check(TokenType.LEFT_BRACE)) {
            Expression value = expressionManager.parseAssignmentExpression();
            return value == null ? null : new CppInitializer(CppInitializer.Kind.COPY, java.util.List.of(value),
                    SourceRange.span(start.range(), value.range()));
        }
        boolean list = state.match(TokenType.LEFT_BRACE);
        if (!list && (copy || !state.match(TokenType.LEFT_PAREN))) {
            state.report(state.peek(), "初始化器期望 '(' 或 '{'");
            return null;
        }
        TokenType closing = list ? TokenType.RIGHT_BRACE : TokenType.RIGHT_PAREN;
        ArrayList<Expression> arguments = new ArrayList<>();
        boolean valid = true;
        if (!state.check(closing)) {
            do {
                Expression argument = state.check(TokenType.LEFT_BRACE)
                        ? parseCppInitializer() : expressionManager.parseAssignmentExpression();
                if (argument == null) { valid = false; break; }
                arguments.add(expressionManager.finishPackExpansion(argument));
                if (!state.match(TokenType.COMMA)) break;
                if (list && state.check(closing)) break;
            } while (!state.isAtEnd());
        }
        Token end = state.consume(closing, list ? "初始化列表期望 '}'" : "初始化参数期望 ')'");
        if (!valid || end == null) return null;
        var kind = copy ? CppInitializer.Kind.COPY_LIST
                : list ? CppInitializer.Kind.DIRECT_LIST : CppInitializer.Kind.DIRECT_PAREN;
        return new CppInitializer(kind, arguments, SourceRange.span(start.range(), end.range()));
    }

    /** Reference-only C++ direct/list initialization shares the ordinary initializer AST. */
    public Expression parseDeclarationInitializer(minic.compiler.type.MiniType type) {
        if (state.match(TokenType.EQUAL)) return parseInitializer();
        if (!typeReader.isCpp() || type == null || !type.isReference()) return null;
        if (state.check(TokenType.LEFT_BRACE)) return parseAggregateInitializer();
        if (!state.match(TokenType.LEFT_PAREN)) return null;
        Token start = state.previous();
        Expression expression = expressionManager.parseAssignmentExpression();
        Token end = state.consume(TokenType.RIGHT_PAREN, "引用直接初始化期望单个表达式和 ')'");
        return expression == null || end == null ? null
                : new Expression.GroupingExpr(expression, SourceRange.span(start.range(), end.range()));
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
            Expression value = state.check(TokenType.LEFT_BRACE)
                    ? parseAggregateInitializer()
                    : expressionManager.parseAssignmentExpression();
            return expressionManager.finishPackExpansion(value);
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
            expression = state.languageMode() == minic.compiler.LanguageMode.CPP17_ALGORITHM && state.check(TokenType.LEFT_BRACE)
                    ? expressionManager.parseInitializerClause() : expressionManager.parseExpression();
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
        return typeReader.isCpp() && (state.check(TokenType.STATIC)||state.check(TokenType.CONSTEXPR)) || state.check(TokenType.ALIGNAS) || typeReader.canStartType() && !typeReader.startsCppConstructionStatement();
    }
}
