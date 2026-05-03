package minic.compiler.lexer;

import minic.compiler.stage.CompilerStageInput;
import minic.compiler.stage.CompilerStageOutput;
import minic.compiler.stage.CompilerStageResult;
import minic.compiler.stage.CompilerStageSnapshot;
import minic.compiler.stage.CompilerStageState;
import minic.compiler.stage.CompilerStageStatus;
import minic.compiler.stage.CompilerStageWork;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.runtime.step.CompileStage;
import minic.runtime.step.StageProgress;
import minic.source.SourceFile;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 可正向步进的 lexer 状态。
 */
public final class LexerState implements CompilerStageState<LexerState.Input, LexerState.Work, LexerState.Output> {
    private final Input input;
    private final Work work;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private Token currentToken;
    private Diagnostic currentDiagnostic;
    private boolean eofEmitted;
    private long stepCount;

    /**
     * 创建 lexer 状态。
     *
     * @param sourceFile 待分析源码
     */
    public LexerState(SourceFile sourceFile) {
        input = new Input(Objects.requireNonNull(sourceFile, "sourceFile"));
        work = new Work();
    }

    @Override
    public CompileStage stage() {
        return CompileStage.LEXER;
    }

    @Override
    public Input input() {
        return input;
    }

    @Override
    public Work work() {
        return work;
    }

    @Override
    public CompilerStageSnapshot snapshot() {
        CompilerStageStatus status = eofEmitted ? CompilerStageStatus.COMPLETED
                : stepCount == 0 ? CompilerStageStatus.NOT_STARTED : CompilerStageStatus.RUNNING;
        String item = currentToken != null ? currentToken.kind() + " " + currentToken.lexeme()
                : currentDiagnostic != null ? currentDiagnostic.message() : "";
        return new CompilerStageSnapshot(
                CompileStage.LEXER,
                status,
                new StageProgress(stepCount, -1, eofEmitted),
                item,
                diagnostics
        );
    }

    @Override
    public boolean canNext() {
        return !eofEmitted;
    }

    /**
     * 推进 lexer，直到产出一个 token 或 diagnostic。
     *
     * @return 本步产物
     */
    public LexStep next() {
        if (!canNext()) {
            throw new IllegalStateException("lexer state is already completed");
        }
        currentToken = null;
        currentDiagnostic = null;
        while (!isAtEnd()) {
            int beforeTokens = tokens.size();
            int beforeDiagnostics = diagnostics.size();
            int startOffset = work.currentOffset;
            char character = advanceChar();
            switch (character) {
                case ' ', '\r', '\t', '\n' -> {
                    // 空白由 lexer 跳过，不产出 token。
                }
                case '+' -> addToken(TokenKind.PLUS, startOffset);
                case '-' -> addToken(match('>') ? TokenKind.ARROW : TokenKind.MINUS, startOffset);
                case '*' -> addToken(TokenKind.STAR, startOffset);
                case '&' -> addToken(TokenKind.AMPERSAND, startOffset);
                case '/' -> {
                    if (match('/')) {
                        skipLineComment();
                    } else {
                        addToken(TokenKind.SLASH, startOffset);
                    }
                }
                case '=' -> addToken(match('=') ? TokenKind.EQUAL_EQUAL : TokenKind.EQUAL, startOffset);
                case '!' -> {
                    if (match('=')) {
                        addToken(TokenKind.BANG_EQUAL, startOffset);
                    } else {
                        addInvalidCharacterDiagnostic(startOffset);
                    }
                }
                case '<' -> addToken(match('=') ? TokenKind.LESS_EQUAL : TokenKind.LESS, startOffset);
                case '>' -> addToken(match('=') ? TokenKind.GREATER_EQUAL : TokenKind.GREATER, startOffset);
                case '(' -> addToken(TokenKind.LEFT_PAREN, startOffset);
                case ')' -> addToken(TokenKind.RIGHT_PAREN, startOffset);
                case '{' -> addToken(TokenKind.LEFT_BRACE, startOffset);
                case '}' -> addToken(TokenKind.RIGHT_BRACE, startOffset);
                case '[' -> addToken(TokenKind.LEFT_BRACKET, startOffset);
                case ']' -> addToken(TokenKind.RIGHT_BRACKET, startOffset);
                case ';' -> addToken(TokenKind.SEMICOLON, startOffset);
                case ',' -> addToken(TokenKind.COMMA, startOffset);
                case '.' -> addToken(TokenKind.DOT, startOffset);
                case '"' -> lexStringLiteral(startOffset);
                case '\'' -> lexCharLiteral(startOffset);
                default -> {
                    if (isIdentifierStart(character)) {
                        lexIdentifier(startOffset);
                    } else if (isAsciiDigit(character)) {
                        lexIntegerLiteral(startOffset);
                    } else {
                        addInvalidCharacterDiagnostic(startOffset);
                    }
                }
            }
            LexStep step = producedStep(beforeTokens, beforeDiagnostics);
            if (step != null) {
                stepCount++;
                return step;
            }
        }
        Token eof = new Token(
                TokenKind.EOF,
                "",
                new SourceRange(input.sourceFile, work.currentOffset, work.currentOffset)
        );
        tokens.add(eof);
        currentToken = eof;
        eofEmitted = true;
        stepCount++;
        return LexStep.token(eof);
    }

