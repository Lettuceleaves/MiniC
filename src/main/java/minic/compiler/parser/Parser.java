package minic.compiler.parser;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.Stage;
import minic.compiler.lexer.Lexer;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.manager.DeclarationManager;
import minic.compiler.parser.manager.ExpressionManager;
import minic.compiler.parser.manager.StatementManager;
import minic.compiler.parser.node.AstNode;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.parser.node.Declaration.StructDecl;
import minic.compiler.parser.node.Declaration.EnumDecl;
import minic.compiler.parser.node.Declaration.TypedefDecl;
import minic.compiler.parser.node.Declaration.GlobalVarDecl;
import minic.compiler.parser.node.Declaration;
import minic.compiler.type.MiniType;
import minic.compiler.Diagnostic;
import minic.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MiniC 递归下降语法分析器。
 *
 * <p>Parser 负责 token 游标、单步生命周期和三个 Manager 的调度；声明、
 * 语句和表达式的具体语法规则分别由对应 Manager 处理。</p>
 */
public final class Parser extends Stage {
    private final Lexer lexer;
    private final LanguageMode languageMode;
    private final boolean traceEnabled;
    private List<Token> tokens;
    private Context context;
    private DeclarationManager declarationManager;
    private TypeReader typeReader;
    private final ArrayList<Declaration> declarations = new ArrayList<>();
    private final java.util.Deque<List<Declaration>> namespaceMembers = new java.util.ArrayDeque<>();
    private final ArrayList<StructDecl> structs = new ArrayList<>();
    private final ArrayList<EnumDecl> enums = new ArrayList<>();
    private final ArrayList<TypedefDecl> typedefs = new ArrayList<>();
    private final ArrayList<GlobalVarDecl> globals = new ArrayList<>();
    private final java.util.Map<String, Long> enumConstants = new java.util.LinkedHashMap<>();
    private final ArrayList<FunctionDecl> functions = new ArrayList<>();
    private final ArrayList<AstNode> completedNodes = new ArrayList<>();

    private AstNode currentNode;
    private ParserResult parserResult;
    private boolean sourceCompleted;
    private boolean completed;
    private long stepCount;

    /** 创建不记录 trace 的 Parser。 */
    public Parser(List<Token> tokens) {
        this(tokens, false);
    }

    /** 创建 Parser，并可选择记录递归下降 trace。 */
    public Parser(List<Token> tokens, boolean traceEnabled) {
        this(tokens, LanguageMode.C, traceEnabled);
    }

    public Parser(List<Token> tokens, LanguageMode languageMode, boolean traceEnabled) {
        lexer = null;
        this.languageMode = Objects.requireNonNull(languageMode, "languageMode");
        this.traceEnabled = traceEnabled;
        initialize(tokens);
    }

    /** 创建由 Lexer 提供输入的 Parser。 */
    public Parser(Lexer lexer) {
        this(lexer, false);
    }

    /** 创建由 Lexer 提供输入的 Parser，并可选择记录递归下降 trace。 */
    public Parser(Lexer lexer, boolean traceEnabled) {
        this.lexer = Objects.requireNonNull(lexer, "lexer");
        languageMode = null; // Inherit after a standalone preprocessor has received its source/options.
        this.traceEnabled = traceEnabled;
    }

    public LanguageMode languageMode() {
        return languageMode != null ? languageMode : lexer.languageMode();
    }

    private void initialize(List<Token> sourceTokens) {
        tokens = List.copyOf(Objects.requireNonNull(sourceTokens, "tokens"));
        context = new Context(tokens, languageMode(), traceEnabled);
        typeReader = new TypeReader(context, aggregate -> {
            if (namespaceMembers.isEmpty()) structs.add(aggregate);
            else namespaceMembers.peek().add(aggregate);
        });
        ExpressionManager expressionManager = new ExpressionManager(context, typeReader, enumConstants);
        StatementManager statementManager = new StatementManager(context, expressionManager, typeReader);
        declarationManager = new DeclarationManager(context, statementManager, expressionManager, typeReader, enumConstants);
    }

    /** 循环执行 {@link #step()}，直到独立的最后空步骤发出。 */
    public ParserResult parse() {
        new CompilerApi(List.of(this)).run();
        return result();
    }

    @Override
    public boolean canNext() {
        return !completed;
    }

    /** 每次解析一个顶层声明。token 处理结束后先生成结果，下一步再发出空结束步骤。 */
    @Override
    public SourceRange step() {
        if (!canNext()) {
            throw new IllegalStateException("parser step is already completed");
        }
        ensureInitialized();
        currentNode = null;

        if (sourceCompleted) {
            completed = true;
            stepCount++;
            return finishStep(null, "", context.reportedErrors(), () -> parserResult);
        }

        if (context.isAtEnd()) {
            parserResult = buildResult();
            sourceCompleted = true;
            stepCount++;
            SourceRange range = context.peek().range();
            return finishStep(range, "COMPLETE_PARSE", context.reportedErrors(), this::currentResult);
        }

        Declaration declaration = parseDeclaration();
        if (declaration != null) {
            declarations.add(declaration);
            if (declaration instanceof TypedefDecl item) typedefs.add(item);
            else if (declaration instanceof EnumDecl item) enums.add(item);
            else if (declaration instanceof StructDecl item) structs.add(item);
            else if (declaration instanceof FunctionDecl item) functions.add(item);
            else if (declaration instanceof GlobalVarDecl item) globals.add(item);
            captureNode(declaration);
        } else {
            context.synchronizeFunction();
        }
        stepCount++;
        SourceRange range = currentNode != null
                ? currentNode.range()
                : context.peek().range();
        String operation = currentNode == null ? "RECOVER" : "PARSE_" + currentNode.getClass().getSimpleName();
        return finishStep(range, operation, context.reportedErrors(), this::currentResult);
    }

