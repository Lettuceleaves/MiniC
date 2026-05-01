package minic.compiler.lexer;

import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MiniC 词法分析器。
 */
public final class Lexer {
    private final SourceFile sourceFile;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private int currentOffset;

    /**
     * 创建词法分析器。
     *
     * @param sourceFile 待分析源码文件
     */
    public Lexer(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
    }

    /**
     * 执行词法分析。
     *
     * @return 词法分析结果
     */
    public LexResult lex() {
        while (!isAtEnd()) {
            int startOffset = currentOffset;
            char character = advance();
            switch (character) {
                case ' ', '\r', '\t', '\n' -> {
                    // 空白由 lexer 跳过，不产出 token。
                }
                case '+' -> addToken(TokenKind.PLUS, startOffset);
                case '-' -> addToken(TokenKind.MINUS, startOffset);
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
        }

        tokens.add(new Token(
                TokenKind.EOF,
                "",
                new SourceRange(sourceFile, currentOffset, currentOffset)
        ));
        return new LexResult(tokens, diagnostics);
    }

    private boolean isAtEnd() {
        return currentOffset >= sourceFile.content().length();
    }

    private char advance() {
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
        TokenKind kind = switch (lexeme) {
            case "int" -> TokenKind.INT;
            case "extern" -> TokenKind.EXTERN;
            case "struct" -> TokenKind.STRUCT;
            case "return" -> TokenKind.RETURN;
            case "if" -> TokenKind.IF;
            case "else" -> TokenKind.ELSE;
            case "while" -> TokenKind.WHILE;
            case "for" -> TokenKind.FOR;
            case "break" -> TokenKind.BREAK;
            case "continue" -> TokenKind.CONTINUE;
            default -> TokenKind.IDENTIFIER;
        };
        addToken(kind, startOffset);
    }

    private void lexIntegerLiteral(int startOffset) {
        while (!isAtEnd() && isAsciiDigit(sourceFile.content().charAt(currentOffset))) {
            currentOffset++;
        }

        String lexeme = sourceFile.content().substring(startOffset, currentOffset);
        tokens.add(new Token(
                TokenKind.INTEGER_LITERAL,
                lexeme,
                new SourceRange(sourceFile, startOffset, currentOffset),
                Integer.parseInt(lexeme)
        ));
    }

    private void lexStringLiteral(int startOffset) {
        StringBuilder value = new StringBuilder();
        while (!isAtEnd() && sourceFile.content().charAt(currentOffset) != '"') {
            char character = advance();
            if (character == '\n' || character == '\r') {
                diagnostics.add(new Diagnostic(
                        "LEX002",
                        DiagnosticSeverity.ERROR,
                        "字符串字面量不能跨行",
                        new SourceRange(sourceFile, startOffset, currentOffset)
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
                TokenKind.STRING_LITERAL,
                sourceFile.content().substring(startOffset, currentOffset),
                new SourceRange(sourceFile, startOffset, currentOffset),
                value.toString()
        ));
    }

    private char lexEscape(int startOffset) {
        char escaped = advance();
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
                        new SourceRange(sourceFile, startOffset, currentOffset)
                ));
                yield escaped;
            }
        };
    }

    private void addUnterminatedStringDiagnostic(int startOffset) {
        diagnostics.add(new Diagnostic(
                "LEX002",
                DiagnosticSeverity.ERROR,
                "字符串字面量缺少结束引号",
                new SourceRange(sourceFile, startOffset, currentOffset)
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
                "非法字符：" + sourceFile.content().charAt(startOffset),
                new SourceRange(sourceFile, startOffset, currentOffset)
        ));
    }

    private void addToken(TokenKind kind, int startOffset) {
        tokens.add(new Token(
                kind,
                sourceFile.content().substring(startOffset, currentOffset),
                new SourceRange(sourceFile, startOffset, currentOffset)
        ));
    }
}
