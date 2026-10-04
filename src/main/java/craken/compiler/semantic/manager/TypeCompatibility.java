package craken.compiler.semantic.manager;

import craken.compiler.lexer.token.TokenType;
import craken.compiler.parser.node.Expression;
import craken.compiler.type.CrakenType;

public final class TypeCompatibility {
    private TypeCompatibility() {
    }

    /** Contextual conversion does not change the integer literal's own expression type. */
    static CrakenType pointerContextType(CrakenType targetType, CrakenType valueType, Expression value) {
        return decay(targetType).isPointer() && isNullPointerLiteral(value) ? CrakenType.NULL : valueType;
    }

    private static boolean isNullPointerLiteral(Expression expression) {
        return switch (expression) {
            case Expression.GroupingExpr group -> isNullPointerLiteral(group.expression());
            case Expression.IntegerLiteralExpr value -> value.value() == 0;
            case Expression.LongLiteralExpr value -> value.value() == 0;
            // Enum references also use IntegerConstantExpr; their identifier spelling is
            // not an integer literal, even when the enumerator happens to equal zero.
            case Expression.IntegerConstantExpr value -> value.value() == 0
                    && value.type().isIntegerScalar() && !value.lexeme().isEmpty()
                    && Character.isDigit(value.lexeme().charAt(0));
            default -> false;
        };
    }

    static boolean isAssignmentCompatible(CrakenType targetType, CrakenType valueType, Expression value) {
        return isAssignmentCompatible(targetType, pointerContextType(targetType, valueType, value));
    }

    static boolean isAssignmentCompatible(CrakenType targetType, CrakenType valueType) {
        valueType = decay(valueType);
        if (targetType.unqualified().equals(valueType.unqualified())) {
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
            CrakenType targetPointee = targetType.pointee();
            CrakenType valuePointee = valueType.pointee();
            if (!targetPointee.qualifiers().containsAll(valuePointee.qualifiers())) {
                return false;
            }
            return targetPointee.unqualified().equals(valuePointee.unqualified())
                    || targetPointee.isVoid() && !valuePointee.isFunction()
                    || valuePointee.isVoid() && !targetPointee.isFunction();
        }
        if (targetType.isScalar()) {
            return valueType.isScalar();
        }
        return false;
    }

    static boolean isArgumentCompatible(CrakenType parameterType, CrakenType argumentType) {
        return isAssignmentCompatible(parameterType, argumentType);
    }

    static boolean isArgumentCompatible(CrakenType parameterType, CrakenType argumentType, Expression argument) {
        return isAssignmentCompatible(parameterType, argumentType, argument);
    }

    static boolean isConditionCompatible(CrakenType type) {
        type = decay(type);
        return type.isScalar() || type.isPointer() || type.isNullPointer();
    }

    static boolean isIndexCompatible(CrakenType type) {
        return type.isIntegerScalar();
    }

    public static CrakenType binaryResultType(CrakenType leftType, CrakenType rightType, TokenType operator) {
        leftType = decay(leftType);
        rightType = decay(rightType);
        if (isLogical(operator)) {
            return CrakenType.INT;
        }
        if (isComparison(operator)) {
            return CrakenType.INT;
        }
        if (isShift(operator)) {
            return integerPromotion(leftType);
        }
        if (isBitwise(operator)) {
            return usualArithmeticType(leftType, rightType);
        }
        if (isPointerArithmetic(leftType, rightType, operator)) {
            return leftType.isPointer() ? leftType : rightType;
        }
        if (isPointerDifference(leftType, rightType, operator)) {
            return CrakenType.LONG_LONG;
        }
        if (leftType.isScalar() && rightType.isScalar()) {
            return usualArithmeticType(leftType, rightType);
        }
        return CrakenType.INT;
    }