    @Override
    public CompilerStageSnapshot advance() {
        next();
        return snapshot();
    }

    @Override
    public CompilerStageResult<Output> result() {
        return CompilerStageResult.success(CompileStage.LEXER, new Output(toLexResult()));
    }

    /**
     * 返回最近产出的 token。
     *
     * @return token Optional
     */
    public Optional<Token> currentToken() {
        return Optional.ofNullable(currentToken);
    }

    /**
     * 返回最近产出的 diagnostic。
     *
     * @return diagnostic Optional
     */
    public Optional<Diagnostic> currentDiagnostic() {
        return Optional.ofNullable(currentDiagnostic);
    }

    /**
     * 返回已产出 token。
     *
     * @return token 列表
     */
    public List<Token> tokens() {
        return List.copyOf(tokens);
    }

    /**
     * 返回已产出 diagnostics。
     *
     * @return diagnostic 列表
     */
    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    /**
     * 构建与原 lexer API 等价的词法结果。
     *
     * @return 词法结果
     */
    public LexResult toLexResult() {
        return new LexResult(tokens, diagnostics);
    }

    private LexStep producedStep(int beforeTokens, int beforeDiagnostics) {
        if (tokens.size() > beforeTokens) {
            currentToken = tokens.getLast();
            return LexStep.token(currentToken);
        }
        if (diagnostics.size() > beforeDiagnostics) {
            currentDiagnostic = diagnostics.getLast();
            return LexStep.diagnostic(currentDiagnostic);
        }
        return null;
    }

    private boolean isAtEnd() {
        return work.currentOffset >= input.sourceFile.content().length();
    }

    private char advanceChar() {
        return input.sourceFile.content().charAt(work.currentOffset++);
    }

    private boolean match(char expected) {
        if (isAtEnd() || input.sourceFile.content().charAt(work.currentOffset) != expected) {
            return false;
        }
        work.currentOffset++;
        return true;
    }

    private void skipLineComment() {
        while (!isAtEnd() && input.sourceFile.content().charAt(work.currentOffset) != '\n') {
            work.currentOffset++;
        }
    }

    private void lexIdentifier(int startOffset) {
        while (!isAtEnd() && isIdentifierPart(input.sourceFile.content().charAt(work.currentOffset))) {
            work.currentOffset++;
        }

        String lexeme = input.sourceFile.content().substring(startOffset, work.currentOffset);
        TokenKind kind = switch (lexeme) {
            case "bool" -> TokenKind.BOOL;
            case "char" -> TokenKind.CHAR;
            case "int" -> TokenKind.INT;
            case "long" -> TokenKind.LONG;
            case "float" -> TokenKind.FLOAT;
            case "double" -> TokenKind.DOUBLE;
            case "extern" -> TokenKind.EXTERN;
            case "struct" -> TokenKind.STRUCT;
            case "return" -> TokenKind.RETURN;
            case "if" -> TokenKind.IF;
            case "else" -> TokenKind.ELSE;
            case "while" -> TokenKind.WHILE;
            case "for" -> TokenKind.FOR;
            case "break" -> TokenKind.BREAK;
            case "continue" -> TokenKind.CONTINUE;
            case "true", "false" -> TokenKind.BOOL_LITERAL;
            case "NULL" -> TokenKind.NULL_LITERAL;
            default -> TokenKind.IDENTIFIER;
        };
        Object literalValue = switch (kind) {
            case BOOL_LITERAL -> Boolean.parseBoolean(lexeme);
            default -> null;
        };
        addToken(kind, startOffset, literalValue);
    }

