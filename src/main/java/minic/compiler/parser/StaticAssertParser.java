package minic.compiler.parser;

import minic.SourceRange;
import minic.compiler.lexer.token.*;
import minic.compiler.parser.manager.ExpressionManager;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Declaration.StaticAssertDecl;

public final class StaticAssertParser {
    private StaticAssertParser(){}
    public static StaticAssertDecl parse(Parser.Context state,ExpressionManager expressions){
        Token start=state.consume(TokenType.STATIC_ASSERT,"Expected static_assert");
        if(state.consume(TokenType.LEFT_PAREN,"Expected '(' after static_assert")==null)return null;
        Expression condition=expressions.parseAssignmentExpression();StringLiteralExpr message=null;
        if(state.match(TokenType.COMMA)){
            Expression argument=expressions.parseAssignmentExpression();
            if(argument instanceof StringLiteralExpr text&&text.encoding()==LiteralEncoding.ORDINARY)message=text;
            else state.report(argument==null?state.peek().range():argument.range(),"A static assertion message must be an ordinary string literal");
        }
        if(state.consume(TokenType.RIGHT_PAREN,"Expected ')' after assertion")==null)return null;
        Token end=state.consume(TokenType.SEMICOLON,"Expected ';' after static_assert");
        return start==null||condition==null||end==null?null:new StaticAssertDecl(condition,message,SourceRange.span(start.range(),end.range()));
    }
}
