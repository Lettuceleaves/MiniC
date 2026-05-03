package minic.compiler.semantic;

import minic.compiler.lexer.TokenKind;
import minic.compiler.type.MiniType;

final class TypeCompatibility {
    private TypeCompatibility() {
    }

    static boolean isAssignmentCompatible(MiniType targetType, MiniType valueType) {
        if (targetType.equals(valueType)) {
            return true;
        }
        if (targetType.isPointer()) {
            return valueType.isNullPointer();
        }
        if (targetType.isScalar()) {
            return valueType.isScalar();
        }
        return false;
    }

    static boolean isArgumentCompatible(MiniType parameterType, MiniType argumentType) {
        return isAssignmentCompatible(parameterType, argumentType);
    }

    static boolean isConditionCompatible(MiniType type) {
        return type.isScalar() || type.isPointer() || type.isNullPointer();
    }

    static boolean isIndexCompatible(MiniType type) {
        return type.isIntegerScalar();
    }

    static MiniType binaryResultType(MiniType leftType, MiniType rightType, TokenKind operator) {
        if (isComparison(operator)) {
            return MiniType.INT;
        }
        if (isPointerArithmetic(leftType, rightType, operator)) {
            return leftType.isPointer() ? leftType : rightType;
        }
        if (isPointerDifference(leftType, rightType, operator)) {
            return MiniType.LONG;
        }
        if (leftType.isScalar() && rightType.isScalar()) {
            return usualArithmeticType(leftType, rightType);
        }
        return MiniType.INT;
    }

    static boolean isBinaryCompatible(MiniType leftType, MiniType rightType, TokenKind operator) {
        if (isPointerArithmetic(leftType, rightType, operator) || isPointerDifference(leftType, rightType, operator)) {
            return true;
        }
        if (leftType.isScalar() && rightType.isScalar()) {
            return true;
        }
        if (isComparison(operator)
                && ((leftType.isPointer() && (rightType.isPointer() || rightType.isNullPointer()))
                || (rightType.isPointer() && leftType.isNullPointer()))) {
            return true;
        }
        return false;
    }

    private static boolean isPointerArithmetic(MiniType leftType, MiniType rightType, TokenKind operator) {
        if (operator == TokenKind.PLUS) {
            return (leftType.isPointer() && rightType.isIntegerScalar())
                    || (rightType.isPointer() && leftType.isIntegerScalar());
        }
        if (operator == TokenKind.MINUS) {
            return leftType.isPointer() && rightType.isIntegerScalar();
        }
        return false;
    }

    private static boolean isPointerDifference(MiniType leftType, MiniType rightType, TokenKind operator) {
        return operator == TokenKind.MINUS && leftType.isPointer() && rightType.isPointer();
    }

    private static MiniType usualArithmeticType(MiniType leftType, MiniType rightType) {
        if (leftType.equals(MiniType.DOUBLE) || rightType.equals(MiniType.DOUBLE)) {
            return MiniType.DOUBLE;
        }
        if (leftType.equals(MiniType.FLOAT) || rightType.equals(MiniType.FLOAT)) {
            return MiniType.FLOAT;
        }
        if (leftType.equals(MiniType.LONG) || rightType.equals(MiniType.LONG)) {
            return MiniType.LONG;
        }
        return MiniType.INT;
    }

    private static boolean isComparison(TokenKind operator) {
        return operator == TokenKind.EQUAL_EQUAL
                || operator == TokenKind.BANG_EQUAL
                || operator == TokenKind.LESS
                || operator == TokenKind.LESS_EQUAL
                || operator == TokenKind.GREATER
                || operator == TokenKind.GREATER_EQUAL;
    }
}