    public ParserResult result() {
        if (parserResult == null) {
            throw new IllegalStateException("parser result is not available before parsing completes");
        }
        return parserResult;
    }

    /** 返回当前已构建部分对应的结果，供观察界面生成预览。 */
    public ParserResult currentResult() {
        return parserResult != null ? parserResult : buildResult();
    }

    public List<Token> tokens() {
        return tokens == null ? List.of() : tokens;
    }

    public int currentIndex() {
        return context == null ? 0 : context.currentIndex();
    }

    public long stepCount() {
        return stepCount;
    }

    public List<AstNode> completedNodes() {
        return List.copyOf(completedNodes);
    }

    public List<TraceEvent> traceEvents() {
        return context == null ? List.of() : context.traceEvents();
    }

    private void captureNode(AstNode node) {
        currentNode = node;
        completedNodes.add(node);
    }

    private void ensureInitialized() {
        if (context != null) {
            return;
        }
        if (lexer.canNext()) {
            throw new IllegalStateException("lexer has not completed");
        }
        if (!lexer.succeeded()) {
            throw new IllegalStateException("lexer did not succeed");
        }
        initialize(lexer.toLexerResult().tokens());
    }

    private boolean isStructDeclaration() {
        return context.peekAt(1).type() == TokenType.IDENTIFIER
                && (context.peekAt(2).type() == TokenType.LEFT_BRACE
                || context.peekAt(2).type() == TokenType.SEMICOLON);
    }

    private Declaration parseDeclaration() {
        if (languageMode() == LanguageMode.CPP17_ALGORITHM) {
            if (context.check(TokenType.NAMESPACE)) return parseNamespace();
            if (context.check(TokenType.USING)) return CppNameParser.parseUsing(context);
            if (context.check(TokenType.SCOPE) || context.check(TokenType.IDENTIFIER)
                    && context.peekAt(1).type() == TokenType.SCOPE) {
                context.unsupportedCpp(context.peek().range(), "限定类型名称查找尚未实现");
                return null;
            }
        }
        if (context.check(TokenType.TYPEDEF)) return declarationManager.parseTypedefDecl();
        if (context.check(TokenType.ENUM) && context.peekAt(1).type() == TokenType.IDENTIFIER
                && context.peekAt(2).type() == TokenType.LEFT_BRACE) return declarationManager.parseEnumDecl();
        if ((context.check(TokenType.STRUCT) || context.check(TokenType.UNION)) && isStructDeclaration()) {
            return declarationManager.parseStructDecl();
        }
        return declarationManager.parseFunctionOrGlobalDecl();
    }

    private Declaration.NamespaceDecl parseNamespace() {
        Token start = context.advance();
        if (context.check(TokenType.LEFT_BRACE)) {
            context.unsupportedCpp(context.peek().range(), "匿名命名空间尚未实现");
            return null;
        }
        var name = CppNameParser.parseName(context);
        if (name == null) return null;
        if (name.global()) {
            context.report(name.range(), "命名空间定义不能以 :: 开头");
            return null;
        }
        if (context.check(TokenType.EQUAL)) {
            context.unsupportedCpp(context.peek().range(), "命名空间别名尚未实现");
            return null;
        }
        if (context.consume(TokenType.LEFT_BRACE, "期望 '{'") == null) return null;
        var members = new ArrayList<Declaration>();
        var savedConstants = new java.util.LinkedHashMap<>(enumConstants);
        typeReader.enterScope(List.of());
        namespaceMembers.push(members);
        try {
            while (!context.isAtEnd() && !context.check(TokenType.RIGHT_BRACE)) {
                int before = context.currentIndex();
                if (context.match(TokenType.SEMICOLON)) continue;
                Declaration member = parseDeclaration();
                if (member != null) members.add(member);
                else {
                    // Recovery remains inside this namespace's closing brace.
                    while (!context.isAtEnd() && !context.check(TokenType.RIGHT_BRACE)
                            && !context.check(TokenType.SEMICOLON)) context.advance();
                    context.match(TokenType.SEMICOLON);
                }
                if (context.currentIndex() == before && !context.isAtEnd()
                        && !context.check(TokenType.RIGHT_BRACE)) context.advance();
            }
            Token end = context.consume(TokenType.RIGHT_BRACE, "期望命名空间结束的 '}'");
            if (end == null) return null;
            var namespace = new Declaration.NamespaceDecl(name, members, SourceRange.span(start.range(), end.range()));
            context.build(namespace, "NamespaceDecl", namespace.range());
            return namespace;
        } finally {
            typeReader.exitScope();
            namespaceMembers.pop();
            enumConstants.clear();
            enumConstants.putAll(savedConstants);
        }
    }

    private ParserResult buildResult() {
        return new ParserResult(new Program(structs, enums, typedefs, globals, functions, declarations, programRange()));
    }

    private SourceRange programRange() {
        if (declarations.isEmpty() && structs.isEmpty()) {
            return context.peek().range();
        }
        ArrayList<SourceRange> ranges = new ArrayList<>();
        declarations.forEach(declaration -> ranges.add(declaration.range()));
        structs.forEach(declaration -> ranges.add(declaration.range()));
        enums.forEach(declaration -> ranges.add(declaration.range()));
        typedefs.forEach(declaration -> ranges.add(declaration.range()));
        globals.forEach(declaration -> ranges.add(declaration.range()));
        functions.forEach(declaration -> ranges.add(declaration.range()));
        SourceRange first = ranges.stream().min(Parser::compareRangeStart).orElseThrow();
        SourceRange last = ranges.stream().max(Parser::compareRangeEnd).orElseThrow();
        return SourceRange.span(first, last);
    }

