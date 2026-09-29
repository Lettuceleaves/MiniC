package minic.compiler.lexer;

import minic.compiler.CompilerApi;
import minic.compiler.Stage;
import minic.compiler.lexer.token.Token;
import minic.compiler.lexer.token.Token.IntegerLiteralKind;
import minic.compiler.lexer.token.Token.IntegerLiteralValue;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.type.MiniType;
import minic.diagnostics.Diagnostic;
import minic.compiler.SourceFile;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.math.BigInteger;
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
        new CompilerApi(List.of(this)).run();
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
                } else if (match('*')) {
                    skipBlockComment(startOffset);
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
            case '.' -> {
                if (!isAtEnd() && isAsciiDigit(sourceFile.content().charAt(currentOffset))) {
                    lexDecimalFloatingLiteral(startOffset);
                } else {
                    lexDotOrEllipsis(startOffset);
                }
            }
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

    /** C block comments are whitespace and do not nest. */
    private void skipBlockComment(int startOffset) {
        while (!isAtEnd()) {
            if (sourceFile.content().charAt(currentOffset) == '*'
                    && currentOffset + 1 < sourceFile.content().length()
                    && sourceFile.content().charAt(currentOffset + 1) == '/') {
                currentOffset += 2;
                return;
            }
            currentOffset++;
        }
        diagnostics.add(new Diagnostic(
                "LEX006",
                Diagnostic.Severity.ERROR,
                "块注释缺少结束符 */",
                range(startOffset, currentOffset)
        ));
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
            case "short" -> TokenType.SHORT;
            case "signed" -> TokenType.SIGNED;
            case "unsigned" -> TokenType.UNSIGNED;
            case "typedef" -> TokenType.TYPEDEF;
            case "const" -> TokenType.CONST;
            case "volatile" -> TokenType.VOLATILE;
            case "restrict" -> TokenType.RESTRICT;
            case "_Alignof", "alignof" -> TokenType.ALIGNOF;
            case "_Alignas", "alignas" -> TokenType.ALIGNAS;
            case "_Noreturn", "noreturn" -> TokenType.NORETURN;
            case "__minic_va_list" -> TokenType.BUILTIN_VA_LIST;
            case "va_start" -> TokenType.VA_START;
            case "va_arg" -> TokenType.VA_ARG;
            case "va_copy" -> TokenType.VA_COPY;
            case "va_end" -> TokenType.VA_END;
            case "float" -> TokenType.FLOAT;
            case "double" -> TokenType.DOUBLE;
            case "void" -> TokenType.VOID;
            case "extern" -> TokenType.EXTERN;
            case "struct" -> TokenType.STRUCT;
            case "union" -> TokenType.UNION;
            case "enum" -> TokenType.ENUM;
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
        int radix = 10;
        int digitsStart = startOffset;
        if (sourceFile.content().charAt(startOffset) == '0' && currentOffset < sourceFile.content().length()) {
            char prefix = sourceFile.content().charAt(currentOffset);
            if (prefix == 'x' || prefix == 'X') {
                radix = 16;
                currentOffset++;
                digitsStart = currentOffset;
                while (!isAtEnd() && isHexDigit(sourceFile.content().charAt(currentOffset))) currentOffset++;
            } else if (prefix == 'b' || prefix == 'B') {
                radix = 2;
                currentOffset++;
                digitsStart = currentOffset;
                while (!isAtEnd() && (sourceFile.content().charAt(currentOffset) == '0'
                        || sourceFile.content().charAt(currentOffset) == '1')) currentOffset++;
            } else {
                radix = 8;
                while (!isAtEnd() && isAsciiDigit(sourceFile.content().charAt(currentOffset))) currentOffset++;
            }
        } else {
            while (!isAtEnd() && isAsciiDigit(sourceFile.content().charAt(currentOffset))) currentOffset++;
        }

        if (radix == 16 && !isAtEnd()
                && (sourceFile.content().charAt(currentOffset) == '.'
                || sourceFile.content().charAt(currentOffset) == 'p'
                || sourceFile.content().charAt(currentOffset) == 'P')) {
            lexHexadecimalFloatingLiteral(startOffset);
            return;
        }

        // Decimal floating constants retain the existing float/double representation.
        if ((radix == 10 || radix == 8 && sourceFile.content().charAt(startOffset) == '0') && !isAtEnd()
                && (sourceFile.content().charAt(currentOffset) == '.'
                || sourceFile.content().charAt(currentOffset) == 'e'
                || sourceFile.content().charAt(currentOffset) == 'E')) {
            lexDecimalFloatingLiteral(startOffset);
            return;
        }

        if (digitsStart == currentOffset) {
            addNumericOverflowDiagnostic(startOffset, currentOffset, "数值字面量缺少数字");
            return;
        }

        int suffixStart = currentOffset;
        while (!isAtEnd()) {
            char suffix = sourceFile.content().charAt(currentOffset);
            if (suffix != 'u' && suffix != 'U' && suffix != 'l' && suffix != 'L') break;
            currentOffset++;
        }
        String suffix = sourceFile.content().substring(suffixStart, currentOffset).toLowerCase(java.util.Locale.ROOT);
        if (!suffix.matches("(?:u(?:l|ll)?|(?:l|ll)u?)?")) {
            addNumericOverflowDiagnostic(startOffset, currentOffset, "无效的整数后缀");
            return;
        }

        String digits = sourceFile.content().substring(digitsStart, suffixStart);
        BigInteger magnitude;
        try {
            magnitude = new BigInteger(digits, radix);
        } catch (NumberFormatException exception) {
            addNumericOverflowDiagnostic(startOffset, suffixStart, "无效的整数数字");
            return;
        }
        boolean unsigned = suffix.contains("u");
        int longCount = suffix.contains("ll") ? 2 : suffix.contains("l") ? 1 : 0;
        MiniType type = selectIntegerLiteralType(magnitude, radix, unsigned, longCount);
        if (type == null) {
            addNumericOverflowDiagnostic(startOffset, currentOffset, "整数字面量超出 unsigned long long 范围");
            return;
        }
        long value = magnitude.longValue();
        TokenType tokenType = TypeLayoutForLexer.sizeOf(type) > 4 ? TokenType.LONG_LITERAL : TokenType.INTEGER_LITERAL;
        Object literalValue = type.equals(MiniType.INT)
                ? magnitude.intValue()
                : new IntegerLiteralValue(value, literalKind(type));
        tokens.add(new Token(tokenType, sourceFile.content().substring(startOffset, currentOffset),
                range(startOffset, currentOffset), literalValue));
    }

    private void lexDecimalFloatingLiteral(int startOffset) {
        if (sourceFile.content().charAt(startOffset) == '.') {
            while (!isAtEnd() && isAsciiDigit(sourceFile.content().charAt(currentOffset))) currentOffset++;
        } else if (!isAtEnd() && sourceFile.content().charAt(currentOffset) == '.') {
            currentOffset++;
            while (!isAtEnd() && isAsciiDigit(sourceFile.content().charAt(currentOffset))) currentOffset++;
        }
        if (!isAtEnd() && (sourceFile.content().charAt(currentOffset) == 'e'
                || sourceFile.content().charAt(currentOffset) == 'E')) {
            currentOffset++;
            if (!isAtEnd() && (sourceFile.content().charAt(currentOffset) == '+'
                    || sourceFile.content().charAt(currentOffset) == '-')) currentOffset++;
            int exponentStart = currentOffset;
            while (!isAtEnd() && isAsciiDigit(sourceFile.content().charAt(currentOffset))) currentOffset++;
            if (exponentStart == currentOffset) {
                addNumericOverflowDiagnostic(startOffset, currentOffset, "浮点指数缺少数字");
                return;
            }
        }
        boolean floatLiteral = false;
        if (!isAtEnd() && (sourceFile.content().charAt(currentOffset) == 'f'
                || sourceFile.content().charAt(currentOffset) == 'F')) {
            floatLiteral = true;
            currentOffset++;
        } else if (!isAtEnd() && (sourceFile.content().charAt(currentOffset) == 'l'
                || sourceFile.content().charAt(currentOffset) == 'L')) {
            // Windows long double has the same representation as double.
            currentOffset++;
        }
        String lexeme = sourceFile.content().substring(startOffset, currentOffset);
        String valueLexeme = lexeme;
        if (!valueLexeme.isEmpty() && "fFlL".indexOf(valueLexeme.charAt(valueLexeme.length() - 1)) >= 0) {
            valueLexeme = valueLexeme.substring(0, valueLexeme.length() - 1);
        }
        try {
            Object value;
            if (floatLiteral) {
                value = Float.valueOf(Float.parseFloat(valueLexeme));
            } else {
                value = Double.valueOf(Double.parseDouble(valueLexeme));
            }
            tokens.add(new Token(floatLiteral ? TokenType.FLOAT_LITERAL : TokenType.DOUBLE_LITERAL,
                    lexeme, range(startOffset, currentOffset), value));
        } catch (NumberFormatException exception) {
            addNumericOverflowDiagnostic(startOffset, currentOffset, "浮点字面量超出范围");
        }
    }

    private void lexHexadecimalFloatingLiteral(int startOffset) {
        if (!isAtEnd() && sourceFile.content().charAt(currentOffset) == '.') {
            currentOffset++;
            while (!isAtEnd() && isHexDigit(sourceFile.content().charAt(currentOffset))) currentOffset++;
        }
        if (isAtEnd() || (sourceFile.content().charAt(currentOffset) != 'p'
                && sourceFile.content().charAt(currentOffset) != 'P')) {
            addNumericOverflowDiagnostic(startOffset, currentOffset, "十六进制浮点字面量缺少 p 指数");
            return;
        }
        currentOffset++;
        if (!isAtEnd() && (sourceFile.content().charAt(currentOffset) == '+'
                || sourceFile.content().charAt(currentOffset) == '-')) currentOffset++;
        int exponentStart = currentOffset;
        while (!isAtEnd() && isAsciiDigit(sourceFile.content().charAt(currentOffset))) currentOffset++;
        if (exponentStart == currentOffset) {
            addNumericOverflowDiagnostic(startOffset, currentOffset, "十六进制浮点指数缺少数字");
            return;
        }
        boolean floatLiteral = false;
        if (!isAtEnd() && (sourceFile.content().charAt(currentOffset) == 'f'
                || sourceFile.content().charAt(currentOffset) == 'F')) {
            floatLiteral = true;
            currentOffset++;
        } else if (!isAtEnd() && (sourceFile.content().charAt(currentOffset) == 'l'
                || sourceFile.content().charAt(currentOffset) == 'L')) {
            currentOffset++;
        }
        String lexeme = sourceFile.content().substring(startOffset, currentOffset);
        String valueText = "fFlL".indexOf(lexeme.charAt(lexeme.length() - 1)) >= 0
                ? lexeme.substring(0, lexeme.length() - 1) : lexeme;
        try {
            Object value;
            if (floatLiteral) value = Float.valueOf(Float.parseFloat(valueText));
            else value = Double.valueOf(Double.parseDouble(valueText));
            tokens.add(new Token(floatLiteral ? TokenType.FLOAT_LITERAL : TokenType.DOUBLE_LITERAL,
                    lexeme, range(startOffset, currentOffset), value));
        } catch (NumberFormatException exception) {
            addNumericOverflowDiagnostic(startOffset, currentOffset, "十六进制浮点字面量超出范围");
        }
    }

    private MiniType selectIntegerLiteralType(BigInteger value, int radix, boolean unsigned, int longCount) {
        java.util.List<MiniType> candidates;
        if (longCount >= 2) {
            candidates = unsigned ? java.util.List.of(MiniType.UNSIGNED_LONG_LONG)
                    : radix == 10 ? java.util.List.of(MiniType.LONG_LONG)
                    : java.util.List.of(MiniType.LONG_LONG, MiniType.UNSIGNED_LONG_LONG);
        } else if (longCount == 1) {
            candidates = unsigned
                    ? java.util.List.of(MiniType.UNSIGNED_LONG, MiniType.UNSIGNED_LONG_LONG)
                    : radix == 10
                    ? java.util.List.of(MiniType.LONG, MiniType.LONG_LONG)
                    : java.util.List.of(MiniType.LONG, MiniType.UNSIGNED_LONG, MiniType.LONG_LONG, MiniType.UNSIGNED_LONG_LONG);
        } else if (unsigned) {
            candidates = java.util.List.of(MiniType.UNSIGNED_INT, MiniType.UNSIGNED_LONG, MiniType.UNSIGNED_LONG_LONG);
        } else if (radix == 10) {
            candidates = java.util.List.of(MiniType.INT, MiniType.LONG, MiniType.LONG_LONG);
        } else {
            candidates = java.util.List.of(MiniType.INT, MiniType.UNSIGNED_INT, MiniType.LONG,
                    MiniType.UNSIGNED_LONG, MiniType.LONG_LONG, MiniType.UNSIGNED_LONG_LONG);
        }
        for (MiniType candidate : candidates) {
            int bits = TypeLayoutForLexer.sizeOf(candidate) * 8;
            boolean signedType = ((MiniType.ScalarType) candidate).kind().signed();
            BigInteger max = signedType ? BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE)
                    : BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
            if (value.compareTo(max) <= 0) return candidate;
        }
        return null;
    }

    private IntegerLiteralKind literalKind(MiniType type) {
        if (type.equals(MiniType.UNSIGNED_INT)) return IntegerLiteralKind.UNSIGNED_INT;
        if (type.equals(MiniType.UNSIGNED_LONG)) return IntegerLiteralKind.UNSIGNED_LONG;
        if (type.equals(MiniType.LONG_LONG)) return IntegerLiteralKind.LONG_LONG;
        if (type.equals(MiniType.UNSIGNED_LONG_LONG)) return IntegerLiteralKind.UNSIGNED_LONG_LONG;
        return IntegerLiteralKind.LONG;
    }

    private boolean isHexDigit(char character) {
        return isAsciiDigit(character)
                || character >= 'a' && character <= 'f'
                || character >= 'A' && character <= 'F';
    }

    /** Avoids coupling the lexer to contextual aggregate layout. */
    private static final class TypeLayoutForLexer {
        private static int sizeOf(MiniType type) {
            return ((MiniType.ScalarType) type).kind().sizeBytes();
        }
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
