package minic.compiler.ir.lowering;

import minic.compiler.ir.instruction.IrBinaryOperator;
import minic.compiler.lexer.TokenKind;

final class IrOperatorLowerer {
    private IrOperatorLowerer() {
    }

    static IrBinaryOperator lower(TokenKind operator) {
        return switch (operator) {
            case PLUS -> IrBinaryOperator.ADD;
            case MINUS -> IrBinaryOperator.SUBTRACT;
            case STAR -> IrBinaryOperator.MULTIPLY;
            case SLASH -> IrBinaryOperator.DIVIDE;
            case EQUAL_EQUAL -> IrBinaryOperator.EQUAL;
            case BANG_EQUAL -> IrBinaryOperator.NOT_EQUAL;
            case LESS -> IrBinaryOperator.LESS_THAN;
            case LESS_EQUAL -> IrBinaryOperator.LESS_EQUAL;
            case GREATER -> IrBinaryOperator.GREATER_THAN;
            case GREATER_EQUAL -> IrBinaryOperator.GREATER_EQUAL;
            default -> throw new IllegalArgumentException("unsupported binary operator: " + operator);
        };
    }
}