    static boolean isBinaryCompatible(CrakenType leftType, CrakenType rightType, TokenType operator) {
        leftType = decay(leftType);
        rightType = decay(rightType);
        if (isLogical(operator)) {
            return isConditionCompatible(leftType) && isConditionCompatible(rightType);
        }
        if (operator == TokenType.PERCENT || isBitwise(operator) || isShift(operator)) {
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

    static boolean isConditionalBranchCompatible(CrakenType thenType, CrakenType elseType) {
        thenType = decay(thenType);
        elseType = decay(elseType);
        return isAssignmentCompatible(thenType, elseType) || isAssignmentCompatible(elseType, thenType);
    }

    public static CrakenType conditionalResultType(CrakenType thenType, CrakenType elseType) {
        thenType = decay(thenType);
        elseType = decay(elseType);
        if (thenType.isScalar() && elseType.isScalar()) {
            return usualArithmeticType(thenType, elseType);
        }
        if (thenType.equals(elseType) || isAssignmentCompatible(thenType, elseType)) {
            return thenType;
        }
        if (isAssignmentCompatible(elseType, thenType)) {
            return elseType;
        }
        return CrakenType.INT;
    }

    private static boolean isPointerArithmetic(CrakenType leftType, CrakenType rightType, TokenType operator) {
        if (operator == TokenType.PLUS) {
            return (isObjectPointer(leftType) && rightType.isIntegerScalar())
                    || (isObjectPointer(rightType) && leftType.isIntegerScalar());
        }
        if (operator == TokenType.MINUS) {
            return isObjectPointer(leftType) && rightType.isIntegerScalar();
        }
        return false;
    }

    private static boolean isPointerDifference(CrakenType leftType, CrakenType rightType, TokenType operator) {
        return operator == TokenType.MINUS
                && isObjectPointer(leftType)
                && isObjectPointer(rightType)
                && leftType.pointee().unqualified().equals(rightType.pointee().unqualified());
    }

    private static boolean isObjectPointer(CrakenType type) {
        return type.isPointer() && !type.pointee().isFunction();
    }

    static CrakenType usualArithmeticType(CrakenType leftType, CrakenType rightType) {
        leftType = leftType.unqualified();
        rightType = rightType.unqualified();
        if (leftType.equals(CrakenType.LONG_DOUBLE) || rightType.equals(CrakenType.LONG_DOUBLE)) return CrakenType.LONG_DOUBLE;
        if (leftType.equals(CrakenType.DOUBLE) || rightType.equals(CrakenType.DOUBLE)) {
            return CrakenType.DOUBLE;
        }
        if (leftType.equals(CrakenType.FLOAT) || rightType.equals(CrakenType.FLOAT)) {
            return CrakenType.FLOAT;
        }
        leftType = integerPromotion(leftType);
        rightType = integerPromotion(rightType);
        if (leftType.equals(rightType)) {
            return leftType;
        }
        CrakenType.ScalarKind leftKind = scalarKind(leftType);
        CrakenType.ScalarKind rightKind = scalarKind(rightType);
        if (leftKind.signed() == rightKind.signed()) {
            return leftKind.integerRank() >= rightKind.integerRank() ? leftType : rightType;
        }

        CrakenType unsignedType = leftKind.signed() ? rightType : leftType;
        CrakenType signedType = leftKind.signed() ? leftType : rightType;
        CrakenType.ScalarKind unsignedKind = scalarKind(unsignedType);
        CrakenType.ScalarKind signedKind = scalarKind(signedType);
        if (unsignedKind.integerRank() >= signedKind.integerRank()) {
            return unsignedType;
        }
        if (signedKind.sizeBytes() > unsignedKind.sizeBytes()) {
            return signedType;
        }
        return unsignedCounterpart(signedType);
    }

    public static CrakenType integerPromotion(CrakenType type) {
        type = type.unqualified();
        if (!type.isIntegerScalar()) {
            return type;
        }
        CrakenType.ScalarKind kind = scalarKind(type);
        return kind.integerRank() < CrakenType.ScalarKind.INT.integerRank() ? CrakenType.INT : type;
    }

    private static CrakenType.ScalarKind scalarKind(CrakenType type) {
        type = type.unqualified();
        if (type instanceof CrakenType.ScalarType scalarType && scalarType.kind().integer()) {
            return scalarType.kind();
        }
        throw new IllegalArgumentException("not an integer scalar type: " + type);
    }

    private static CrakenType unsignedCounterpart(CrakenType type) {
        type = type.unqualified();
        if (type.equals(CrakenType.INT)) return CrakenType.UNSIGNED_INT;
        if (type.equals(CrakenType.LONG)) return CrakenType.UNSIGNED_LONG;
        if (type.equals(CrakenType.LONG_LONG)) return CrakenType.UNSIGNED_LONG_LONG;
        if (type.equals(CrakenType.SHORT)) return CrakenType.UNSIGNED_SHORT;
        if (type.equals(CrakenType.CHAR) || type.equals(CrakenType.SIGNED_CHAR)) return CrakenType.UNSIGNED_CHAR;
        return type;
    }

    /** Arrays and function designators decay only in value contexts; queries and address-of keep their types. */
    public static CrakenType decay(CrakenType type) {
        CrakenType unqualified = type.unqualified();
        return unqualified instanceof CrakenType.ArrayType arrayType
                ? qualifiedElementType(type, arrayType.elementType()).pointerTo()
                : unqualified instanceof CrakenType.FunctionType ? type.pointerTo() : type;
    }

    /** const/volatile on an array object qualify its elements for lvalue access. */
    private static CrakenType qualifiedElementType(CrakenType arrayType, CrakenType elementType) {
        java.util.EnumSet<CrakenType.TypeQualifier> qualifiers = java.util.EnumSet.noneOf(CrakenType.TypeQualifier.class);
        qualifiers.addAll(elementType.qualifiers());
        for (CrakenType.TypeQualifier qualifier : arrayType.qualifiers()) {
            if (qualifier != CrakenType.TypeQualifier.RESTRICT) {
                qualifiers.add(qualifier);
            }
        }
        return CrakenType.qualified(elementType.unqualified(), qualifiers);
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