    private static int compareRangeStart(SourceRange left, SourceRange right) {
        int line = Integer.compare(left.startLine(), right.startLine());
        return line != 0 ? line : Integer.compare(left.startByte(), right.startByte());
    }

    private static int compareRangeEnd(SourceRange left, SourceRange right) {
        int line = Integer.compare(left.endLine(), right.endLine());
        return line != 0 ? line : Integer.compare(left.endByte(), right.endByte());
    }

    /** 三个 Manager 共享的 token 游标、诊断和 trace 上下文。 */
    public static final class Context {
        private final List<Token> tokens;
        private final LanguageMode languageMode;
        private final ArrayList<Diagnostic> reportedErrors = new ArrayList<>();
        private final ArrayList<TraceEvent> traceEvents;
        private int currentIndex;
        private boolean functionBoundaryRecovered;

        private Context(List<Token> tokens, LanguageMode languageMode, boolean traceEnabled) {
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("tokens must contain EOF");
            }
            this.tokens = tokens;
            this.languageMode = languageMode;
            traceEvents = traceEnabled ? new ArrayList<>() : null;
        }

        List<Diagnostic> reportedErrors() {
            return reportedErrors;
        }

        public int currentIndex() {
            return currentIndex;
        }

        public LanguageMode languageMode() { return languageMode; }

        public void unsupportedCpp(SourceRange range, String message) {
            reportedErrors.add(new Diagnostic("CPP001", Diagnostic.Severity.ERROR, message,
                    "此语法需要后续 C++ 名称查找支持；请参照兼容能力清单。", range));
        }

        public boolean match(TokenType type) {
            if (!check(type)) {
                return false;
            }
            advance();
            return true;
        }

        public Token consume(TokenType type, String message) {
            if (check(type)) {
                return advance();
            }
            Token actual = peek();
            if (reportUnsupportedCpp(actual)) {
                return null;
            }
            reportedErrors.add(new Diagnostic(
                    "PAR001",
                    Diagnostic.Severity.ERROR,
                    message + "；实际读到 " + describeToken(actual),
                    "请在该位置补充或替换为 " + type,
                    actual.range()
            ));
            return null;
        }

        public boolean check(TokenType type) {
            return peek().type() == type;
        }

        public Token advance() {
            if (!isAtEnd()) {
                currentIndex++;
            }
            Token token = previous();
            if (traceEvents != null) {
                traceEvents.add(new TraceEvent(
                        "consume",
                        "consume " + token.type() + " " + token.lexeme(),
                        token.range(),
                        null
                ));
            }
            return token;
        }

        public boolean isAtEnd() {
            return peek().type() == TokenType.EOF;
        }

        public Token peek() {
            return tokens.get(currentIndex);
        }

        public Token peekAt(int offset) {
            int index = currentIndex + offset;
            return index >= tokens.size() ? tokens.getLast() : tokens.get(index);
        }

        public Token previous() {
            return tokens.get(currentIndex - 1);
        }

        public void report(Token token, String message) {
            if (reportUnsupportedCpp(token)) {
                return;
            }
            reportedErrors.add(new Diagnostic(
                    "PAR001",
                    Diagnostic.Severity.ERROR,
                    message + "；实际读到 " + describeToken(token),
                    parserSolution(message),
                    token.range()
            ));
        }

        public void report(SourceRange range, String message) {
            reportedErrors.add(new Diagnostic(
                    "PAR001",
                    Diagnostic.Severity.ERROR,
                    message,
                    parserSolution(message),
                    range
            ));
        }

        private boolean reportUnsupportedCpp(Token token) {
            if (languageMode != LanguageMode.CPP17_ALGORITHM || !token.type().isCppToken()) {
                return false;
            }
            reportedErrors.add(new Diagnostic("CPP001", Diagnostic.Severity.ERROR,
                    "C++ 算法兼容模式尚未支持此处的语法：" + token.lexeme(),
                    "该语法需要后续编译器支持；请参照 C++ 能力清单。", token.range()));
            return true;
        }

        private static String describeToken(Token token) {
            String lexeme = token.lexeme().replace("\n", "\\n").replace("\r", "\\r");
            return token.type() + (lexeme.isEmpty() ? "" : " '" + lexeme + "'");
        }

        private static String parserSolution(String message) {
            if (message.startsWith("期望")) {
                return "请在该位置补充或替换为" + message.substring(2);
            }
            if (message.contains("必须") || message.contains("不能") || message.contains("只能")) {
                return "请调整当前语法结构，使其满足限制：" + message;
            }
            return "请修正该位置附近的语法结构后重新解析。";
        }

        public void enter(String rule) {
            if (traceEvents != null) {
                traceEvents.add(new TraceEvent("enter", "enter " + rule, peek().range(), null));
            }
        }

        public void exit(String rule, SourceRange range) {
            if (traceEvents != null) {
                traceEvents.add(new TraceEvent("exit", "exit " + rule, range, null));
            }
        }

        public void build(AstNode node, String label, SourceRange range) {
            if (traceEvents != null) {
                traceEvents.add(new TraceEvent("build", "build " + label, range, node));
            }
        }

        public void synchronizeFunction() {
            if (functionBoundaryRecovered) {
                functionBoundaryRecovered = false;
                return;
            }
            if (isAtEnd()) {
                return;
            }
            advance();
            while (!isAtEnd()
                    && previous().type() != TokenType.RIGHT_BRACE
                    && previous().type() != TokenType.SEMICOLON) {
                advance();
            }
        }

