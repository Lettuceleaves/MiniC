package minic.compiler.parser;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.ast.expr.AssignmentExpr;
import minic.compiler.ast.expr.BinaryExpr;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.ast.stmt.ExprStmt;
import minic.compiler.ast.expr.Expression;
import minic.compiler.ast.expr.GroupingExpr;
import minic.compiler.ast.expr.IntegerLiteralExpr;
import minic.compiler.ast.expr.NameExpr;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.stmt.ReturnStmt;
import minic.compiler.ast.stmt.Statement;
import minic.compiler.ast.stmt.VarDeclStmt;
import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MiniC 递归下降语法分析器。
 *
 * <p>当前阶段覆盖函数声明和基础语句级别的语法：
 * {@code program ::= functionDecl* EOF}，
 * {@code functionDecl ::= "int" identifier "(" parameterList? ")" (block | ";")}。</p>
 *
 * <p>解析器不直接读取源码文本，而是消费 lexer 产出的 token 列表。它维护一个
 * 指向“当前待消费 token”的游标 {@code currentIndex}；所有解析方法都通过
 * {@link #peek()} 查看当前 token，通过 {@link #advance()} 消费 token。</p>
 */
public final class Parser {
    /**
     * lexer 产出的 token 快照。
     *
     * <p>构造函数会防御性复制，避免调用方在解析过程中修改 token 流。</p>
     */
    private final List<Token> tokens;

    /**
     * 解析过程中累计产生的语法诊断。
     *
     * <p>诊断会尽量关联到当前 token 的 source range，方便后续 UI 高亮错误位置。</p>
     */
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    /**
     * 当前待消费 token 的下标。
     *
     * <p>例如初始值 0 表示还未消费任何 token；调用 {@link #advance()} 后，
     * 游标右移一位，并返回刚刚消费的 token。</p>
     */
    private int currentIndex;

    /**
     * 创建语法分析器。
     *
     * @param tokens lexer 产出的 token 列表，必须包含 EOF token
     */
    public Parser(List<Token> tokens) {
        this.tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
        if (this.tokens.isEmpty()) {
            throw new IllegalArgumentException("tokens must contain EOF");
        }
    }

    /**
     * 解析程序。
     *
     * <p>程序由 0 个或多个函数声明组成。每次尝试解析一个函数声明：
     * 如果成功，就加入 AST；如果失败，就执行错误恢复，跳到下一个可能的函数边界。</p>
     *
     * @return 语法分析结果
     */
    public ParseResult parse() {
        ArrayList<FunctionDecl> functions = new ArrayList<>();
        while (!isAtEnd()) {
            FunctionDecl functionDecl = parseFunctionDecl();
            if (functionDecl != null) {
                functions.add(functionDecl);
            } else {
                synchronize();
            }
        }

        return new ParseResult(new Program(functions, programRange(functions)), diagnostics);
    }

    /**
     * 解析一个函数声明。
     *
     * <p>A033 阶段会解析函数体 block、基础语句和表达式 AST。</p>
     *
     * <p>方法返回 {@code null} 表示当前函数声明无法可靠构造 AST。调用方会据此触发
     * {@link #synchronize()}，避免继续在错误位置解释 token。</p>
     *
     * @return 函数声明节点；语法错误严重到无法构造时返回 {@code null}
     */
    private FunctionDecl parseFunctionDecl() {
        // startToken 用于在成功解析后构造整个函数声明的 source range。
        Token startToken = peek();
        if (!match(TokenKind.INT)) {
            report(peek(), "期望函数声明以 int 开始");
            return null;
        }

        Token nameToken = consume(TokenKind.IDENTIFIER, "期望函数名");
        consume(TokenKind.LEFT_PAREN, "期望 '('");
        List<Parameter> parameters = parseParameters();
        consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        Token semicolonToken = null;
        BlockStmt body = null;
        if (match(TokenKind.SEMICOLON)) {
            semicolonToken = previous();
        } else {
            body = parseBlock();
        }

        // 函数名和结束位置是构造 FunctionDecl 的最低必要信息。
        if (nameToken == null || (body == null && semicolonToken == null)) {
            return null;
        }
        int endOffset = body != null ? body.range().endOffset() : semicolonToken.range().endOffset();
        return new FunctionDecl(
                nameToken.lexeme(),
                parameters,
                body,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endOffset
                )
        );
    }

    /**
     * 解析 block。
     *
     * <p>block 由左右花括号包围，内部可以包含 0 个或多个语句。</p>
     *
     * @return block 语句节点；缺少必要边界时返回 {@code null}
     */
    private BlockStmt parseBlock() {
        Token startToken = consume(TokenKind.LEFT_BRACE, "期望 '{'");
        if (startToken == null) {
            return null;
        }

        ArrayList<Statement> statements = new ArrayList<>();
        while (!check(TokenKind.RIGHT_BRACE) && !isAtEnd()) {
            Statement statement = parseStatement();
            if (statement != null) {
                statements.add(statement);
            } else {
                synchronizeStatement();
            }
        }

        Token endToken = consume(TokenKind.RIGHT_BRACE, "期望 '}'");
        if (endToken == null) {
            return null;
        }
        return new BlockStmt(
                statements,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endToken.range().endOffset()
                )
        );
    }

    /**
     * 解析一条语句。
     *
     * <p>当前支持 block、局部变量声明、return 和表达式语句。</p>
     *
     * @return 语句节点；无法解析时返回 {@code null}
     */
    private Statement parseStatement() {
        if (check(TokenKind.LEFT_BRACE)) {
            return parseBlock();
        }
        if (check(TokenKind.INT)) {
            return parseVarDeclStmt();
        }
        if (check(TokenKind.RETURN)) {
            return parseReturnStmt();
        }
        return parseExprStmt();
    }

    /**
     * 解析局部变量声明语句。
     *
     * @return 局部变量声明语句节点；无法可靠构造时返回 {@code null}
     */
    private VarDeclStmt parseVarDeclStmt() {
        Token startToken = consume(TokenKind.INT, "期望变量类型 int");
        Token nameToken = consume(TokenKind.IDENTIFIER, "期望变量名");
        Expression initializer = null;
        if (match(TokenKind.EQUAL)) {
            initializer = parseExpression();
        }
        Token semicolonToken = consume(TokenKind.SEMICOLON, "期望 ';'");

        if (startToken == null || nameToken == null || semicolonToken == null) {
            return null;
        }
        return new VarDeclStmt(
                nameToken.lexeme(),
                initializer,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        semicolonToken.range().endOffset()
                )
        );
    }

    /**
     * 解析 return 语句。
     *
     * @return return 语句节点；无法可靠构造时返回 {@code null}
     */
    private ReturnStmt parseReturnStmt() {
        Token startToken = consume(TokenKind.RETURN, "期望 return");
        Expression expression = null;
        if (!check(TokenKind.SEMICOLON)) {
            expression = parseExpression();
        }
        Token semicolonToken = consume(TokenKind.SEMICOLON, "期望 ';'");

        if (startToken == null || semicolonToken == null) {
            return null;
        }
        return new ReturnStmt(
                expression,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        semicolonToken.range().endOffset()
                )
        );
    }

    /**
     * 解析表达式语句。
     *
     * @return 表达式语句节点；无法可靠构造时返回 {@code null}
     */
    private ExprStmt parseExprStmt() {
        Token startToken = peek();
        Expression expression = parseExpression();
        Token semicolonToken = consume(TokenKind.SEMICOLON, "期望 ';'");
        if (expression == null || semicolonToken == null) {
            return null;
        }
        return new ExprStmt(
                expression,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        semicolonToken.range().endOffset()
                )
        );
    }

    /**
     * 解析表达式。
     *
     * @return 表达式 AST；无法解析时返回 {@code null}
     */
    private Expression parseExpression() {
        return parseAssignment();
    }

    /**
     * 解析赋值表达式。
     *
     * <p>赋值是右结合的，因此右侧递归调用 {@link #parseAssignment()}。</p>
     *
     * @return 表达式 AST；无法解析时返回 {@code null}
     */
    private Expression parseAssignment() {
        Expression expression = parseEquality();
        if (!match(TokenKind.EQUAL)) {
            return expression;
        }

        Token equalsToken = previous();
        Expression value = parseAssignment();
        if (expression instanceof NameExpr nameExpr && value != null) {
            return new AssignmentExpr(
                    nameExpr.name(),
                    value,
                    new SourceRange(
                            nameExpr.range().sourceFile(),
                            nameExpr.range().startOffset(),
                            value.range().endOffset()
                    )
            );
        }

        report(equalsToken, "赋值左侧必须是标识符");
        return value;
    }

    /**
     * 解析相等比较表达式。
     *
     * @return 表达式 AST；无法解析时返回 {@code null}
     */
    private Expression parseEquality() {
        Expression expression = parseRelational();
        while (match(TokenKind.EQUAL_EQUAL) || match(TokenKind.BANG_EQUAL)) {
            Token operator = previous();
            Expression right = parseRelational();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    /**
     * 解析大小比较表达式。
     *
     * @return 表达式 AST；无法解析时返回 {@code null}
     */
    private Expression parseRelational() {
        Expression expression = parseAdditive();
        while (match(TokenKind.LESS)
                || match(TokenKind.LESS_EQUAL)
                || match(TokenKind.GREATER)
                || match(TokenKind.GREATER_EQUAL)) {
            Token operator = previous();
            Expression right = parseAdditive();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    /**
     * 解析加减表达式。
     *
     * @return 表达式 AST；无法解析时返回 {@code null}
     */
    private Expression parseAdditive() {
        Expression expression = parseMultiplicative();
        while (match(TokenKind.PLUS) || match(TokenKind.MINUS)) {
            Token operator = previous();
            Expression right = parseMultiplicative();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    /**
     * 解析乘除表达式。
     *
     * @return 表达式 AST；无法解析时返回 {@code null}
     */
    private Expression parseMultiplicative() {
        Expression expression = parsePrimary();
        while (match(TokenKind.STAR) || match(TokenKind.SLASH)) {
            Token operator = previous();
            Expression right = parsePrimary();
            expression = combineBinary(expression, operator, right);
        }
        return expression;
    }

    /**
     * 解析 primary 表达式。
     *
     * @return 表达式 AST；无法解析时返回 {@code null}
     */
    private Expression parsePrimary() {
        if (match(TokenKind.INTEGER_LITERAL)) {
            Token integerToken = previous();
            return new IntegerLiteralExpr((Integer) integerToken.literalValue(), integerToken.lexeme(), integerToken.range());
        }
        if (match(TokenKind.IDENTIFIER)) {
            Token nameToken = previous();
            if (match(TokenKind.LEFT_PAREN)) {
                return finishCall(nameToken);
            }
            return new NameExpr(nameToken.lexeme(), nameToken.range());
        }
        if (match(TokenKind.LEFT_PAREN)) {
            Token startToken = previous();
            Expression expression = parseExpression();
            Token endToken = consume(TokenKind.RIGHT_PAREN, "期望 ')'");
            if (expression == null || endToken == null) {
                return expression;
            }
            return new GroupingExpr(
                    expression,
                    new SourceRange(
                            startToken.range().sourceFile(),
                            startToken.range().startOffset(),
                            endToken.range().endOffset()
                    )
            );
        }

        report(peek(), "期望表达式");
        if (!isAtEnd()) {
            advance();
        }
        return null;
    }

    /**
     * 完成函数调用表达式解析。
     *
     * @param calleeToken 已消费的函数名 token
     * @return 函数调用表达式；无法可靠构造时返回 {@code null}
     */
    private Expression finishCall(Token calleeToken) {
        ArrayList<Expression> arguments = new ArrayList<>();
        if (!check(TokenKind.RIGHT_PAREN)) {
            do {
                Expression argument = parseExpression();
                if (argument != null) {
                    arguments.add(argument);
                }
            } while (match(TokenKind.COMMA));
        }

        Token endToken = consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        if (endToken == null) {
            return null;
        }
        return new CallExpr(
                calleeToken.lexeme(),
                arguments,
                new SourceRange(
                        calleeToken.range().sourceFile(),
                        calleeToken.range().startOffset(),
                        endToken.range().endOffset()
                )
        );
    }

    /**
     * 组合二元表达式。
     *
     * @param left 左表达式
     * @param operator 运算符 token
     * @param right 右表达式
     * @return 二元表达式；任一操作数缺失时返回可保留的操作数
     */
    private Expression combineBinary(Expression left, Token operator, Expression right) {
        if (left == null || right == null) {
            return left != null ? left : right;
        }
        return new BinaryExpr(
                left,
                operator.kind(),
                right,
                new SourceRange(
                        left.range().sourceFile(),
                        left.range().startOffset(),
                        right.range().endOffset()
                )
        );
    }

    /**
     * 解析函数形参列表。
     *
     * <p>调用方已经消费了左括号 {@code (}。如果当前 token 是右括号 {@code )}，
     * 说明参数列表为空；否则按 {@code int name (, int name)*} 解析。</p>
     *
     * @return 形参节点列表
     */
    private List<Parameter> parseParameters() {
        ArrayList<Parameter> parameters = new ArrayList<>();
        if (check(TokenKind.RIGHT_PAREN)) {
            return parameters;
        }

        do {
            // v0.1 中所有参数类型都是 int，因此 AST 目前只保存参数名和 source range。
            Token startToken = consume(TokenKind.INT, "期望参数类型 int");
            Token nameToken = consume(TokenKind.IDENTIFIER, "期望参数名");
            if (startToken != null && nameToken != null) {
                parameters.add(new Parameter(
                        nameToken.lexeme(),
                        new SourceRange(
                                startToken.range().sourceFile(),
                                startToken.range().startOffset(),
                                nameToken.range().endOffset()
                        )
                ));
            }
        } while (match(TokenKind.COMMA));

        return parameters;
    }

    /**
     * 计算 Program 节点的源码范围。
     *
     * <p>空程序没有函数声明，此时使用 EOF token 的空 range。非空程序从第一个函数
     * 起点覆盖到最后一个函数终点。</p>
     *
     * @param functions 已成功解析出的函数列表
     * @return 程序源码范围
     */
    private SourceRange programRange(List<FunctionDecl> functions) {
        if (functions.isEmpty()) {
            return peek().range();
        }
        SourceRange firstRange = functions.getFirst().range();
        SourceRange lastRange = functions.getLast().range();
        return new SourceRange(firstRange.sourceFile(), firstRange.startOffset(), lastRange.endOffset());
    }

    /**
     * 如果当前 token 是指定类型，则消费它。
     *
     * <p>{@code match} 用于“可选 token”或分支判断：不匹配时不会报错，也不会移动游标。</p>
     *
     * @param kind 期望的 token 类型
     * @return 是否匹配并消费成功
     */
    private boolean match(TokenKind kind) {
        if (!check(kind)) {
            return false;
        }
        advance();
        return true;
    }

    /**
     * 消费一个必需 token。
     *
     * <p>{@code consume} 用于语法中必须出现的 token：匹配时返回被消费的 token；
     * 不匹配时记录诊断并返回 {@code null}，由上层决定是否还能继续构造 AST。</p>
     *
     * @param kind 期望的 token 类型
     * @param message 不匹配时的诊断消息
     * @return 被消费的 token；不匹配时返回 {@code null}
     */
    private Token consume(TokenKind kind, String message) {
        if (check(kind)) {
            return advance();
        }
        report(peek(), message);
        return null;
    }

    /**
     * 判断当前 token 是否为指定类型，不移动游标。
     *
     * @param kind 期望的 token 类型
     * @return 当前 token 是否匹配
     */
    private boolean check(TokenKind kind) {
        return peek().kind() == kind;
    }

    /**
     * 消费当前 token 并返回它。
     *
     * <p>EOF token 是哨兵，不会被越过；当当前 token 是 EOF 时，返回 EOF 前的 previous
     * 语义不应被调用方依赖。当前代码只在非 EOF 的匹配路径调用该方法。</p>
     *
     * @return 刚刚消费的 token
     */
    private Token advance() {
        if (!isAtEnd()) {
            currentIndex++;
        }
        return previous();
    }

    /**
     * 判断是否到达 EOF token。
     *
     * @return 当前 token 是否是 EOF
     */
    private boolean isAtEnd() {
        return peek().kind() == TokenKind.EOF;
    }

    /**
     * 查看当前待消费 token，不移动游标。
     *
     * @return 当前 token
     */
    private Token peek() {
        return tokens.get(currentIndex);
    }

    /**
     * 返回最近一次被消费的 token。
     *
     * @return 上一个 token
     */
    private Token previous() {
        return tokens.get(currentIndex - 1);
    }

    /**
     * 记录语法诊断。
     *
     * <p>当前 parser 阶段统一使用 {@code PAR001}。未来如果需要更细粒度错误码，
     * 可以在这里按错误类型拆分。</p>
     *
     * @param token 触发错误的 token
     * @param message 用户可读错误信息
     */
    private void report(Token token, String message) {
        diagnostics.add(new Diagnostic("PAR001", DiagnosticSeverity.ERROR, message, token.range()));
    }

    /**
     * 错误恢复。
     *
     * <p>当一个函数声明解析失败时，解析器不能停在原地，否则外层循环会重复处理同一个
     * token。这里先消费一个 token，然后继续前进，直到遇到右花括号或 EOF。这个策略很粗糙，
     * 但足以让 A031 在函数级别错误后继续处理后续输入。后续 parser 能识别更多语法边界时，
     * 可以把同步点扩展为 {@code int}、分号、右花括号等。</p>
     */
    private void synchronize() {
        if (isAtEnd()) {
            return;
        }
        advance();
        while (!isAtEnd() && previous().kind() != TokenKind.RIGHT_BRACE) {
            advance();
        }
    }

    /**
     * 语句级错误恢复。
     *
     * <p>语句解析失败时跳到分号或右花括号，降低后续语句被连带误报的概率。</p>
     */
    private void synchronizeStatement() {
        while (!isAtEnd() && !check(TokenKind.SEMICOLON) && !check(TokenKind.RIGHT_BRACE)) {
            advance();
        }
        match(TokenKind.SEMICOLON);
    }
}
