package minic.compiler.parser;

import minic.compiler.CompilerApi;
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
import minic.compiler.type.MiniType;
import minic.diagnostics.Diagnostic;
import minic.source.SourceRange;

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
    private final boolean traceEnabled;
    private List<Token> tokens;
    private Context context;
    private DeclarationManager declarationManager;
    private final ArrayList<StructDecl> structs = new ArrayList<>();
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
        lexer = null;
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
        this.traceEnabled = traceEnabled;
    }

    private void initialize(List<Token> sourceTokens) {
        tokens = List.copyOf(Objects.requireNonNull(sourceTokens, "tokens"));
        context = new Context(tokens, traceEnabled);
        TypeReader typeReader = new TypeReader(context);
        ExpressionManager expressionManager = new ExpressionManager(context, typeReader);
        StatementManager statementManager = new StatementManager(context, expressionManager, typeReader);
        declarationManager = new DeclarationManager(context, statementManager, typeReader);
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

    @Override
    public boolean succeeded() {
        return completed && parserResult != null && parserResult.diagnostics().isEmpty();
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
            return null;
        }

        if (context.isAtEnd()) {
            parserResult = buildResult();
            sourceCompleted = true;
            stepCount++;
            return context.peek().range();
        }

        if (context.check(TokenType.STRUCT) && isStructDeclaration()) {
            StructDecl structDecl = declarationManager.parseStructDecl();
            if (structDecl != null) {
                structs.add(structDecl);
                captureNode(structDecl);
            } else {
                context.synchronizeFunction();
            }
        } else {
            FunctionDecl functionDecl = declarationManager.parseFunctionDecl();
            if (functionDecl != null) {
                functions.add(functionDecl);
                captureNode(functionDecl);
            } else {
                context.synchronizeFunction();
            }
        }
        stepCount++;
        return currentNode != null
                ? currentNode.range()
                : context.peek().range();
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

    public Optional<AstNode> currentNode() {
        return Optional.ofNullable(currentNode);
    }

    public List<AstNode> completedNodes() {
        return List.copyOf(completedNodes);
    }

    public List<Diagnostic> diagnostics() {
        return context == null ? List.of() : List.copyOf(context.diagnostics());
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
                && context.peekAt(2).type() == TokenType.LEFT_BRACE;
    }

    private ParserResult buildResult() {
        return new ParserResult(new Program(structs, functions, programRange()), context.diagnostics());
    }

    private SourceRange programRange() {
        if (structs.isEmpty() && functions.isEmpty()) {
            return context.peek().range();
        }
        SourceRange first = !structs.isEmpty() ? structs.getFirst().range() : functions.getFirst().range();
        SourceRange last = !functions.isEmpty() ? functions.getLast().range() : structs.getLast().range();
        return SourceRange.span(first, last);
    }

    /** 三个 Manager 共享的 token 游标、诊断和 trace 上下文。 */
    public static final class Context {
        private final List<Token> tokens;
        private final ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        private final ArrayList<TraceEvent> traceEvents;
        private int currentIndex;
        private boolean functionBoundaryRecovered;

        private Context(List<Token> tokens, boolean traceEnabled) {
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("tokens must contain EOF");
            }
            this.tokens = tokens;
            traceEvents = traceEnabled ? new ArrayList<>() : null;
        }

        public List<Diagnostic> diagnostics() {
            return diagnostics;
        }

        public int currentIndex() {
            return currentIndex;
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
            report(peek(), message);
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
            report(token.range(), message);
        }

        public void report(SourceRange range, String message) {
            diagnostics.add(new Diagnostic("PAR001", Diagnostic.Severity.ERROR, message, range));
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
            if (type == TokenType.STRUCT) {
                if (peekAt(offset + 1).type() != TokenType.IDENTIFIER) {
                    return false;
                }
                offset += 2;
            } else if (type == TokenType.BOOL
                    || type == TokenType.CHAR
                    || type == TokenType.INT
                    || type == TokenType.LONG
                    || type == TokenType.FLOAT
                    || type == TokenType.DOUBLE) {
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

        public TypeReader(Context context) {
            this.context = Objects.requireNonNull(context, "context");
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
            BaseType baseType = parseBaseType(expectedTypeMessage);
            if (baseType == null) {
                return null;
            }
            Declarator declarator = parseDeclarator(expectedNameMessage, true);
            if (declarator == null || declarator.name().isEmpty()) {
                return null;
            }
            FunctionModifier topFunction = declarator.topFunction();
            return new ParsedNamedType(
                    declarator.name(),
                    declarator.resolve(baseType.type()),
                    SourceRange.span(baseType.startToken().range(), declarator.endToken().range()),
                    topFunction == null ? List.of() : topFunction.parameters(),
                    topFunction != null && topFunction.variadic()
            );
        }

        public boolean canStartType() {
            return context.check(TokenType.BOOL)
                    || context.check(TokenType.CHAR)
                    || context.check(TokenType.INT)
                    || context.check(TokenType.LONG)
                    || context.check(TokenType.FLOAT)
                    || context.check(TokenType.DOUBLE)
                    || context.check(TokenType.VOID)
                    || context.check(TokenType.STRUCT);
        }

        private Declarator parseDeclarator(String expectedNameMessage, boolean nameRequired) {
            ArrayList<Token> pointerTokens = new ArrayList<>();
            while (context.match(TokenType.STAR)) {
                pointerTokens.add(context.previous());
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
                Token anchor = pointerTokens.isEmpty() ? context.peek() : pointerTokens.getFirst();
                direct = new Declarator("", new ArrayList<>(), anchor, anchor);
            } else {
                context.report(context.peek(), expectedNameMessage);
                return null;
            }

            while (context.check(TokenType.LEFT_BRACKET) || context.check(TokenType.LEFT_PAREN)) {
                if (context.match(TokenType.LEFT_BRACKET)) {
                    Token lengthToken = context.consume(TokenType.INTEGER_LITERAL, "期望数组长度");
                    Token endToken = context.consume(TokenType.RIGHT_BRACKET, "期望 ']'");
                    if (lengthToken == null || endToken == null) {
                        return null;
                    }
                    int length = (Integer) lengthToken.literalValue();
                    if (length <= 0) {
                        context.report(lengthToken, "数组长度必须大于 0");
                        length = 1;
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

            for (Token ignored : pointerTokens) {
                direct.modifiers().add(PointerModifier.INSTANCE);
            }
            if (!pointerTokens.isEmpty()) {
                direct = direct.withStart(pointerTokens.getFirst());
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
            if (type instanceof MiniType.ArrayType arrayType) {
                return arrayType.elementType().pointerTo();
            }
            if (type instanceof MiniType.FunctionType) {
                return type.pointerTo();
            }
            return type;
        }

        private BaseType parseBaseType(String expectedMessage) {
            if (context.check(TokenType.BOOL)) {
                Token token = context.advance();
                return new BaseType(MiniType.BOOL, token, token);
            }
            if (context.check(TokenType.CHAR)) {
                Token token = context.advance();
                return new BaseType(MiniType.CHAR, token, token);
            }
            if (context.check(TokenType.INT)) {
                Token token = context.advance();
                return new BaseType(MiniType.INT, token, token);
            }
            if (context.check(TokenType.LONG)) {
                Token token = context.advance();
                return new BaseType(MiniType.LONG, token, token);
            }
            if (context.check(TokenType.FLOAT)) {
                Token token = context.advance();
                return new BaseType(MiniType.FLOAT, token, token);
            }
            if (context.check(TokenType.DOUBLE)) {
                Token token = context.advance();
                return new BaseType(MiniType.DOUBLE, token, token);
            }
            if (context.check(TokenType.VOID)) {
                Token token = context.advance();
                return new BaseType(MiniType.VOID, token, token);
            }
            if (context.check(TokenType.STRUCT)) {
                return parseStructType();
            }
            context.report(context.peek(), expectedMessage);
            return null;
        }

        private BaseType parseStructType() {
            Token startToken = context.advance();
            Token nameToken = context.consume(TokenType.IDENTIFIER, "期望结构体类型名");
            return nameToken == null ? null : new BaseType(MiniType.struct(nameToken.lexeme()), startToken, nameToken);
        }

        private record BaseType(MiniType type, Token startToken, Token endToken) {
        }

        private interface DeclaratorModifier {
            MiniType apply(MiniType inner);
        }

        private enum PointerModifier implements DeclaratorModifier {
            INSTANCE;

            @Override
            public MiniType apply(MiniType inner) {
                return inner.pointerTo();
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
                boolean variadic
        ) implements DeclaratorModifier {
            private FunctionModifier {
                parameters = List.copyOf(parameters);
            }

            @Override
            public MiniType apply(MiniType inner) {
                return MiniType.function(
                        inner,
                        parameters.stream().map(ParsedParameter::type).toList(),
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
            boolean variadic
    ) {
        public ParsedNamedType {
            parameters = List.copyOf(parameters);
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