        /**
         * 函数头后缺少左花括号时，跳过这份无法可靠解析的函数体。
         *
         * <p>从当前位置开始，成对花括号视为函数体中的嵌套块；遇到第一个没有
         * 对应左花括号的右花括号时，将它作为当前函数原本的结束位置并消费；
         * 如果先遇到明确的下一个函数头，则停在函数头之前。这样一次 Parser
         * step 就能完成恢复，避免把每条函数体语句都误当成顶层函数声明反复解析。</p>
         */
        public void synchronizeMissingFunctionBody() {
            int nestedBraceDepth = 0;
            while (!isAtEnd()) {
                if (nestedBraceDepth == 0 && isLikelyFunctionDeclarationStart()) {
                    functionBoundaryRecovered = true;
                    return;
                }
                if (check(TokenType.LEFT_BRACE)) {
                    nestedBraceDepth++;
                    advance();
                    continue;
                }
                if (check(TokenType.RIGHT_BRACE)) {
                    advance();
                    if (nestedBraceDepth == 0) {
                        functionBoundaryRecovered = true;
                        return;
                    }
                    nestedBraceDepth--;
                    continue;
                }
                advance();
            }
            functionBoundaryRecovered = true;
        }

        private boolean isLikelyFunctionDeclarationStart() {
            int offset = 0;
            if (peekAt(offset).type() == TokenType.EXTERN) {
                offset++;
            }
            TokenType type = peekAt(offset).type();
            if (type == TokenType.STRUCT || type == TokenType.UNION || type == TokenType.ENUM) {
                if (peekAt(offset + 1).type() != TokenType.IDENTIFIER) {
                    return false;
                }
                offset += 2;
            } else if (isIntegerTypeSpecifier(type)) {
                do {
                    offset++;
                } while (isIntegerTypeSpecifier(peekAt(offset).type()));
            } else if (type == TokenType.BOOL
                    || type == TokenType.FLOAT
                    || type == TokenType.DOUBLE
                    || type == TokenType.VOID) {
                offset++;
            } else {
                return false;
            }
            while (peekAt(offset).type() == TokenType.STAR) {
                offset++;
            }
            return peekAt(offset).type() == TokenType.IDENTIFIER
                    && peekAt(offset + 1).type() == TokenType.LEFT_PAREN;
        }

        private boolean isIntegerTypeSpecifier(TokenType type) {
            return type == TokenType.CHAR
                    || type == TokenType.SHORT
                    || type == TokenType.INT
                    || type == TokenType.LONG
                    || type == TokenType.SIGNED
                    || type == TokenType.UNSIGNED;
        }

        public void synchronizeStatement() {
            while (!isAtEnd() && !check(TokenType.SEMICOLON) && !check(TokenType.RIGHT_BRACE)) {
                advance();
            }
            match(TokenType.SEMICOLON);
        }

