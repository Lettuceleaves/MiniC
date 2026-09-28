package minic.compiler.lexer;

import minic.compiler.Loop;
import minic.compiler.Stage;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.preprocess.Preprocessor;
import minic.diagnostics.Diagnostic;
import minic.compiler.SourceFile;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MiniC 词法分析器。既可逐步执行，也可循环执行完整词法分析。
 */
public final class Lexer extends Stage {
    private final Preprocessor preprocessor;
    private SourceFile sourceFile;
    private SourceFile rangeSourceFile;
    private int[] sourceMap;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private int currentOffset;
    private Token currentToken;
    private Diagnostic currentDiagnostic;
    private boolean eofEmitted;
    private boolean completed;
    private long stepCount;

    /** 创建词法分析器。 */
    public Lexer(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        rangeSourceFile = sourceFile;
        preprocessor = null;
    }

    /**
     * 创建由 Preprocessor 提供输入的词法分析阶段。
     */
    public Lexer(Preprocessor preprocessor) {
        this.preprocessor = Objects.requireNonNull(preprocessor, "preprocessor");
    }

    @Override
    public boolean canNext() {
        return !completed;
    }

    @Override
    public boolean succeeded() {
        return completed && diagnostics.isEmpty();
    }

    /** @return 已执行的步数 */
    public long stepCount() {
        return stepCount;
    }

    /** @return 当前源码偏移 */
    public int currentOffset() {
        return currentOffset;
    }

    /**
     * 循环执行 {@link #step()}，直到独立的最后空步骤发出。
     *
     * @return 词法分析结果
     */
    public LexerResult lex() {
        new Loop(List.of(this)).run();
        return toLexerResult();
    }