    private void lexIntegerLiteral(int startOffset) {
        while (!isAtEnd() && isAsciiDigit(input.sourceFile.content().charAt(work.currentOffset))) {
            work.currentOffset++;
        }
        boolean floating = false;
        if (!isAtEnd()
                && input.sourceFile.content().charAt(work.currentOffset) == '.'
                && hasNextAsciiDigit()) {
            floating = true;
            work.currentOffset++;
            while (!isAtEnd() && isAsciiDigit(input.sourceFile.content().charAt(work.currentOffset))) {
                work.currentOffset++;
            }
        }
        boolean longLiteral = false;
        boolean floatLiteral = false;
        if (!isAtEnd()) {
            char suffix = input.sourceFile.content().charAt(work.currentOffset);
            if (!floating && (suffix == 'l' || suffix == 'L')) {
                longLiteral = true;
                work.currentOffset++;
            } else if (floating && (suffix == 'f' || suffix == 'F')) {
                floatLiteral = true;
                work.currentOffset++;
            }
        }

        String lexeme = input.sourceFile.content().substring(startOffset, work.currentOffset);
        if (floating) {
            String valueLexeme = floatLiteral ? lexeme.substring(0, lexeme.length() - 1) : lexeme;
            Object literalValue;
            try {
                if (floatLiteral) {
                    literalValue = Float.parseFloat(valueLexeme);
                    if (!Float.isFinite((Float) literalValue)) {
                        addNumericOverflowDiagnostic(startOffset, work.currentOffset, "浮点字面量超出范围");
                        return;
                    }
                } else {
                    literalValue = Double.parseDouble(valueLexeme);
                    if (!Double.isFinite((Double) literalValue)) {
                        addNumericOverflowDiagnostic(startOffset, work.currentOffset, "浮点字面量超出范围");
                        return;
                    }
                }
            } catch (NumberFormatException exception) {
                addNumericOverflowDiagnostic(startOffset, work.currentOffset, "浮点字面量超出范围");
                return;
            }
            tokens.add(new Token(
                    floatLiteral ? TokenKind.FLOAT_LITERAL : TokenKind.DOUBLE_LITERAL,
                    lexeme,
                    new SourceRange(input.sourceFile, startOffset, work.currentOffset),
                    literalValue
            ));
            return;
        }
        if (longLiteral) {
            String valueLexeme = lexeme.substring(0, lexeme.length() - 1);
            long literalValue;
            try {
                literalValue = Long.parseLong(valueLexeme);
            } catch (NumberFormatException exception) {
                addNumericOverflowDiagnostic(startOffset, work.currentOffset, "long 字面量超出范围");
                return;
            }
            tokens.add(new Token(
                    TokenKind.LONG_LITERAL,
                    lexeme,
                    new SourceRange(input.sourceFile, startOffset, work.currentOffset),
                    literalValue
            ));
            return;
        }
        int literalValue;
        try {
            literalValue = Integer.parseInt(lexeme);
        } catch (NumberFormatException exception) {
            addNumericOverflowDiagnostic(startOffset, work.currentOffset, "整数字面量超出范围");
            return;
        }
        tokens.add(new Token(
                TokenKind.INTEGER_LITERAL,
                lexeme,
                new SourceRange(input.sourceFile, startOffset, work.currentOffset),
                literalValue
        ));
    }

    private boolean hasNextAsciiDigit() {
        int nextOffset = work.currentOffset + 1;
        return nextOffset < input.sourceFile.content().length()
                && isAsciiDigit(input.sourceFile.content().charAt(nextOffset));
    }

    private void lexStringLiteral(int startOffset) {
        StringBuilder value = new StringBuilder();
        while (!isAtEnd() && input.sourceFile.content().charAt(work.currentOffset) != '"') {
            char character = advanceChar();
            if (character == '\n' || character == '\r') {
                diagnostics.add(new Diagnostic(
                        "LEX002",
                        DiagnosticSeverity.ERROR,
                        "字符串字面量不能跨行",
                        new SourceRange(input.sourceFile, startOffset, work.currentOffset)
                ));
                return;
            }
            if (character == '\\') {
                if (isAtEnd()) {
                    addUnterminatedStringDiagnostic(startOffset);
                    return;
                }
                value.append(lexEscape(startOffset));
            } else {
                value.append(character);
            }
        }

        if (isAtEnd()) {
            addUnterminatedStringDiagnostic(startOffset);
            return;
        }

        work.currentOffset++;
        tokens.add(new Token(
                TokenKind.STRING_LITERAL,
                input.sourceFile.content().substring(startOffset, work.currentOffset),
                new SourceRange(input.sourceFile, startOffset, work.currentOffset),
                value.toString()
        ));
    }