        private List<TraceEvent> traceEvents() {
            return traceEvents == null ? List.of() : List.copyOf(traceEvents);
        }
    }

    /** Manager 共用的类型语法读取器，不单独形成第四个 Manager。 */
    public static final class TypeReader {
        private final Context context;
        private final java.util.Deque<java.util.Map<String, MiniType>> typedefScopes = new java.util.ArrayDeque<>();
        private final java.util.Deque<java.util.Set<String>> ordinaryNameScopes = new java.util.ArrayDeque<>();
        private final java.util.function.Consumer<StructDecl> aggregateSink;
        private int anonymousAggregateIndex;

        public TypeReader(Context context) {
            this(context, ignored -> { });
        }

        public TypeReader(Context context, java.util.function.Consumer<StructDecl> aggregateSink) {
            this.context = Objects.requireNonNull(context, "context");
            this.aggregateSink = Objects.requireNonNull(aggregateSink, "aggregateSink");
            enterScope(List.of());
        }

        public void enterScope(java.util.Collection<String> ordinaryNames) {
            typedefScopes.push(new java.util.LinkedHashMap<>());
            ordinaryNameScopes.push(new java.util.LinkedHashSet<>(ordinaryNames));
        }

        public void exitScope() {
            if (typedefScopes.size() <= 1) {
                throw new IllegalStateException("cannot exit parser global type scope");
            }
            typedefScopes.pop();
            ordinaryNameScopes.pop();
        }

        public boolean defineTypedef(String name, MiniType type, SourceRange range) {
            if (typedefScopes.peek().containsKey(name) || ordinaryNameScopes.peek().contains(name)) {
                context.report(range, "同一作用域中的 typedef 名称重复或与普通标识符冲突：" + name);
                return false;
            }
            typedefScopes.peek().put(name, type);
            return true;
        }

        public void declareOrdinaryName(String name, SourceRange range) {
            if (typedefScopes.peek().containsKey(name)) {
                context.report(range, "普通标识符与同一作用域的 typedef 名称冲突：" + name);
                return;
            }
            ordinaryNameScopes.peek().add(name);
        }

        public MiniType resolveTypedef(String name) {
            var typeIterator = typedefScopes.iterator();
            var ordinaryIterator = ordinaryNameScopes.iterator();
            while (typeIterator.hasNext() && ordinaryIterator.hasNext()) {
                java.util.Map<String, MiniType> types = typeIterator.next();
                java.util.Set<String> ordinary = ordinaryIterator.next();
                if (ordinary.contains(name)) {
                    return null;
                }
                MiniType type = types.get(name);
                if (type != null) {
                    return type;
                }
            }
            return null;
        }

        public ParsedType parseType(String expectedMessage) {
            BaseType baseType = parseBaseType(expectedMessage);
            if (baseType == null) {
                return null;
            }
            if (!canStartDeclarator(false)) {
                return new ParsedType(
                        baseType.type(),
                        baseType.startToken(),
                        SourceRange.span(baseType.startToken().range(), baseType.endToken().range())
                );
            }

            Declarator declarator = parseDeclarator("", false);
            if (declarator == null) {
                return null;
            }
            MiniType type = declarator.resolve(baseType.type());
            return new ParsedType(
                    type,
                    baseType.startToken(),
                    SourceRange.span(baseType.startToken().range(), declarator.endToken().range())
            );
        }

        /**
         * 读取一个完整 C 声明器。声明器先保存“从名字向外”的组合节点，随后从外向内
         * 绑定到命名叶子类型，因此可统一表达多级指针、多维数组、数组指针和函数指针。
         */
        public ParsedNamedType parseNamedType(String expectedTypeMessage, String expectedNameMessage) {
            List<minic.compiler.parser.node.Declaration.AlignmentSpec> alignmentSpecs = parseAlignmentSpecs();
            BaseType baseType = parseBaseType(expectedTypeMessage);
            if (baseType == null) {
                return null;
            }
            Declarator declarator = parseDeclarator(expectedNameMessage, true);
            if (declarator == null || declarator.name().isEmpty()) {
                return null;
            }
            MiniType resolvedType = declarator.resolve(baseType.type());
            FunctionModifier topFunction = declarator.topFunction();
            List<ParsedParameter> resolvedParameters;
            boolean resolvedVariadic;
            if (topFunction != null) {
                resolvedParameters = topFunction.parameters();
                resolvedVariadic = topFunction.variadic();
            } else if (resolvedType.unqualified() instanceof MiniType.FunctionType functionType) {
                resolvedParameters = functionType.parameterTypes().stream()
                        .map(type -> new ParsedParameter("", type, declarator.endToken().range()))
                        .toList();
                resolvedVariadic = functionType.variadic();
            } else {
                resolvedParameters = List.of();
                resolvedVariadic = false;
            }
            SourceRange declarationRange = alignmentSpecs.isEmpty()
                    ? SourceRange.span(baseType.startToken().range(), declarator.endToken().range())
                    : SourceRange.span(alignmentSpecs.getFirst().range(), declarator.endToken().range());
            return new ParsedNamedType(
                    declarator.name(),
                    resolvedType,
                    declarationRange,
                    resolvedParameters,
                    resolvedVariadic,
                    alignmentSpecs
            );
        }

        public boolean canStartType() {
            return canStartTypeAt(0);
        }

        public boolean canStartTypeAt(int offset) {
            while (isTypeQualifier(context.peekAt(offset).type())) {
                offset++;
            }
            TokenType type = context.peekAt(offset).type();
            return type == TokenType.BOOL
                    || type == TokenType.CHAR
                    || type == TokenType.INT
                    || type == TokenType.LONG
                    || type == TokenType.SHORT
                    || type == TokenType.SIGNED
                    || type == TokenType.UNSIGNED
                    || type == TokenType.FLOAT
                    || type == TokenType.DOUBLE
                    || type == TokenType.VOID
                    || type == TokenType.STRUCT
                    || type == TokenType.UNION
                    || type == TokenType.ENUM
                    || type == TokenType.BUILTIN_VA_LIST
                    || type == TokenType.IDENTIFIER
                    && resolveTypedef(context.peekAt(offset).lexeme()) != null;
        }

        public List<minic.compiler.parser.node.Declaration.AlignmentSpec> parseAlignmentSpecs() {
            ArrayList<minic.compiler.parser.node.Declaration.AlignmentSpec> specs = new ArrayList<>();
            while (context.match(TokenType.ALIGNAS)) {
                Token start = context.previous();
                context.consume(TokenType.LEFT_PAREN, "alignas 后期望 '('");
                if (canStartType()) {
                    ParsedType parsedType = parseType("alignas 期望类型或整数常量");
                    Token end = context.consume(TokenType.RIGHT_PAREN, "alignas 期望 ')'");
                    if (parsedType != null && end != null) {
                        specs.add(minic.compiler.parser.node.Declaration.AlignmentSpec.type(
                                parsedType.type(), SourceRange.span(start.range(), end.range())));
                    }
                    continue;
                }
                boolean negative = context.match(TokenType.MINUS);
                Token value = context.peek();
                if (!context.match(TokenType.INTEGER_LITERAL) && !context.match(TokenType.LONG_LITERAL)) {
                    context.report(value, "alignas 期望类型或整数常量");
                    context.consume(TokenType.RIGHT_PAREN, "alignas 期望 ')'");
                    continue;
                }
                long raw = value.literalValue() instanceof Integer integer
                        ? integer.longValue()
                        : ((Token.IntegerLiteralValue) value.literalValue()).value();
                if (negative) {
                    raw = -raw;
                }
                Token end = context.consume(TokenType.RIGHT_PAREN, "alignas 期望 ')'");
                if (end != null) {
                    if (raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
                        context.report(value, "alignas 整数超出支持范围");
                        raw = -1;
                    }
                    specs.add(minic.compiler.parser.node.Declaration.AlignmentSpec.constant(
                            (int) raw, SourceRange.span(start.range(), end.range())));
                }
            }
            return List.copyOf(specs);
        }

        private Declarator parseDeclarator(String expectedNameMessage, boolean nameRequired) {
            ArrayList<PointerLayer> pointerLayers = new ArrayList<>();
            while (context.match(TokenType.STAR)) {
                Token star = context.previous();
                pointerLayers.add(new PointerLayer(star, parseTypeQualifiers()));
            }

            Declarator direct;
            if (context.match(TokenType.IDENTIFIER)) {
                Token nameToken = context.previous();
                direct = new Declarator(nameToken.lexeme(), new ArrayList<>(), nameToken, nameToken);
            } else if (context.match(TokenType.LEFT_PAREN)) {
                Token startToken = context.previous();
                direct = parseDeclarator(expectedNameMessage, nameRequired);
                Token endToken = context.consume(TokenType.RIGHT_PAREN, "期望 ')'");
                if (direct == null || endToken == null) {
                    return null;
                }
                direct = direct.withRange(startToken, endToken);
            } else if (!nameRequired) {
                Token anchor = pointerLayers.isEmpty() ? context.peek() : pointerLayers.getFirst().token();
                direct = new Declarator("", new ArrayList<>(), anchor, anchor);
            } else {
                context.report(context.peek(), expectedNameMessage);
                return null;
            }

            while (context.check(TokenType.LEFT_BRACKET) || context.check(TokenType.LEFT_PAREN)) {
                if (context.match(TokenType.LEFT_BRACKET)) {
                    Token lengthToken;
                    if (context.check(TokenType.INTEGER_LITERAL) || context.check(TokenType.LONG_LITERAL)) {
                        lengthToken = context.advance();
                    } else {
                        context.report(context.peek(), "期望数组长度");
                        lengthToken = null;
                    }
                    Token endToken = context.consume(TokenType.RIGHT_BRACKET, "期望 ']'");
                    if (lengthToken == null || endToken == null) {
                        return null;
                    }
                    long parsedLength = lengthToken.literalValue() instanceof Integer integer
                            ? integer.longValue()
                            : ((Token.IntegerLiteralValue) lengthToken.literalValue()).value();
                    int length;
                    if (parsedLength <= 0 || parsedLength > Integer.MAX_VALUE) {
                        context.report(lengthToken, "数组长度必须位于 1..2147483647");
                        length = 1;
                    } else {
                        length = (int) parsedLength;
                    }
                    direct.modifiers().add(new ArrayModifier(length));
                    direct = direct.withEnd(endToken);
                    continue;
                }

                context.advance();
                ParameterList parameterList = parseParameterList();
                Token endToken = context.consume(TokenType.RIGHT_PAREN, "期望 ')'");
                if (endToken == null) {
                    return null;
                }
                direct.modifiers().add(new FunctionModifier(
                        parameterList.parameters(),
                        parameterList.variadic()
                ));
                direct = direct.withEnd(endToken);
            }

            // The '*' nearest the identifier is the outermost pointer layer. Tokens are
            // collected left-to-right, so append them in reverse to preserve qualifiers
            // on their exact pointer level when Declarator.resolve walks inside-out.
            for (int index = pointerLayers.size() - 1; index >= 0; index--) {
                direct.modifiers().add(new PointerModifier(pointerLayers.get(index).qualifiers()));
            }
            if (!pointerLayers.isEmpty()) {
                direct = direct.withStart(pointerLayers.getFirst().token());
            }
            return direct;
        }

        private ParameterList parseParameterList() {
            ArrayList<ParsedParameter> parameters = new ArrayList<>();
            if (context.check(TokenType.RIGHT_PAREN)) {
                return new ParameterList(parameters, false);
            }
            // C 的 (void) 表示无参数；void* 等声明仍按普通参数解析。
            if (context.check(TokenType.VOID) && context.peekAt(1).type() == TokenType.RIGHT_PAREN) {
                context.advance();
                return new ParameterList(parameters, false);
            }
            boolean variadic = false;
            do {
                if (context.match(TokenType.ELLIPSIS)) {
                    variadic = true;
                    if (!context.check(TokenType.RIGHT_PAREN)) {
                        context.report(context.peek(), "可变参数标记必须位于参数列表末尾");
                    }
                    break;
                }
                BaseType baseType = parseBaseType("期望参数类型");
                if (baseType == null) {
                    break;
                }
                Declarator declarator;
                if (canStartDeclarator(true)) {
                    declarator = parseDeclarator("", false);
                    if (declarator == null) {
                        break;
                    }
                } else {
                    declarator = new Declarator("", new ArrayList<>(), baseType.endToken(), baseType.endToken());
                }
                MiniType parameterType = adjustParameterType(declarator.resolve(baseType.type()));
                SourceRange range = SourceRange.span(baseType.startToken().range(), declarator.endToken().range());
                parameters.add(new ParsedParameter(declarator.name(), parameterType, range));
            } while (context.match(TokenType.COMMA));
            return new ParameterList(parameters, variadic);
        }

        private boolean canStartDeclarator(boolean allowIdentifier) {
            return context.check(TokenType.STAR)
                    || context.check(TokenType.LEFT_PAREN)
                    || context.check(TokenType.LEFT_BRACKET)
                    || (allowIdentifier && context.check(TokenType.IDENTIFIER));
        }

        private MiniType adjustParameterType(MiniType type) {
            MiniType unqualified = type.unqualified();
            if (unqualified instanceof MiniType.ArrayType arrayType) {
                java.util.EnumSet<MiniType.TypeQualifier> elementQualifiers =
                        java.util.EnumSet.noneOf(MiniType.TypeQualifier.class);
                elementQualifiers.addAll(arrayType.elementType().qualifiers());
                type.qualifiers().stream()
                        .filter(qualifier -> qualifier != MiniType.TypeQualifier.RESTRICT)
                        .forEach(elementQualifiers::add);
                return MiniType.qualified(arrayType.elementType().unqualified(), elementQualifiers).pointerTo();
            }
            if (unqualified instanceof MiniType.FunctionType) {
                return type.pointerTo();
            }
            return type;
        }

        private BaseType parseBaseType(String expectedMessage) {
            Token start = context.peek();
            java.util.EnumSet<MiniType.TypeQualifier> qualifiers = java.util.EnumSet.noneOf(
                    MiniType.TypeQualifier.class);
            qualifiers.addAll(parseTypeQualifiers());
            if (isIntegerTypeSpecifier(context.peek().type())) {
                return parseIntegerBaseType(start, qualifiers);
            }

            MiniType type;
            Token end;
            if (context.check(TokenType.BOOL)) {
                end = context.advance();
                type = MiniType.BOOL;
            } else if (context.check(TokenType.FLOAT)) {
                end = context.advance();
                type = MiniType.FLOAT;
            } else if (context.check(TokenType.DOUBLE)) {
                end = context.advance();
                type = MiniType.DOUBLE;
            } else if (context.check(TokenType.VOID)) {
                end = context.advance();
                type = MiniType.VOID;
            } else if (context.check(TokenType.BUILTIN_VA_LIST)) {
                end = context.advance();
                type = MiniType.VA_LIST;
            } else if (context.check(TokenType.STRUCT)) {
                BaseType struct = parseStructType();
                if (struct == null) return null;
                type = struct.type();
                end = struct.endToken();
            } else if (context.check(TokenType.UNION)) {
                BaseType union = parseUnionType();
                if (union == null) return null;
                type = union.type();
                end = union.endToken();
            } else if (context.check(TokenType.ENUM)) {
                Token startToken = context.advance();
                Token nameToken = context.consume(TokenType.IDENTIFIER, "期望枚举类型名");
                if (nameToken == null) return null;
                type = MiniType.INT;
                end = nameToken;
            } else if (context.check(TokenType.IDENTIFIER)
                    && resolveTypedef(context.peek().lexeme()) != null) {
                Token alias = context.advance();
                type = resolveTypedef(alias.lexeme());
                end = alias;
            } else {
                context.report(context.peek(), expectedMessage);
                return null;
            }
            java.util.Set<MiniType.TypeQualifier> trailing = parseTypeQualifiers();
            qualifiers.addAll(trailing);
            if (!trailing.isEmpty()) {
                end = context.previous();
            }
            return new BaseType(MiniType.qualified(type, qualifiers), start, end);
        }

        private BaseType parseIntegerBaseType(
                Token start,
                java.util.EnumSet<MiniType.TypeQualifier> qualifiers
        ) {
            Token end = start;
            boolean signed = false;
            boolean unsigned = false;
            boolean character = false;
            boolean shortType = false;
            boolean explicitInt = false;
            int longCount = 0;

            while (isIntegerTypeSpecifier(context.peek().type()) || isTypeQualifier(context.peek().type())) {
                Token token = context.advance();
                end = token;
                switch (token.type()) {
                    case CONST -> qualifiers.add(MiniType.TypeQualifier.CONST);
                    case VOLATILE -> qualifiers.add(MiniType.TypeQualifier.VOLATILE);
                    case RESTRICT -> qualifiers.add(MiniType.TypeQualifier.RESTRICT);
                    case SIGNED -> {
                        if (signed || unsigned) context.report(token, "整数类型的 signed/unsigned 说明重复或冲突");
                        signed = true;
                    }
                    case UNSIGNED -> {
                        if (signed || unsigned) context.report(token, "整数类型的 signed/unsigned 说明重复或冲突");
                        unsigned = true;
                    }
                    case CHAR -> {
                        if (character) context.report(token, "char 类型说明重复");
                        character = true;
                    }
                    case SHORT -> {
                        if (shortType) context.report(token, "short 类型说明重复");
                        shortType = true;
                    }
                    case INT -> {
                        if (explicitInt) context.report(token, "int 类型说明重复");
                        explicitInt = true;
                    }
                    case LONG -> longCount++;
                    default -> throw new IllegalStateException("unexpected integer type specifier " + token.type());
                }
            }

            if (longCount > 2 || character && (shortType || longCount > 0) || shortType && longCount > 0) {
                context.report(SourceRange.span(start.range(), end.range()), "无效的整数类型说明符组合");
            }

            MiniType type;
            if (character) {
                type = unsigned ? MiniType.UNSIGNED_CHAR : signed ? MiniType.SIGNED_CHAR : MiniType.CHAR;
            } else if (shortType) {
                type = unsigned ? MiniType.UNSIGNED_SHORT : MiniType.SHORT;
            } else if (longCount >= 2) {
                type = unsigned ? MiniType.UNSIGNED_LONG_LONG : MiniType.LONG_LONG;
            } else if (longCount == 1) {
                type = unsigned ? MiniType.UNSIGNED_LONG : MiniType.LONG;
            } else {
                type = unsigned ? MiniType.UNSIGNED_INT : MiniType.INT;
            }
            return new BaseType(MiniType.qualified(type, qualifiers), start, end);
        }

        private boolean isIntegerTypeSpecifier(TokenType type) {
            return type == TokenType.CHAR
                    || type == TokenType.SHORT
                    || type == TokenType.INT
                    || type == TokenType.LONG
                    || type == TokenType.SIGNED
                    || type == TokenType.UNSIGNED;
        }

        private boolean isTypeQualifier(TokenType type) {
            return type == TokenType.CONST || type == TokenType.VOLATILE || type == TokenType.RESTRICT;
        }

        private java.util.Set<MiniType.TypeQualifier> parseTypeQualifiers() {
            java.util.EnumSet<MiniType.TypeQualifier> qualifiers = java.util.EnumSet.noneOf(
                    MiniType.TypeQualifier.class);
            while (isTypeQualifier(context.peek().type())) {
                Token token = context.advance();
                qualifiers.add(switch (token.type()) {
                    case CONST -> MiniType.TypeQualifier.CONST;
                    case VOLATILE -> MiniType.TypeQualifier.VOLATILE;
                    case RESTRICT -> MiniType.TypeQualifier.RESTRICT;
                    default -> throw new IllegalStateException("not a type qualifier: " + token.type());
                });
            }
            return java.util.Set.copyOf(qualifiers);
        }

        private BaseType parseStructType() {
            return parseAggregateType(false);
        }

        private BaseType parseUnionType() {
            return parseAggregateType(true);
        }

        private BaseType parseAggregateType(boolean union) {
            Token startToken = context.advance();
            Token nameToken = context.check(TokenType.IDENTIFIER) ? context.advance() : null;
            if (!context.match(TokenType.LEFT_BRACE)) {
                if (nameToken == null) {
                    context.report(context.peek(), union ? "期望联合体名称或定义" : "期望结构体名称或定义");
                    return null;
                }
                String name = union ? "$union$" + nameToken.lexeme() : nameToken.lexeme();
                return new BaseType(MiniType.struct(name), startToken, nameToken);
            }
            String sourceName = nameToken == null ? "$anonymous$" + anonymousAggregateIndex++ : nameToken.lexeme();
            String internalName = union ? "$union$" + sourceName : sourceName;
            ArrayList<minic.compiler.parser.node.Declaration.StructField> fields = new ArrayList<>();
            while (!context.check(TokenType.RIGHT_BRACE) && !context.isAtEnd()) {
                List<minic.compiler.parser.node.Declaration.AlignmentSpec> specs = parseAlignmentSpecs();
                BaseType fieldBase = parseBaseType("期望字段类型");
                if (fieldBase == null) { context.synchronizeStatement(); continue; }
                if (context.check(TokenType.SEMICOLON)
                        && fieldBase.type().unqualified() instanceof MiniType.StructType) {
                    Token end = context.advance();
                    fields.add(new minic.compiler.parser.node.Declaration.StructField(
                            "", fieldBase.type(), true, specs,
                            SourceRange.span(fieldBase.startToken().range(), end.range())));
                    continue;
                }
                Declarator declarator = parseDeclarator("期望字段名", true);
                Token end = context.consume(TokenType.SEMICOLON, "期望 ';'");
                if (declarator != null && end != null) {
                    fields.add(new minic.compiler.parser.node.Declaration.StructField(
                            declarator.name(), declarator.resolve(fieldBase.type()), false, specs,
                            SourceRange.span(fieldBase.startToken().range(), end.range())));
                }
            }
            Token close = context.consume(TokenType.RIGHT_BRACE, "期望 '}'");
            if (close == null) return null;
            StructDecl declaration = new StructDecl(internalName, fields, true, union,
                    SourceRange.span(startToken.range(), close.range()));
            aggregateSink.accept(declaration);
            context.build(declaration, "AnonymousAggregate " + internalName, declaration.range());
            return new BaseType(MiniType.struct(internalName), startToken, close);
        }

        private record BaseType(MiniType type, Token startToken, Token endToken) {
        }

        private interface DeclaratorModifier {
            MiniType apply(MiniType inner);
        }

        private record PointerModifier(java.util.Set<MiniType.TypeQualifier> qualifiers)
                implements DeclaratorModifier {
            @Override
            public MiniType apply(MiniType inner) {
                return MiniType.qualified(inner.pointerTo(), qualifiers);
            }
        }

        private record PointerLayer(Token token, java.util.Set<MiniType.TypeQualifier> qualifiers) {
        }

        private record ArrayModifier(int length) implements DeclaratorModifier {
            @Override
            public MiniType apply(MiniType inner) {
                return inner.arrayOf(length);
            }
        }

        private record FunctionModifier(
                List<ParsedParameter> parameters,
                boolean variadic
        ) implements DeclaratorModifier {
            private FunctionModifier {
                parameters = List.copyOf(parameters);
            }

            @Override
            public MiniType apply(MiniType inner) {
                return MiniType.function(
                        inner.unqualified(),
                        parameters.stream().map(ParsedParameter::type).map(MiniType::unqualified).toList(),
                        variadic
                );
            }
        }

        private record Declarator(
                String name,
                ArrayList<DeclaratorModifier> modifiers,
                Token startToken,
                Token endToken
        ) {
            private MiniType resolve(MiniType baseType) {
                MiniType resolved = baseType;
                for (int index = modifiers.size() - 1; index >= 0; index--) {
                    resolved = modifiers.get(index).apply(resolved);
                }
                return resolved;
            }

            private FunctionModifier topFunction() {
                return !modifiers.isEmpty() && modifiers.getFirst() instanceof FunctionModifier function
                        ? function
                        : null;
            }

            private Declarator withRange(Token start, Token end) {
                return new Declarator(name, modifiers, start, end);
            }

            private Declarator withStart(Token start) {
                return new Declarator(name, modifiers, start, endToken);
            }

            private Declarator withEnd(Token end) {
                return new Declarator(name, modifiers, startToken, end);
            }
        }

        private record ParameterList(List<ParsedParameter> parameters, boolean variadic) {
            private ParameterList {
                parameters = List.copyOf(parameters);
            }
        }
    }

    public record ParsedType(MiniType type, Token startToken, SourceRange range) {
    }

    public record ParsedNamedType(
            String name,
            MiniType type,
            SourceRange range,
            List<ParsedParameter> parameters,
            boolean variadic,
            List<minic.compiler.parser.node.Declaration.AlignmentSpec> alignmentSpecs
    ) {
        public ParsedNamedType {
            parameters = List.copyOf(parameters);
            alignmentSpecs = List.copyOf(alignmentSpecs);
        }
    }

    public record ParsedParameter(String name, MiniType type, SourceRange range) {
    }

    /** Parser 递归下降与 AST 构建观察事件。 */
    public record TraceEvent(String kind, String label, SourceRange range, AstNode node) {
        public TraceEvent {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(label, "label");
        }
    }
}
