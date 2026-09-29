package minic.compiler.semantic.manager;

import minic.compiler.lexer.token.TokenType;
import minic.compiler.type.MiniType;

final class TypeCompatibility {
    private TypeCompatibility() {
    }

    static boolean isAssignmentCompatible(MiniType targetType, MiniType valueType) {
        valueType = decay(valueType);
        if (targetType.equals(valueType)) {
            return true;
        }
        if (targetType.isPointer()) {
            if (valueType.isNullPointer()) {
                return true;
            }
            if (!valueType.isPointer()) {
                return false;
            }
            // C 对象指针可以隐式转换为/从 void*；函数指针不参与该规则。
            MiniType targetPointee = targetType.pointee();
            MiniType valuePointee = valueType.pointee();
            return targetPointee.equals(valuePointee)
                    || targetPointee.isVoid() && !valuePointee.isFunction()
                    || valuePointee.isVoid() && !targetPointee.isFunction();
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
        type = decay(type);
        return type.isScalar() || type.isPointer() || type.isNullPointer();
    }

    static boolean isIndexCompatible(MiniType type) {
        return type.isIntegerScalar();
    }

    static MiniType binaryResultType(MiniType leftType, MiniType rightType, TokenType operator) {
        leftType = decay(leftType);
        rightType = decay(rightType);
        if (isLogical(operator)) {
            return MiniType.INT;
        }
        if (isComparison(operator)) {
            return MiniType.INT;
        }
        if (isBitwise(operator) || isShift(operator)) {
            return usualArithmeticType(leftType, rightType);
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

    static boolean isBinaryCompatible(MiniType leftType, MiniType rightType, TokenType operator) {
        leftType = decay(leftType);
        rightType = decay(rightType);
        if (isLogical(operator)) {
            return isConditionCompatible(leftType) && isConditionCompatible(rightType);
        }
        if (isBitwise(operator) || isShift(operator)) {
            return leftType.isIntegerScalar() && rightType.isIntegerScalar();
        }
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

    static boolean isConditionalBranchCompatible(MiniType thenType, MiniType elseType) {
        thenType = decay(thenType);
        elseType = decay(elseType);
        return isAssignmentCompatible(thenType, elseType) || isAssignmentCompatible(elseType, thenType);
    }

    static MiniType conditionalResultType(MiniType thenType, MiniType elseType) {
        thenType = decay(thenType);
        elseType = decay(elseType);
        if (thenType.equals(elseType) || isAssignmentCompatible(thenType, elseType)) {
            return thenType;
        }
        if (isAssignmentCompatible(elseType, thenType)) {
            return elseType;
        }
        return MiniType.INT;
    }

    private static boolean isPointerArithmetic(MiniType leftType, MiniType rightType, TokenType operator) {
        if (operator == TokenType.PLUS) {
            return (isObjectPointer(leftType) && rightType.isIntegerScalar())
                    || (isObjectPointer(rightType) && leftType.isIntegerScalar());
        }
        if (operator == TokenType.MINUS) {
            return isObjectPointer(leftType) && rightType.isIntegerScalar();
        }
        return false;
    }

    private static boolean isPointerDifference(MiniType leftType, MiniType rightType, TokenType operator) {
        return operator == TokenType.MINUS
                && isObjectPointer(leftType)
                && leftType.equals(rightType);
    }

    private static boolean isObjectPointer(MiniType type) {
        return type.isPointer() && !type.pointee().isFunction();
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

    /** C 数组仅在值上下文中退化一层；声明、sizeof 和取址仍保留完整数组节点。 */
    static MiniType decay(MiniType type) {
        return type instanceof MiniType.ArrayType arrayType
                ? arrayType.elementType().pointerTo()
                : type;
    }

    private static boolean isComparison(TokenType operator) {
        return operator == TokenType.EQUAL_EQUAL
                || operator == TokenType.BANG_EQUAL
                || operator == TokenType.LESS
                || operator == TokenType.LESS_EQUAL
                || operator == TokenType.GREATER
                || operator == TokenType.GREATER_EQUAL;
    }

    private static boolean isLogical(TokenType operator) {
        return operator == TokenType.AMPERSAND_AMPERSAND || operator == TokenType.PIPE_PIPE;
    }

    private static boolean isBitwise(TokenType operator) {
        return operator == TokenType.AMPERSAND
                || operator == TokenType.PIPE
                || operator == TokenType.CARET;
    }

    private static boolean isShift(TokenType operator) {
        return operator == TokenType.LESS_LESS || operator == TokenType.GREATER_GREATER;
    }
}