    private char lexEscape(int startOffset) {
        char escaped = advanceChar();
        return switch (escaped) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '\\' -> '\\';
            case '"' -> '"';
            case '0' -> '\0';
            default -> {
                diagnostics.add(new Diagnostic(
                        "LEX003",
                        DiagnosticSeverity.ERROR,
                        "不支持的字符串转义：" + escaped,
                        new SourceRange(input.sourceFile, startOffset, work.currentOffset)
                ));
                yield escaped;
            }
        };
    }

    private void lexCharLiteral(int startOffset) {
        if (isAtEnd()) {
            addUnterminatedCharDiagnostic(startOffset);
            return;
        }
        char value = advanceChar();
        if (value == '\n' || value == '\r') {
            diagnostics.add(new Diagnostic(
                    "LEX004",
                    DiagnosticSeverity.ERROR,
                    "字符字面量不能跨行",
                    new SourceRange(input.sourceFile, startOffset, work.currentOffset)
            ));
            return;
        }
        if (value == '\\') {
            if (isAtEnd()) {
                addUnterminatedCharDiagnostic(startOffset);
                return;
            }
            value = lexEscape(startOffset);
        }
        if (isAtEnd() || advanceChar() != '\'') {
            diagnostics.add(new Diagnostic(
                    "LEX004",
                    DiagnosticSeverity.ERROR,
                    "字符字面量必须只包含一个字符",
                    new SourceRange(input.sourceFile, startOffset, work.currentOffset)
            ));
            while (!isAtEnd()
                    && input.sourceFile.content().charAt(work.currentOffset) != '\''
                    && input.sourceFile.content().charAt(work.currentOffset) != '\n'
                    && input.sourceFile.content().charAt(work.currentOffset) != '\r') {
                work.currentOffset++;
            }
            match('\'');
            return;
        }
        tokens.add(new Token(
                TokenKind.CHAR_LITERAL,
                input.sourceFile.content().substring(startOffset, work.currentOffset),
                new SourceRange(input.sourceFile, startOffset, work.currentOffset),
                value
        ));
    }

    private void addUnterminatedStringDiagnostic(int startOffset) {
        diagnostics.add(new Diagnostic(
                "LEX002",
                DiagnosticSeverity.ERROR,
                "字符串字面量缺少结束引号",
                new SourceRange(input.sourceFile, startOffset, work.currentOffset)
        ));
    }

    private void addUnterminatedCharDiagnostic(int startOffset) {
        diagnostics.add(new Diagnostic(
                "LEX004",
                DiagnosticSeverity.ERROR,
                "字符字面量缺少结束引号",
                new SourceRange(input.sourceFile, startOffset, work.currentOffset)
        ));
    }

    private boolean isIdentifierStart(char character) {
        return character == '_' || isAsciiLetter(character);
    }

    private boolean isIdentifierPart(char character) {
        return isIdentifierStart(character) || isAsciiDigit(character);
    }

    private boolean isAsciiLetter(char character) {
        return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
    }

    private boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private void addInvalidCharacterDiagnostic(int startOffset) {
        diagnostics.add(new Diagnostic(
                "LEX001",
                DiagnosticSeverity.ERROR,
                "非法字符：" + input.sourceFile.content().charAt(startOffset),
                new SourceRange(input.sourceFile, startOffset, work.currentOffset)
        ));
    }

    private void addNumericOverflowDiagnostic(int startOffset, int endOffset, String message) {
        diagnostics.add(new Diagnostic(
                "LEX005",
                DiagnosticSeverity.ERROR,
                message,
                new SourceRange(input.sourceFile, startOffset, endOffset)
        ));
    }

    private void addToken(TokenKind kind, int startOffset) {
        addToken(kind, startOffset, null);
    }

    private void addToken(TokenKind kind, int startOffset, Object literalValue) {
        tokens.add(new Token(
                kind,
                input.sourceFile.content().substring(startOffset, work.currentOffset),
                new SourceRange(input.sourceFile, startOffset, work.currentOffset),
                literalValue
        ));
    }

    /**
     * Lexer 阶段输入数据。
     *
     * @param sourceFile 源码文件
     */
    public record Input(SourceFile sourceFile) implements CompilerStageInput {
        /**
         * 创建输入数据。
         *
         * @param sourceFile 源码文件
         */
        public Input {
            Objects.requireNonNull(sourceFile, "sourceFile");
        }
    }

    /**
     * Lexer 阶段内部工作数据。
     */
    public static final class Work implements CompilerStageWork {
        private int currentOffset;

        /**
         * 返回当前源码偏移。
         *
         * @return 当前源码偏移
         */
        public int currentOffset() {
            return currentOffset;
        }
    }

    /**
     * Lexer 阶段输出数据。
     *
     * @param lexResult 词法结果
     */
    public record Output(LexResult lexResult) implements CompilerStageOutput {
        /**
         * 创建输出数据。
         *
         * @param lexResult 词法结果
         */
        public Output {
            Objects.requireNonNull(lexResult, "lexResult");
        }
    }
}
