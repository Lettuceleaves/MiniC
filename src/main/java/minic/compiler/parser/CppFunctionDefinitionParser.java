package minic.compiler.parser;

import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.node.Declaration.DefinitionKind;

/** Consumes a source definition marker, preserving the declaration's candidate identity. */
public final class CppFunctionDefinitionParser {
    private CppFunctionDefinitionParser() {}
    public static DefinitionKind parse(Parser.Context state) {
        if(!state.match(TokenType.EQUAL))return DefinitionKind.ORDINARY;
        DefinitionKind kind;
        if(state.match(TokenType.DEFAULT))kind=DefinitionKind.DEFAULTED;
        else if(state.match(TokenType.DELETE))kind=DefinitionKind.DELETED;
        else {state.report(state.peek(),"Expected 'default' or 'delete' after '=' in a function definition");kind=DefinitionKind.ORDINARY;}
        state.consume(TokenType.SEMICOLON,"Expected ';' after the function definition marker");
        return kind;
    }
}
