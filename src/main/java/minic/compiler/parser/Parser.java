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
import minic.compiler.parser.node.QualifiedName;
import minic.compiler.parser.node.OperatorName;
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
            if (namespaceMembers.isEmpty()) {
                structs.add(aggregate);
                declarations.add(aggregate);
            }
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
        if (context.peekAt(1).type() != TokenType.IDENTIFIER) return false;
        TokenType following = context.peekAt(2).type();
        if (following == TokenType.SEMICOLON) return true;
        if (languageMode() == LanguageMode.CPP17_ALGORITHM && following == TokenType.COLON) return true;
        if (following != TokenType.LEFT_BRACE) return false;
        if (languageMode() != LanguageMode.CPP17_ALGORITHM) return true;
        int depth = 0;
        for (int offset = 2; context.peekAt(offset).type() != TokenType.EOF; offset++) {
            TokenType token = context.peekAt(offset).type();
            if (token == TokenType.LEFT_BRACE) depth++;
            if (token == TokenType.RIGHT_BRACE && --depth == 0) {
                return context.peekAt(offset + 1).type() == TokenType.SEMICOLON;
            }
        }
        return true;
    }

    private Declaration parseDeclaration() {
        if (languageMode() == LanguageMode.CPP17_ALGORITHM) {
            if (context.check(TokenType.NAMESPACE)) return parseNamespace();
            if (context.check(TokenType.USING)) {
                var using = CppNameParser.parseUsing(context);
                if (using != null) typeReader.registerUsing(using);
                return using;
            }
        }
        if (context.check(TokenType.TYPEDEF)) return declarationManager.parseTypedefDecl();
        if (context.check(TokenType.ENUM) && context.peekAt(1).type() == TokenType.IDENTIFIER
                && context.peekAt(2).type() == TokenType.LEFT_BRACE) return declarationManager.parseEnumDecl();
        if ((context.check(TokenType.STRUCT) || context.check(TokenType.UNION)
                || languageMode() == LanguageMode.CPP17_ALGORITHM && context.check(TokenType.CLASS)) && isStructDeclaration()) {
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
        typeReader.enterNamespace(name);
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
            typeReader.exitNamespace();
            namespaceMembers.pop();
            enumConstants.clear();
            enumConstants.putAll(savedConstants);
        }
    }

    private ParserResult buildResult() {
        return new ParserResult(new Program(structs, enums, typedefs, globals, functions, declarations, languageMode(), programRange()));
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
        private int tokenLimit;
        private Token windowEnd;
        private boolean functionBoundaryRecovered;

        private Context(List<Token> tokens, LanguageMode languageMode, boolean traceEnabled) {
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("tokens must contain EOF");
            }
            this.tokens = tokens;
            this.languageMode = languageMode;
            tokenLimit = tokens.size();
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
            return currentIndex >= tokenLimit ? windowEnd : tokens.get(currentIndex);
        }

        public Token peekAt(int offset) {
            int index = currentIndex + offset;
            if (index >= tokenLimit && windowEnd != null) return windowEnd;
            return index >= tokens.size() ? tokens.getLast() : tokens.get(index);
        }

        /** A balanced body is retained as token indices, preserving original source locations. */
        public record TokenWindow(int start, int end) { }

        public TokenWindow deferBlock() {
            if (!check(TokenType.LEFT_BRACE)) throw new IllegalStateException("Expected deferred block");
            int start = currentIndex;
            int depth = 0;
            while (!isAtEnd()) {
                TokenType type = peek().type();
                currentIndex++;
                if (type == TokenType.LEFT_BRACE) depth++;
                if (type == TokenType.RIGHT_BRACE && --depth == 0) return new TokenWindow(start, currentIndex);
            }
            report(tokens.get(start), "未闭合的方法体");
            return new TokenWindow(start, currentIndex);
        }

        /** Retain a constructor's argument list without interpreting names before class completion. */
        public TokenWindow deferParentheses() {
            if (!check(TokenType.LEFT_PAREN)) throw new IllegalStateException("Expected deferred arguments");
            int start = currentIndex, depth = 0, braces = 0;
            while (!isAtEnd()) {
                TokenType type = peek().type();
                // Neither a class terminator nor a member separator can belong to these arguments.
                if (type == TokenType.SEMICOLON || type == TokenType.RIGHT_BRACE && braces == 0) break;
                currentIndex++;
                if (type == TokenType.LEFT_BRACE) braces++;
                if (type == TokenType.RIGHT_BRACE) braces--;
                if (type == TokenType.LEFT_PAREN) depth++;
                if (type == TokenType.RIGHT_PAREN && --depth == 0) return new TokenWindow(start, currentIndex);
            }
            report(tokens.get(start), "未闭合的构造初始化参数，期望 ')'");
            return new TokenWindow(start, currentIndex);
        }

        public <T> T inTokenWindow(TokenWindow window, java.util.function.Supplier<T> parse) {
            if (window.start() < 0 || window.end() <= window.start() || window.end() > tokenLimit) {
                throw new IllegalArgumentException("Invalid token window");
            }
            int savedIndex = currentIndex, savedLimit = tokenLimit;
            Token savedEnd = windowEnd;
            boolean savedRecovery = functionBoundaryRecovered;
            currentIndex = window.start();
            tokenLimit = window.end();
            SourceRange end = tokens.get(window.end() - 1).range();
            windowEnd = new Token(TokenType.EOF, "", new SourceRange(end.endLine(), end.endByte(), end.endLine(), end.endByte()));
            functionBoundaryRecovered = false;
            try { return parse.get(); }
            finally {
                currentIndex = savedIndex;
                tokenLimit = savedLimit;
                windowEnd = savedEnd;
                functionBoundaryRecovered = savedRecovery;
            }
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

        /** A specialized declaration parser already consumed its own failed declaration. */
        public void markDeclarationBoundaryRecovered() {
            functionBoundaryRecovered = true;
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
            while (peekAt(offset).type() == TokenType.STAR
                    || languageMode == LanguageMode.CPP17_ALGORITHM
                    && (peekAt(offset).type() == TokenType.AMPERSAND || peekAt(offset).type() == TokenType.AMPERSAND_AMPERSAND)) {
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
        private final CppTypeEnvironment cppTypes;
        private final java.util.Map<String, List<Declaration.StructField>> aggregateFields = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, StructDecl> aggregateDeclarations = new java.util.LinkedHashMap<>();
        private int cppLocalDepth;
        private int cppMemberDepth;
        private int anonymousAggregateIndex;
        private CppRecordParser cppRecordParser;

        public TypeReader(Context context) {
            this(context, ignored -> { });
        }

        public TypeReader(Context context, java.util.function.Consumer<StructDecl> aggregateSink) {
            this.context = Objects.requireNonNull(context, "context");
            this.aggregateSink = Objects.requireNonNull(aggregateSink, "aggregateSink");
            cppTypes = isCpp() ? new CppTypeEnvironment() : null;
            typedefScopes.push(new java.util.LinkedHashMap<>());
            ordinaryNameScopes.push(new java.util.LinkedHashSet<>());
        }

        public boolean isCpp() { return context.languageMode() == LanguageMode.CPP17_ALGORITHM; }

        public void setCppRecordParser(CppRecordParser parser) { cppRecordParser = Objects.requireNonNull(parser); }

        public void enterNamespace(minic.compiler.parser.node.QualifiedName name) {
            cppTypes.enterNamespace(name.segments(), name.range());
        }

        public void exitNamespace() { cppTypes.exitNamespace(); }

        public void enterMemberScope() {
            enterMemberScope(null);
        }

        public void enterMemberScope(MiniType selfType) {
            if (isCpp()) { cppTypes.enterMemberScope(selfType); cppMemberDepth++; }
        }

        public void exitMemberScope() {
            if (isCpp()) { cppTypes.exitMemberScope(); cppMemberDepth--; }
        }

        /** Retain anonymous aggregate members so a containing class can promote their names. */
        public void recordAggregateFields(StructDecl declaration) {
            if (isCpp()) {
                aggregateFields.put(declaration.name(), declaration.fields());
                if (declaration.definition()) aggregateDeclarations.put(declaration.name(), declaration);
            }
        }

        public void enterMemberDefinitionScope(QualifiedName qualifiedName) {
            QualifiedName owner = new QualifiedName(qualifiedName.global(),
                    qualifiedName.segments().subList(0, qualifiedName.segments().size() - 1), qualifiedName.range());
            MiniType type = cppTypes.enterMemberDefinitionScope(owner);
            cppMemberDepth++;
            if (!(type instanceof MiniType.StructType record)) return;
            StructDecl declaration = aggregateDeclarations.get(record.name());
            if (declaration == null) return;
            declaration.fields().forEach(this::declareMemberField);
            if (declaration.cppInfo() != null) {
                for (var member : declaration.cppInfo().members()) {
                    if (member instanceof Declaration.MethodMember method) {
                        declareOrdinaryName(method.method().name(), method.nameRange());
                    }
                }
            }
        }

        /** Pure disambiguation: a namespace-qualified type may also precede a parenthesized variable. */
        public boolean namesConstructor(QualifiedName name) {
            if (!isCpp() || name.segments().size() < 2) return false;
            var owner = new QualifiedName(name.global(), name.segments().subList(0, name.segments().size() - 1), name.range());
            var lookup = cppTypes.lookup(owner);
            if (lookup.kind() != CppTypeEnvironment.Kind.TYPE || !(lookup.type().unqualified() instanceof MiniType.StructType record)) return false;
            String injectedName = record.name().substring(record.name().lastIndexOf("::") + 2);
            return injectedName.equals(name.segments().getLast());
        }

        public void exitMemberDefinitionScope() {
            cppTypes.exitMemberDefinitionScope();
            cppMemberDepth--;
        }

        public void declareMemberField(Declaration.StructField field) {
            if (!isCpp()) return;
            if (field.anonymous()) {
                promoteAnonymousMemberNames(field.type(), new java.util.HashSet<>());
            } else {
                declareOrdinaryName(field.name(), field.range());
            }
        }

        private void promoteAnonymousMemberNames(MiniType type, java.util.Set<String> visited) {
            if (!(type.unqualified() instanceof MiniType.StructType aggregate) || !visited.add(aggregate.name())) return;
            for (var field : aggregateFields.getOrDefault(aggregate.name(), List.of())) {
                if (field.anonymous()) promoteAnonymousMemberNames(field.type(), visited);
                else declareOrdinaryName(field.name(), field.range());
            }
        }

        public void registerUsing(Declaration.UsingDecl declaration) {
            if (isCpp()) cppTypes.registerUsing(declaration.target(), declaration.namespaceDirective(), declaration.range());
            // Value lookup and invalid using declarations are diagnosed by the binder.
        }

        public MiniType declareAggregate(String name, boolean union, boolean definition, SourceRange range) {
            if (!isCpp()) return MiniType.struct(union ? "$union$" + name : name);
            if ((cppLocalDepth > 0 || cppMemberDepth > 0) && !name.startsWith("$anonymous$")) {
                context.unsupportedCpp(range, "局部或成员命名结构体声明尚未实现");
            }
            int before = cppTypes.diagnostics().size();
            MiniType result = cppTypes.declareStruct(name, union, definition, range);
            copyTypeDiagnostics(before);
            return result;
        }

        private void copyTypeDiagnostics(int before) {
            var diagnostics = cppTypes.diagnostics();
            for (int i = before; i < diagnostics.size(); i++) context.reportedErrors.add(diagnostics.get(i));
        }

        public void enterScope(java.util.Collection<String> ordinaryNames) {
            if (isCpp()) {
                cppTypes.enterLocalScope();
                cppLocalDepth++;
                for (String name : ordinaryNames) cppTypes.declareValue(name, context.peek().range());
                return;
            }
            typedefScopes.push(new java.util.LinkedHashMap<>());
            ordinaryNameScopes.push(new java.util.LinkedHashSet<>(ordinaryNames));
        }

        public void exitScope() {
            if (isCpp()) {
                cppTypes.exitLocalScope();
                cppLocalDepth--;
                return;
            }
            if (typedefScopes.size() <= 1) {
                throw new IllegalStateException("cannot exit parser global type scope");
            }
            typedefScopes.pop();
            ordinaryNameScopes.pop();
        }

        public boolean defineTypedef(String name, MiniType type, SourceRange range) {
            if (isCpp()) {
                int before = cppTypes.diagnostics().size();
                cppTypes.declareTypedef(name, type, range);
                copyTypeDiagnostics(before);
                return before == cppTypes.diagnostics().size();
            }
            if (typedefScopes.peek().containsKey(name) || ordinaryNameScopes.peek().contains(name)) {
                context.report(range, "同一作用域中的 typedef 名称重复或与普通标识符冲突：" + name);
                return false;
            }
            typedefScopes.peek().put(name, type);
            return true;
        }

        public void declareOrdinaryName(String name, SourceRange range) {
            if (isCpp()) {
                cppTypes.declareValue(name, range);
                return;
            }
            if (typedefScopes.peek().containsKey(name)) {
                context.report(range, "普通标识符与同一作用域的 typedef 名称冲突：" + name);
                return;
            }
            ordinaryNameScopes.peek().add(name);
        }

        public MiniType resolveTypedef(String name) {
            if (isCpp()) {
                var result = cppTypes.lookup(new minic.compiler.parser.node.QualifiedName(false, List.of(name), context.peek().range()));
                return result.kind() == CppTypeEnvironment.Kind.TYPE ? result.type() : null;
            }
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
            if (declarator.operatorName() != null) {
                context.report(declarator.operatorName().range(), "类型名称不能声明运算符函数");
                return null;
            }
            MiniType type = resolveDeclarator(declarator, baseType.type());
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
            return parseNamedType(expectedTypeMessage, expectedNameMessage, false);
        }

        public ParsedNamedType parseNamedType(String expectedTypeMessage, String expectedNameMessage,
                                             boolean allowQualifiedName) {
            return parseNamedType(expectedTypeMessage, expectedNameMessage, allowQualifiedName, allowQualifiedName);
        }

        public ParsedNamedType parseNamedType(String expectedTypeMessage, String expectedNameMessage,
                                             boolean allowQualifiedName, boolean allowOperatorName) {
            List<minic.compiler.parser.node.Declaration.AlignmentSpec> alignmentSpecs = parseAlignmentSpecs();
            BaseType baseType = parseBaseType(expectedTypeMessage);
            if (baseType == null) {
                return null;
            }
            Declarator declarator = parseDeclarator(expectedNameMessage, true, allowQualifiedName, baseType.type().isReference());
            if (declarator == null || declarator.name().isEmpty()) {
                return null;
            }
            if (declarator.operatorName() != null && !allowOperatorName) {
                context.report(declarator.operatorName().range(), "此声明不能使用运算符函数名称");
                return null;
            }
            MiniType resolvedType = resolveDeclarator(declarator, baseType.type());
            if (declarator.operatorName() != null && !(resolvedType.unqualified() instanceof MiniType.FunctionType)) {
                context.report(declarator.operatorName().range(), "运算符名称必须声明函数");
                return null;
            }
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
                    alignmentSpecs,
                    declarator.nameToken().range(),
                    declarator.qualifiedName(),
                    declarator.operatorName()
            );
        }

        public boolean canStartType() {
            return canStartTypeAt(0);
        }

        /** Functional notation accepts a simple-type-specifier, not an arbitrary declarator. */
        public int cppConstructionDelimiterAt(int offset) {
            if (!isCpp()) return -1;
            TokenType token = context.peekAt(offset).type();
            int end;
            if (token == TokenType.IDENTIFIER || token == TokenType.SCOPE) {
                var name = CppNameParser.peekName(context, offset);
                if (name == null || cppTypes.lookup(name).kind() != CppTypeEnvironment.Kind.TYPE) return -1;
                end = offset + name.segments().size() * 2 - 1 + (name.global() ? 1 : 0);
            } else {
                if (token != TokenType.BOOL && token != TokenType.CHAR && token != TokenType.INT
                        && token != TokenType.LONG && token != TokenType.SHORT && token != TokenType.SIGNED
                        && token != TokenType.UNSIGNED && token != TokenType.FLOAT && token != TokenType.DOUBLE
                        && token != TokenType.VOID) return -1;
                end = offset + 1;
            }
            TokenType following = context.peekAt(end).type();
            return following == TokenType.LEFT_PAREN || following == TokenType.LEFT_BRACE ? end : -1;
        }

        public ParsedType parseCppConstructionType() {
            BaseType base = parseBaseType("期望构造类型");
            return base == null ? null : new ParsedType(base.type(), base.startToken(),
                    SourceRange.span(base.startToken().range(), base.endToken().range()));
        }

        /** Type-id grammar wins sizeof/alignof ambiguity; function pointer casts remain type-ids. */
        public boolean cppTypeOperandAt(int offset, boolean query) {
            int delimiter = cppConstructionDelimiterAt(offset);
            if (delimiter < 0) return true;
            if (context.peekAt(delimiter).type() == TokenType.LEFT_BRACE) return false;
            TokenType first = context.peekAt(delimiter + 1).type();
            if (first == TokenType.RIGHT_PAREN) return query;
            if (canStartTypeAt(delimiter + 1)) return query && cppTypeOperandAt(delimiter + 1, true);
            int end = skipCppAbstractDeclarator(delimiter);
            return end >= 0 && context.peekAt(end).type() == TokenType.RIGHT_PAREN;
        }

        private int skipCppAbstractDeclarator(int offset) {
            int start = offset;
            while (context.peekAt(offset).type() == TokenType.STAR || context.peekAt(offset).type() == TokenType.AMPERSAND) {
                offset++;
                while (isTypeQualifier(context.peekAt(offset).type())) offset++;
            }
            if (context.peekAt(offset).type() == TokenType.LEFT_PAREN
                    && context.peekAt(offset + 1).type() != TokenType.RIGHT_PAREN && !canStartTypeAt(offset + 1)) {
                int inner = skipCppAbstractDeclarator(offset + 1);
                if (inner < 0 || context.peekAt(inner).type() != TokenType.RIGHT_PAREN) return -1;
                offset = inner + 1;
            }
            while (context.peekAt(offset).type() == TokenType.LEFT_PAREN || context.peekAt(offset).type() == TokenType.LEFT_BRACKET) {
                TokenType open = context.peekAt(offset).type();
                TokenType close = open == TokenType.LEFT_PAREN ? TokenType.RIGHT_PAREN : TokenType.RIGHT_BRACKET;
                if (open == TokenType.LEFT_PAREN && context.peekAt(offset + 1).type() != close && !canStartTypeAt(offset + 1)) return -1;
                int depth = 1;
                while (depth > 0) {
                    TokenType token = context.peekAt(++offset).type();
                    if (token == TokenType.EOF || token == TokenType.SEMICOLON) return -1;
                    if (token == open) depth++;
                    if (token == close) depth--;
                }
                offset++;
            }
            return offset == start || context.peekAt(offset).type() == TokenType.IDENTIFIER ? -1 : offset;
        }

        /** In a statement, a syntactically complete declaration wins T(name) ambiguity. */
        public boolean startsCppConstructionStatement() {
            int delimiter = cppConstructionDelimiterAt(0);
            if (delimiter < 0) return false;
            if (context.peekAt(delimiter).type() == TokenType.LEFT_BRACE) return true;
            int after = skipCppGroupedDeclarator(delimiter);
            if (after < 0) return true;
            TokenType next = context.peekAt(after).type();
            return next != TokenType.SEMICOLON && next != TokenType.EQUAL && next != TokenType.COMMA
                    && next != TokenType.LEFT_BRACE;
        }

        private int skipCppGroupedDeclarator(int offset) {
            while (context.peekAt(offset).type() == TokenType.STAR || context.peekAt(offset).type() == TokenType.AMPERSAND) {
                offset++;
                while (isTypeQualifier(context.peekAt(offset).type())) offset++;
            }
            if (context.peekAt(offset).type() == TokenType.IDENTIFIER) offset++;
            else if (context.peekAt(offset).type() == TokenType.LEFT_PAREN) {
                offset = skipCppGroupedDeclarator(offset + 1);
                if (offset < 0 || context.peekAt(offset).type() != TokenType.RIGHT_PAREN) return -1;
                offset++;
            } else return -1;
            while (context.peekAt(offset).type() == TokenType.LEFT_PAREN || context.peekAt(offset).type() == TokenType.LEFT_BRACKET) {
                TokenType open = context.peekAt(offset).type();
                TokenType close = open == TokenType.LEFT_PAREN ? TokenType.RIGHT_PAREN : TokenType.RIGHT_BRACKET;
                if (open == TokenType.LEFT_PAREN && context.peekAt(offset + 1).type() != close
                        && !canStartTypeAt(offset + 1)) return -1;
                int depth = 1;
                while (depth > 0) {
                    TokenType token = context.peekAt(++offset).type();
                    if (token == TokenType.EOF || token == TokenType.SEMICOLON) return -1;
                    if (token == open) depth++;
                    if (token == close) depth--;
                }
                offset++;
            }
            return offset;
        }

        public boolean canStartTypeAt(int offset) {
            while (isTypeQualifier(context.peekAt(offset).type())) {
                offset++;
            }
            TokenType type = context.peekAt(offset).type();
            if (isCpp() && (type == TokenType.IDENTIFIER || type == TokenType.SCOPE)) {
                var name = CppNameParser.peekName(context, offset);
                return name != null && cppTypes.lookup(name).kind() == CppTypeEnvironment.Kind.TYPE;
            }
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
                    || isCpp() && type == TokenType.CLASS
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
            return parseDeclarator(expectedNameMessage, nameRequired, false);
        }

        private Declarator parseDeclarator(String expectedNameMessage, boolean nameRequired, boolean allowQualifiedName) {
            return parseDeclarator(expectedNameMessage, nameRequired, allowQualifiedName, false);
        }

        private Declarator parseDeclarator(String expectedNameMessage, boolean nameRequired,
                                           boolean allowQualifiedName, boolean referenceBase) {
            ArrayList<PointerLayer> pointerLayers = new ArrayList<>();
            while (context.check(TokenType.STAR) || isCpp()
                    && (context.check(TokenType.AMPERSAND) || context.check(TokenType.AMPERSAND_AMPERSAND))) {
                Token operator = context.advance();
                boolean reference = operator.type() != TokenType.STAR;
                if (operator.type() == TokenType.AMPERSAND_AMPERSAND) {
                    context.unsupportedCpp(operator.range(), "右值引用尚未实现");
                }
                var qualifiers = parseTypeQualifiers();
                if (reference && !qualifiers.isEmpty()) context.report(operator, "引用声明器不能直接带 const/volatile 限定符");
                pointerLayers.add(new PointerLayer(operator, qualifiers, reference));
            }

            Declarator direct;
            if (isCpp() && context.check(TokenType.OPERATOR)) {
                OperatorName operator = CppOperatorNameParser.parse(context);
                if (operator == null) return null;
                Token name = new Token(TokenType.IDENTIFIER, operator.spelling(), operator.range());
                direct = new Declarator(name.lexeme(), new ArrayList<>(), name, name, name, null, operator);
            } else if (isCpp() && allowQualifiedName && (context.check(TokenType.SCOPE)
                    || context.check(TokenType.IDENTIFIER) && context.peekAt(1).type() == TokenType.SCOPE)) {
                Token first = context.peek();
                var parsed = CppOperatorNameParser.parseQualified(context);
                if (parsed == null) return null;
                Token last = parsed.nameToken();
                direct = new Declarator(last.lexeme(), new ArrayList<>(), first, last, last, parsed.qualifiedName(), parsed.operatorName());
            } else if (context.match(TokenType.IDENTIFIER)) {
                Token nameToken = context.previous();
                direct = new Declarator(nameToken.lexeme(), new ArrayList<>(), nameToken, nameToken, nameToken);
            } else if (!nameRequired && context.check(TokenType.LEFT_PAREN)
                    && (context.peekAt(1).type() == TokenType.RIGHT_PAREN || canStartTypeAt(1)
                    || context.peekAt(1).type() == TokenType.ELLIPSIS)) {
                Token anchor = context.peek();
                direct = new Declarator("", new ArrayList<>(), anchor, anchor, anchor);
            } else if (context.match(TokenType.LEFT_PAREN)) {
                Token startToken = context.previous();
                direct = parseDeclarator(expectedNameMessage, nameRequired, allowQualifiedName, referenceBase);
                Token endToken = context.consume(TokenType.RIGHT_PAREN, "期望 ')'");
                if (direct == null || endToken == null) {
                    return null;
                }
                direct = direct.withRange(startToken, endToken);
            } else if (!nameRequired) {
                Token anchor = pointerLayers.isEmpty() ? context.peek() : pointerLayers.getFirst().token();
                direct = new Declarator("", new ArrayList<>(), anchor, anchor, anchor);
            } else {
                context.report(context.peek(), expectedNameMessage);
                return null;
            }

            boolean memberScope = direct.qualifiedName() != null && direct.qualifiedName().segments().size() > 1;
            if (memberScope) enterMemberDefinitionScope(direct.qualifiedName());
            try {
                return parseDeclaratorSuffix(direct, pointerLayers, referenceBase);
            } finally {
                if (memberScope) exitMemberDefinitionScope();
            }
        }

        private Declarator parseDeclaratorSuffix(Declarator direct, List<PointerLayer> pointerLayers, boolean referenceBase) {
            while (context.check(TokenType.LEFT_BRACKET) || context.check(TokenType.LEFT_PAREN)) {
                // A named declarator followed by an expression is direct initialization. A
                // type (or empty list) still begins a function declarator, including reference returns.
                if (isCpp() && context.check(TokenType.LEFT_PAREN) && !direct.name().isEmpty()
                        && !canStartTypeAt(1) && context.peekAt(1).type() != TokenType.RIGHT_PAREN
                        && context.peekAt(1).type() != TokenType.ELLIPSIS) break;
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
                        parameterList.variadic(),
                        isCpp()
                ));
                direct = direct.withEnd(endToken);
            }

            // The '*' nearest the identifier is the outermost pointer layer. Tokens are
            // collected left-to-right, so append them in reverse to preserve qualifiers
            // on their exact pointer level when Declarator.resolve walks inside-out.
            for (int index = pointerLayers.size() - 1; index >= 0; index--) {
                PointerLayer layer = pointerLayers.get(index);
                direct.modifiers().add(layer.reference() ? new ReferenceModifier() : new PointerModifier(layer.qualifiers()));
            }
            if (!pointerLayers.isEmpty()) {
                direct = direct.withStart(pointerLayers.getFirst().token());
            }
            return direct;
        }

        /** The caller owns the surrounding parentheses; shared by functions and constructors. */
        public ParameterList parseParameterList() {
            if (!isCpp()) return parseParameterListContents();
            enterScope(List.of());
            try { return parseParameterListContents(); }
            finally { exitScope(); }
        }

        private ParameterList parseParameterListContents() {
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
                    declarator = new Declarator("", new ArrayList<>(), baseType.endToken(), baseType.endToken(), baseType.endToken());
                }
                if (declarator.operatorName() != null) {
                    context.report(declarator.operatorName().range(), "形参不能使用运算符函数名称");
                    break;
                }
                MiniType parameterType = adjustParameterType(resolveDeclarator(declarator, baseType.type()));
                SourceRange range = SourceRange.span(baseType.startToken().range(), declarator.endToken().range());
                parameters.add(new ParsedParameter(declarator.name(), parameterType, range));
                if (isCpp() && !declarator.name().isEmpty()) declareOrdinaryName(declarator.name(), range);
            } while (context.match(TokenType.COMMA));
            return new ParameterList(parameters, variadic);
        }

        private boolean canStartDeclarator(boolean allowIdentifier) {
            return context.check(TokenType.STAR)
                    || isCpp() && (context.check(TokenType.AMPERSAND) || context.check(TokenType.AMPERSAND_AMPERSAND))
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
            } else if (context.check(TokenType.STRUCT) || isCpp() && context.check(TokenType.CLASS)) {
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
            } else if (isCpp() && (context.check(TokenType.IDENTIFIER) || context.check(TokenType.SCOPE))) {
                var name = CppNameParser.parseName(context);
                if (name == null) return null;
                var result = cppTypes.lookup(name);
                if (result.kind() != CppTypeEnvironment.Kind.TYPE) {
                    context.report(name.range(), "此位置不能将名称作为类型使用："
                            + (name.global() ? "::" : "") + String.join("::", name.segments()) + " (" + result.kind() + ")");
                    return null;
                }
                type = result.type();
                end = context.previous();
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
            if (isCpp()) return parseCppAggregateType(union, startToken);
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
            return parseAggregateDefinition(MiniType.struct(internalName), union, startToken);
        }

        private BaseType parseCppAggregateType(boolean union, Token startToken) {
            var name = CppNameParser.peekName(context, 0);
            if (name != null) name = CppNameParser.parseName(context);
            Token end = name == null ? startToken : context.previous();
            if (context.match(TokenType.LEFT_BRACE)) {
                if (name != null && (name.global() || name.segments().size() != 1)) {
                    context.unsupportedCpp(name.range(), "限定名称的结构体定义尚未实现");
                    return null;
                }
                String simple = name == null ? "$anonymous$" + anonymousAggregateIndex++ : name.segments().getFirst();
                MiniType type = declareAggregate(simple, union, true, name == null ? startToken.range() : name.range());
                return parseAggregateDefinition(type, union, startToken);
            }
            if (name == null) {
                context.report(context.peek(), union ? "期望联合体名称或定义" : "期望结构体名称或定义");
                return null;
            }
            var lookup = cppTypes.lookupElaborated(name);
            MiniType type;
            if (lookup.kind() == CppTypeEnvironment.Kind.MISSING && !name.global() && name.segments().size() == 1) {
                type = declareAggregate(name.segments().getFirst(), union, false, name.range());
                String identity = ((MiniType.StructType) type.unqualified()).name();
                var info = union ? null : new Declaration.CppRecordInfo(
                        startToken.type() == TokenType.CLASS ? Declaration.RecordKey.CLASS : Declaration.RecordKey.STRUCT,
                        List.of(), startToken.range());
                aggregateSink.accept(new StructDecl(identity, List.of(), false, union, info,
                        SourceRange.span(startToken.range(), end.range())));
            } else if (lookup.kind() == CppTypeEnvironment.Kind.TYPE && lookup.type().unqualified() instanceof MiniType.StructType tag) {
                type = lookup.type();
                if (union != tag.name().startsWith("$union$")) {
                    context.report(name.range(), "struct/union 种类与已声明类型不一致");
                    return null;
                }
            } else {
                context.report(name.range(), "此位置未找到可使用的结构体类型：" + String.join("::", name.segments()));
                return null;
            }
            return new BaseType(type, startToken, end);
        }

        private BaseType parseAggregateDefinition(MiniType type, boolean union, Token startToken) {
            if (isCpp() && cppRecordParser != null) {
                StructDecl declaration = cppRecordParser.parseDefinition(type, union, startToken);
                if (declaration == null) return null;
                recordAggregateFields(declaration);
                aggregateSink.accept(declaration);
                context.build(declaration, "CppRecord " + declaration.name(), declaration.range());
                return new BaseType(type, startToken, context.previous());
            }
            enterMemberScope(type);
            try { return parseAggregateDefinitionContents(type, union, startToken); }
            finally { exitMemberScope(); }
        }

        private BaseType parseAggregateDefinitionContents(MiniType type, boolean union, Token startToken) {
            String internalName = ((MiniType.StructType) type.unqualified()).name();
            ArrayList<minic.compiler.parser.node.Declaration.StructField> fields = new ArrayList<>();
            while (!context.check(TokenType.RIGHT_BRACE) && !context.isAtEnd()) {
                List<minic.compiler.parser.node.Declaration.AlignmentSpec> specs = parseAlignmentSpecs();
                BaseType fieldBase = parseBaseType("期望字段类型");
                if (fieldBase == null) { context.synchronizeStatement(); continue; }
                if (context.check(TokenType.SEMICOLON)
                        && fieldBase.type().unqualified() instanceof MiniType.StructType) {
                    Token end = context.advance();
                    var field = new minic.compiler.parser.node.Declaration.StructField(
                            "", fieldBase.type(), true, specs,
                            SourceRange.span(fieldBase.startToken().range(), end.range()));
                    fields.add(field);
                    declareMemberField(field);
                    continue;
                }
                Declarator declarator = parseDeclarator("期望字段名", true);
                Token end = context.consume(TokenType.SEMICOLON, "期望 ';'");
                if (declarator != null && end != null) {
                    var field = new minic.compiler.parser.node.Declaration.StructField(
                            declarator.name(), resolveDeclarator(declarator, fieldBase.type()), false, specs,
                            SourceRange.span(fieldBase.startToken().range(), end.range()));
                    fields.add(field);
                    declareMemberField(field);
                }
            }
            Token close = context.consume(TokenType.RIGHT_BRACE, "期望 '}'");
            if (close == null) return null;
            StructDecl declaration = new StructDecl(internalName, fields, true, union,
                    SourceRange.span(startToken.range(), close.range()));
            recordAggregateFields(declaration);
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

        private record PointerLayer(Token token, java.util.Set<MiniType.TypeQualifier> qualifiers, boolean reference) {
        }

        private record ReferenceModifier() implements DeclaratorModifier {
            @Override public MiniType apply(MiniType inner) { return inner.referenceTo(); }
        }

        private MiniType resolveDeclarator(Declarator declarator, MiniType baseType) {
            MiniType type = baseType;
            SourceRange range = SourceRange.span(declarator.startToken().range(), declarator.endToken().range());
            boolean directReference = false;
            for (int index = declarator.modifiers().size() - 1; index >= 0; index--) {
                DeclaratorModifier modifier = declarator.modifiers().get(index);
                if (isCpp() && modifier instanceof ReferenceModifier && directReference) {
                    context.report(range, "不能直接声明引用的引用；引用折叠仅适用于类型别名");
                }
                type = modifier.apply(type);
                // A function/array/pointer layer separates references; a reference already
                // present in baseType came through an alias and is allowed to collapse.
                directReference = modifier instanceof ReferenceModifier;
            }
            if (isCpp()) validateReferenceShape(type, range);
            return type;
        }

        private void validateReferenceShape(MiniType type, SourceRange range) {
            switch (type.unqualified()) {
                case MiniType.ReferenceType reference -> {
                    if (reference.referent().isVoid()) context.report(range, "引用不能指向 void");
                    validateReferenceShape(reference.referent(), range);
                }
                case MiniType.PointerType pointer -> {
                    if (pointer.pointee().isReference()) context.report(range, "不能声明指向引用的指针");
                    validateReferenceShape(pointer.pointee(), range);
                }
                case MiniType.ArrayType array -> {
                    if (array.elementType().isReference()) context.report(range, "数组元素不能是引用");
                    validateReferenceShape(array.elementType(), range);
                }
                case MiniType.FunctionType function -> {
                    validateReferenceShape(function.returnType(), range);
                    function.parameterTypes().forEach(parameter -> validateReferenceShape(parameter, range));
                }
                default -> { }
            }
        }

        private record ArrayModifier(int length) implements DeclaratorModifier {
            @Override
            public MiniType apply(MiniType inner) {
                return inner.arrayOf(length);
            }
        }

        private record FunctionModifier(
                List<ParsedParameter> parameters,
                boolean variadic,
                boolean preserveReturnQualifiers
        ) implements DeclaratorModifier {
            private FunctionModifier {
                parameters = List.copyOf(parameters);
            }

            @Override
            public MiniType apply(MiniType inner) {
                return MiniType.function(
                        preserveReturnQualifiers ? inner : inner.unqualified(),
                        parameters.stream().map(ParsedParameter::type).map(MiniType::unqualified).toList(),
                        variadic
                );
            }
        }

        private record Declarator(
                String name,
                ArrayList<DeclaratorModifier> modifiers,
                Token startToken,
                Token endToken,
                Token nameToken,
                QualifiedName qualifiedName,
                OperatorName operatorName
        ) {
            private Declarator(String name, ArrayList<DeclaratorModifier> modifiers, Token startToken,
                               Token endToken, Token nameToken) {
                this(name, modifiers, startToken, endToken, nameToken, null, null);
            }
            private FunctionModifier topFunction() {
                return !modifiers.isEmpty() && modifiers.getFirst() instanceof FunctionModifier function
                        ? function
                        : null;
            }

            private Declarator withRange(Token start, Token end) {
                return new Declarator(name, modifiers, start, end, nameToken, qualifiedName, operatorName);
            }

            private Declarator withStart(Token start) {
                return new Declarator(name, modifiers, start, endToken, nameToken, qualifiedName, operatorName);
            }

            private Declarator withEnd(Token end) {
                return new Declarator(name, modifiers, startToken, end, nameToken, qualifiedName, operatorName);
            }
        }

        public record ParameterList(List<ParsedParameter> parameters, boolean variadic) {
            public ParameterList {
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
            List<minic.compiler.parser.node.Declaration.AlignmentSpec> alignmentSpecs,
            SourceRange nameRange,
            QualifiedName qualifiedName,
            OperatorName operatorName
    ) {
        public ParsedNamedType(String name, MiniType type, SourceRange range, List<ParsedParameter> parameters,
                               boolean variadic, List<Declaration.AlignmentSpec> alignmentSpecs, SourceRange nameRange,
                               QualifiedName qualifiedName) {
            this(name, type, range, parameters, variadic, alignmentSpecs, nameRange, qualifiedName, null);
        }
        public ParsedNamedType(String name, MiniType type, SourceRange range, List<ParsedParameter> parameters,
                               boolean variadic, List<Declaration.AlignmentSpec> alignmentSpecs, SourceRange nameRange) {
            this(name, type, range, parameters, variadic, alignmentSpecs, nameRange, null);
        }
        public ParsedNamedType(String name, MiniType type, SourceRange range, List<ParsedParameter> parameters,
                               boolean variadic, List<Declaration.AlignmentSpec> alignmentSpecs) {
            this(name, type, range, parameters, variadic, alignmentSpecs, range);
        }
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