    /**
     * 执行词法扫描主循环的一轮。
     */
    @Override
    public SourceRange step() {
        if (!canNext()) {
            throw new IllegalStateException("lexer step is already completed");
        }
        ensureSourceFile();
        currentToken = null;
        currentDiagnostic = null;
        int beforeTokens = tokens.size();
        int beforeDiagnostics = diagnostics.size();
        if (eofEmitted) {
            completed = true;
            stepCount++;
            return null;
        }
        if (isAtEnd()) {
            int eofOffset = currentOffset;
            Token eof = new Token(
                    TokenType.EOF,
                    "",
                    range(eofOffset, eofOffset)
            );
            tokens.add(eof);
            currentToken = eof;
            eofEmitted = true;
            stepCount++;
            return eof.range();
        }

        int startOffset = currentOffset;
        char character = advanceChar();
        switch (character) {
            case ' ', '\r', '\t', '\n' -> {
                // 空白由 lexer 跳过，不产出 token。
            }
            case '+' -> addToken(match('+') ? TokenType.PLUS_PLUS : match('=') ? TokenType.PLUS_EQUAL : TokenType.PLUS, startOffset);
            case '-' -> addToken(match('>') ? TokenType.ARROW
                    : match('-') ? TokenType.MINUS_MINUS
                    : match('=') ? TokenType.MINUS_EQUAL
                    : TokenType.MINUS, startOffset);
            case '*' -> addToken(match('=') ? TokenType.STAR_EQUAL : TokenType.STAR, startOffset);
            case '&' -> addToken(match('&') ? TokenType.AMPERSAND_AMPERSAND
                    : match('=') ? TokenType.AMPERSAND_EQUAL
                    : TokenType.AMPERSAND, startOffset);
            case '/' -> {
                if (match('/')) {
                    skipLineComment();
                } else {
                    addToken(match('=') ? TokenType.SLASH_EQUAL : TokenType.SLASH, startOffset);
                }
            }
            case '%' -> addToken(match('=') ? TokenType.PERCENT_EQUAL : TokenType.PERCENT, startOffset);
            case '|' -> addToken(match('|') ? TokenType.PIPE_PIPE
                    : match('=') ? TokenType.PIPE_EQUAL
                    : TokenType.PIPE, startOffset);
            case '^' -> addToken(match('=') ? TokenType.CARET_EQUAL : TokenType.CARET, startOffset);
            case '~' -> addToken(TokenType.TILDE, startOffset);
            case '=' -> addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL, startOffset);
            case '!' -> addToken(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG, startOffset);
            case '<' -> addToken(match('<')
                    ? match('=') ? TokenType.LESS_LESS_EQUAL : TokenType.LESS_LESS
                    : match('=') ? TokenType.LESS_EQUAL : TokenType.LESS, startOffset);
            case '>' -> addToken(match('>')
                    ? match('=') ? TokenType.GREATER_GREATER_EQUAL : TokenType.GREATER_GREATER
                    : match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER, startOffset);
            case '(' -> addToken(TokenType.LEFT_PAREN, startOffset);
            case ')' -> addToken(TokenType.RIGHT_PAREN, startOffset);
            case '{' -> addToken(TokenType.LEFT_BRACE, startOffset);
            case '}' -> addToken(TokenType.RIGHT_BRACE, startOffset);
            case '[' -> addToken(TokenType.LEFT_BRACKET, startOffset);
            case ']' -> addToken(TokenType.RIGHT_BRACKET, startOffset);
            case ';' -> addToken(TokenType.SEMICOLON, startOffset);
            case ',' -> addToken(TokenType.COMMA, startOffset);
            case '.' -> lexDotOrEllipsis(startOffset);
            case '?' -> addToken(TokenType.QUESTION, startOffset);
            case ':' -> addToken(TokenType.COLON, startOffset);
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
        captureOutput(beforeTokens, beforeDiagnostics);
        stepCount++;
        return range(startOffset, currentOffset);
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
    public LexerResult toLexerResult() {
        return new LexerResult(tokens, diagnostics);
    }

    private void captureOutput(int beforeTokens, int beforeDiagnostics) {
        if (tokens.size() > beforeTokens) {
            currentToken = tokens.getLast();
        }
        if (diagnostics.size() > beforeDiagnostics) {
            currentDiagnostic = diagnostics.getLast();
        }
    }

    private boolean isAtEnd() {
        return currentOffset >= sourceFile.content().length();
    }

    private void ensureSourceFile() {
        if (sourceFile != null) {
            return;
        }
        if (preprocessor.canNext()) {
            throw new IllegalStateException("preprocessor has not completed");
        }
        if (!preprocessor.succeeded()) {
            throw new IllegalStateException("preprocessor did not succeed");
        }
        sourceFile = preprocessor.preprocessResult().sourceFile();
        rangeSourceFile = preprocessor.sourceFile();
        sourceMap = preprocessor.preprocessResult().sourceMap();
    }

    /**
     * 将扫描文本中的字符区间映射回 IDE 原始源码位置。直接词法分析时是恒等映射；
     * 由 Preprocessor 驱动时使用预编译 source map。
     */
    private SourceRange range(int startOffset, int endOffset) {
        if (sourceMap == null) {
            return sourceFile.range(startOffset, endOffset);
        }
        if (sourceMap.length == 0) {
            return rangeSourceFile.range(0, 0);
        }
        if (startOffset == endOffset) {
            int boundary = mappedBoundary(startOffset);
            return rangeSourceFile.range(boundary, boundary);
        }
        int mappedStart = mappedCharacter(startOffset, true);
        int mappedEnd = mappedBoundary(endOffset);
        if (mappedStart < 0 && mappedEnd < 0) {
            return rangeSourceFile.range(0, 0);
        }
        if (mappedStart < 0) {
            mappedStart = mappedEnd;
        }
        if (mappedEnd < mappedStart) {
            mappedEnd = mappedStart;
        }
        if (mappedEnd == mappedStart && mappedEnd < rangeSourceFile.content().length()) {
            mappedEnd++;
        }
        return rangeSourceFile.range(mappedStart, mappedEnd);
    }

    private int mappedBoundary(int offset) {
        if (offset < sourceMap.length) {
            int mapped = mappedCharacter(offset, true);
            if (mapped >= 0) {
                return mapped;
            }
        }
        int mapped = mappedCharacter(Math.min(offset - 1, sourceMap.length - 1), false);
        return mapped < 0 ? 0 : Math.min(rangeSourceFile.content().length(), mapped + 1);
    }

    private int mappedCharacter(int offset, boolean forwardFirst) {
        int bounded = Math.max(0, Math.min(offset, sourceMap.length - 1));
        if (sourceMap[bounded] >= 0) {
            return sourceMap[bounded];
        }
        if (forwardFirst) {
            for (int index = bounded + 1; index < sourceMap.length; index++) {
                if (sourceMap[index] >= 0) {
                    return sourceMap[index];
                }
            }
        }
        for (int index = bounded - 1; index >= 0; index--) {
            if (sourceMap[index] >= 0) {
                return sourceMap[index];
            }
        }
        if (!forwardFirst) {
            for (int index = bounded + 1; index < sourceMap.length; index++) {
                if (sourceMap[index] >= 0) {
                    return sourceMap[index];
                }
            }
        }
        return -1;
    }

    private char advanceChar() {
        return sourceFile.content().charAt(currentOffset++);
    }

    private boolean match(char expected) {
        if (isAtEnd() || sourceFile.content().charAt(currentOffset) != expected) {
            return false;
        }
        currentOffset++;
        return true;
    }

    private void skipLineComment() {
        while (!isAtEnd() && sourceFile.content().charAt(currentOffset) != '\n') {
            currentOffset++;
        }
    }

    private void lexIdentifier(int startOffset) {
        while (!isAtEnd() && isIdentifierPart(sourceFile.content().charAt(currentOffset))) {
            currentOffset++;
        }

        String lexeme = sourceFile.content().substring(startOffset, currentOffset);
        TokenType kind = switch (lexeme) {
            case "bool" -> TokenType.BOOL;
            case "char" -> TokenType.CHAR;
            case "int" -> TokenType.INT;
            case "long" -> TokenType.LONG;
            case "float" -> TokenType.FLOAT;
            case "double" -> TokenType.DOUBLE;
            case "extern" -> TokenType.EXTERN;
            case "struct" -> TokenType.STRUCT;
            case "return" -> TokenType.RETURN;
            case "if" -> TokenType.IF;
            case "else" -> TokenType.ELSE;
            case "while" -> TokenType.WHILE;
            case "do" -> TokenType.DO;
            case "for" -> TokenType.FOR;
            case "break" -> TokenType.BREAK;
            case "continue" -> TokenType.CONTINUE;
            case "switch" -> TokenType.SWITCH;
            case "case" -> TokenType.CASE;
            case "default" -> TokenType.DEFAULT;
            case "sizeof" -> TokenType.SIZEOF;
            case "true", "false" -> TokenType.BOOL_LITERAL;
            case "NULL" -> TokenType.NULL_LITERAL;
            default -> TokenType.IDENTIFIER;
        };
        Object literalValue = switch (kind) {
            case BOOL_LITERAL -> Boolean.parseBoolean(lexeme);
            default -> null;
        };
        addToken(kind, startOffset, literalValue);
    }

    private void lexIntegerLiteral(int startOffset) {
        while (!isAtEnd() && isAsciiDigit(sourceFile.content().charAt(currentOffset))) {
            currentOffset++;
        }
        boolean floating = false;
        if (!isAtEnd()
                && sourceFile.content().charAt(currentOffset) == '.'
                && hasNextAsciiDigit()) {
            floating = true;
            currentOffset++;
            while (!isAtEnd() && isAsciiDigit(sourceFile.content().charAt(currentOffset))) {
                currentOffset++;
            }
        }
        boolean longLiteral = false;
        boolean floatLiteral = false;
        if (!isAtEnd()) {
            char suffix = sourceFile.content().charAt(currentOffset);
            if (!floating && (suffix == 'l' || suffix == 'L')) {
                longLiteral = true;
                currentOffset++;
            } else if (floating && (suffix == 'f' || suffix == 'F')) {
                floatLiteral = true;
                currentOffset++;
            }
        }

        String lexeme = sourceFile.content().substring(startOffset, currentOffset);
        if (floating) {
            String valueLexeme = floatLiteral ? lexeme.substring(0, lexeme.length() - 1) : lexeme;
            Object literalValue;
            try {
                if (floatLiteral) {
                    literalValue = Float.parseFloat(valueLexeme);
                    if (!Float.isFinite((Float) literalValue)) {
                        addNumericOverflowDiagnostic(startOffset, currentOffset, "浮点字面量超出范围");
                        return;
                    }
                } else {
                    literalValue = Double.parseDouble(valueLexeme);
                    if (!Double.isFinite((Double) literalValue)) {
                        addNumericOverflowDiagnostic(startOffset, currentOffset, "浮点字面量超出范围");
                        return;
                    }
                }
            } catch (NumberFormatException exception) {
                addNumericOverflowDiagnostic(startOffset, currentOffset, "浮点字面量超出范围");
                return;
            }
            tokens.add(new Token(
                    floatLiteral ? TokenType.FLOAT_LITERAL : TokenType.DOUBLE_LITERAL,
                    lexeme,
                    range(startOffset, currentOffset),
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
                addNumericOverflowDiagnostic(startOffset, currentOffset, "long 字面量超出范围");
                return;
            }
            tokens.add(new Token(
                    TokenType.LONG_LITERAL,
                    lexeme,
                    range(startOffset, currentOffset),
                    literalValue
            ));
            return;
        }
        int literalValue;
        try {
            literalValue = Integer.parseInt(lexeme);
        } catch (NumberFormatException exception) {
            addNumericOverflowDiagnostic(startOffset, currentOffset, "整数字面量超出范围");
            return;
        }
        tokens.add(new Token(
                TokenType.INTEGER_LITERAL,
                lexeme,
                range(startOffset, currentOffset),
                literalValue
        ));
    }

    private void lexDotOrEllipsis(int startOffset) {
        if (match('.')) {
            if (match('.')) {
                addToken(TokenType.ELLIPSIS, startOffset);
            } else {
                diagnostics.add(new Diagnostic(
                        "LEX001",
                        Diagnostic.Severity.ERROR,
                        "不完整的省略号：..",
                        range(startOffset, currentOffset)
                ));
            }
            return;
        }
        addToken(TokenType.DOT, startOffset);
    }

    private boolean hasNextAsciiDigit() {
        int nextOffset = currentOffset + 1;
        return nextOffset < sourceFile.content().length()
                && isAsciiDigit(sourceFile.content().charAt(nextOffset));
    }

    private void lexStringLiteral(int startOffset) {
        StringBuilder value = new StringBuilder();
        while (!isAtEnd() && sourceFile.content().charAt(currentOffset) != '"') {
            char character = advanceChar();
            if (character == '\n' || character == '\r') {
                diagnostics.add(new Diagnostic(
                        "LEX002",
                        Diagnostic.Severity.ERROR,
                        "字符串字面量不能跨行",
                        range(startOffset, currentOffset)
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

        currentOffset++;
        tokens.add(new Token(
                TokenType.STRING_LITERAL,
                sourceFile.content().substring(startOffset, currentOffset),
                range(startOffset, currentOffset),
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
                        Diagnostic.Severity.ERROR,
                        "不支持的字符串转义：" + escaped,
                        range(startOffset, currentOffset)
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
                    Diagnostic.Severity.ERROR,
                    "字符字面量不能跨行",
                    range(startOffset, currentOffset)
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
                    Diagnostic.Severity.ERROR,
                    "字符字面量必须只包含一个字符",
                    range(startOffset, currentOffset)
            ));
            while (!isAtEnd()
                    && sourceFile.content().charAt(currentOffset) != '\''
                    && sourceFile.content().charAt(currentOffset) != '\n'
                    && sourceFile.content().charAt(currentOffset) != '\r') {
                currentOffset++;
            }
            match('\'');
            return;
        }
        tokens.add(new Token(
                TokenType.CHAR_LITERAL,
                sourceFile.content().substring(startOffset, currentOffset),
                range(startOffset, currentOffset),
                value
        ));
    }

    private void addUnterminatedStringDiagnostic(int startOffset) {
        diagnostics.add(new Diagnostic(
                "LEX002",
                Diagnostic.Severity.ERROR,
                "字符串字面量缺少结束引号",
                range(startOffset, currentOffset)
        ));
    }

    private void addUnterminatedCharDiagnostic(int startOffset) {
        diagnostics.add(new Diagnostic(
                "LEX004",
                Diagnostic.Severity.ERROR,
                "字符字面量缺少结束引号",
                range(startOffset, currentOffset)
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
                Diagnostic.Severity.ERROR,
                "非法字符：" + sourceFile.content().charAt(startOffset),
                range(startOffset, currentOffset)
        ));
    }

    private void addNumericOverflowDiagnostic(int startOffset, int endOffset, String message) {
        diagnostics.add(new Diagnostic(
                "LEX005",
                Diagnostic.Severity.ERROR,
                message,
                range(startOffset, endOffset)
        ));
    }

    private void addToken(TokenType kind, int startOffset) {
        addToken(kind, startOffset, null);
    }

    private void addToken(TokenType kind, int startOffset, Object literalValue) {
        tokens.add(new Token(
                kind,
                sourceFile.content().substring(startOffset, currentOffset),
                range(startOffset, currentOffset),
                literalValue
        ));
    }

}
