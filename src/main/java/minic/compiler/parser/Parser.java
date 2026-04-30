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
    private final ParserState state;
    private final DeclarationParser declarationParser;

    /**
     * 创建语法分析器。
     *
     * @param tokens lexer 产出的 token 列表，必须包含 EOF token
     */
    public Parser(List<Token> tokens) {
        state = new ParserState(List.copyOf(Objects.requireNonNull(tokens, "tokens")));
        ExpressionParser expressionParser = new ExpressionParser(state);
        StatementParser statementParser = new StatementParser(state, expressionParser);
        declarationParser = new DeclarationParser(state, statementParser);
    }

    /**
     * 解析程序。
     *
     * @return 语法分析结果
     */
    public ParseResult parse() {
        ArrayList<StructDecl> structs = new ArrayList<>();
        ArrayList<FunctionDecl> functions = new ArrayList<>();
        while (!state.isAtEnd()) {
            if (state.check(TokenKind.STRUCT)) {
                StructDecl structDecl = declarationParser.parseStructDecl();
                if (structDecl != null) {
                    structs.add(structDecl);
                } else {
                    state.synchronizeFunction();
                }
            } else {
                FunctionDecl functionDecl = declarationParser.parseFunctionDecl();
                if (functionDecl != null) {
                    functions.add(functionDecl);
                } else {
                    state.synchronizeFunction();
                }
            }
        }

        return new ParseResult(new Program(structs, functions, programRange(structs, functions)), state.diagnostics());
    }

    private SourceRange programRange(List<StructDecl> structs, List<FunctionDecl> functions) {
        ArrayList<SourceRange> ranges = new ArrayList<>();
        structs.stream().map(StructDecl::range).forEach(ranges::add);
        functions.stream().map(FunctionDecl::range).forEach(ranges::add);
        if (ranges.isEmpty()) {
            return state.peek().range();
        }
        SourceRange firstRange = ranges.getFirst();
        SourceRange lastRange = ranges.getLast();
        return new SourceRange(firstRange.sourceFile(), firstRange.startOffset(), lastRange.endOffset());
    }
}
