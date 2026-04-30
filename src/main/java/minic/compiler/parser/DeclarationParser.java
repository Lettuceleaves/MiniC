package minic.compiler.parser;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Parameter;
import minic.compiler.ast.stmt.BlockStmt;
import minic.compiler.lexer.Token;
import minic.compiler.lexer.TokenKind;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.List;

final class DeclarationParser {
    private final ParserState state;
    private final StatementParser statementParser;
    private final TypeParser typeParser;

    DeclarationParser(ParserState state, StatementParser statementParser) {
        this.state = state;
        this.statementParser = statementParser;
        typeParser = new TypeParser(state);
    }

    FunctionDecl parseFunctionDecl() {
        Token startToken = state.peek();
        boolean external = state.match(TokenKind.EXTERN);
        if (external) {
            startToken = state.previous();
        }
        ParsedType returnType = typeParser.parseType("期望函数声明以 int 开始");
        if (returnType == null) {
            return null;
        }

        Token nameToken = state.consume(TokenKind.IDENTIFIER, "期望函数名");
        state.consume(TokenKind.LEFT_PAREN, "期望 '('");
        List<Parameter> parameters = parseParameters();
        state.consume(TokenKind.RIGHT_PAREN, "期望 ')'");
        Token semicolonToken = null;
        BlockStmt body = null;
        if (state.match(TokenKind.SEMICOLON)) {
            semicolonToken = state.previous();
        } else {
            body = statementParser.parseBlock();
        }

        if (nameToken == null || (body == null && semicolonToken == null)) {
            return null;
        }
        int endOffset = body != null ? body.range().endOffset() : semicolonToken.range().endOffset();
        return new FunctionDecl(
                nameToken.lexeme(),
                parameters,
                body,
                external,
                new SourceRange(
                        startToken.range().sourceFile(),
                        startToken.range().startOffset(),
                        endOffset
                )
        );
    }

    private List<Parameter> parseParameters() {
        ArrayList<Parameter> parameters = new ArrayList<>();
        if (state.check(TokenKind.RIGHT_PAREN)) {
            return parameters;
        }

        do {
            ParsedType type = typeParser.parseType("期望参数类型 int");
            Token nameToken = state.consume(TokenKind.IDENTIFIER, "期望参数名");
            if (type != null && nameToken != null) {
                parameters.add(new Parameter(
                        nameToken.lexeme(),
                        type.type(),
                        new SourceRange(
                                type.range().sourceFile(),
                                type.range().startOffset(),
                                nameToken.range().endOffset()
                        )
                ));
            }
        } while (state.match(TokenKind.COMMA));

        return parameters;
    }
}
