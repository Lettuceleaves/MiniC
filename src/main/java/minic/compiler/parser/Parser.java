package minic.compiler.parser;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.decl.StructDecl;
import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MiniC 递归下降语法分析器入口。
 *
 * <p>该类只负责 parser 生命周期编排。具体声明、语句和表达式解析分别由
 * {@link DeclarationParser}、{@link StatementParser} 和 {@link ExpressionParser}
 * 承担，避免新增语法时继续膨胀入口类。</p>
 */
public final class Parser {
    private final List<Token> tokens;

    /**
     * 创建语法分析器。
     *
     * @param tokens lexer 产出的 token 列表，必须包含 EOF token
     */
    public Parser(List<Token> tokens) {
        this.tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
    }

    /**
     * 解析程序。
     *
     * @return 语法分析结果
     */
    public ParseResult parse() {
        ParserStepState state = new ParserStepState(tokens);
        while (state.canNext()) {
            state.next();
        }
        return state.toParseResult();
    }
}
