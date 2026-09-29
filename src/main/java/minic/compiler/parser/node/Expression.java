package minic.compiler.parser.node;

import minic.compiler.lexer.token.TokenType;
import minic.compiler.type.MiniType;
import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 表达式节点及其全部具体类型。
 */
public interface Expression extends AstNode {

    record AssignmentExpr(
            Expression target,
            TokenType operator,
            Expression value,
            SourceRange range
    ) implements Expression {
        public AssignmentExpr {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");
        }

        public Optional<TokenType> compoundBinaryOperator() {
            return switch (operator) {
                case PLUS_EQUAL -> Optional.of(TokenType.PLUS);
                case MINUS_EQUAL -> Optional.of(TokenType.MINUS);
                case STAR_EQUAL -> Optional.of(TokenType.STAR);
                case SLASH_EQUAL -> Optional.of(TokenType.SLASH);
                case PERCENT_EQUAL -> Optional.of(TokenType.PERCENT);
                case AMPERSAND_EQUAL -> Optional.of(TokenType.AMPERSAND);
                case PIPE_EQUAL -> Optional.of(TokenType.PIPE);
                case CARET_EQUAL -> Optional.of(TokenType.CARET);
                case LESS_LESS_EQUAL -> Optional.of(TokenType.LESS_LESS);
                case GREATER_GREATER_EQUAL -> Optional.of(TokenType.GREATER_GREATER);
                default -> Optional.empty();
            };
        }
    }

    record BinaryExpr(
            Expression left,
            TokenType operator,
            Expression right,
            SourceRange range
    ) implements Expression {
        public BinaryExpr {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(right, "right");
            Objects.requireNonNull(range, "range");
        }
    }

    record BoolLiteralExpr(boolean value, String lexeme, SourceRange range) implements Expression {
        public BoolLiteralExpr {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");
        }
    }

    record CallExpr(Expression callee, List<Expression> arguments, SourceRange range) implements Expression {
        public CallExpr {
            Objects.requireNonNull(callee, "callee");
            Objects.requireNonNull(arguments, "arguments");
            Objects.requireNonNull(range, "range");
            arguments = List.copyOf(arguments);
        }

        public String calleeName() {
            if (callee instanceof NameExpr nameExpr) {
                return nameExpr.name();
            }
            throw new IllegalStateException("callee is not a direct function name");
        }

        public boolean hasDirectCalleeName() {
            return callee instanceof NameExpr;
        }
    }

    record CharLiteralExpr(char value, String lexeme, SourceRange range) implements Expression {
        public CharLiteralExpr {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");
        }
    }

    record ConditionalExpr(
            Expression condition,
            Expression thenExpression,
            Expression elseExpression,
            SourceRange range
    ) implements Expression {
        public ConditionalExpr {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(thenExpression, "thenExpression");
            Objects.requireNonNull(elseExpression, "elseExpression");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Explicit C cast expression. */
    record CastExpr(MiniType targetType, Expression operand, SourceRange range) implements Expression {
        public CastExpr {
            Objects.requireNonNull(targetType, "targetType");
            Objects.requireNonNull(operand, "operand");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Left-to-right comma expression; its value is the final operand. */
    record CommaExpr(List<Expression> expressions, SourceRange range) implements Expression {
        public CommaExpr {
            Objects.requireNonNull(expressions, "expressions");
            Objects.requireNonNull(range, "range");
            expressions = List.copyOf(expressions);
            if (expressions.size() < 2) {
                throw new IllegalArgumentException("comma expression needs at least two operands");
            }
        }
    }

    record DoubleLiteralExpr(double value, String lexeme, SourceRange range) implements Expression {
        public DoubleLiteralExpr {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");
        }
    }

    record FieldAccessExpr(
            Expression target,
            String fieldName,
            boolean viaPointer,
            SourceRange range
    ) implements Expression {
        public FieldAccessExpr {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(fieldName, "fieldName");
            Objects.requireNonNull(range, "range");
            if (fieldName.isBlank()) {
                throw new IllegalArgumentException("fieldName must not be blank");
            }
        }
    }

    record FloatLiteralExpr(float value, String lexeme, SourceRange range) implements Expression {
        public FloatLiteralExpr {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");
        }
    }

    record GroupingExpr(Expression expression, SourceRange range) implements Expression {
        public GroupingExpr {
            Objects.requireNonNull(expression, "expression");
            Objects.requireNonNull(range, "range");
        }
    }

    record IndexExpr(Expression target, Expression index, SourceRange range) implements Expression {
        public IndexExpr {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(range, "range");
        }
    }

    record IntegerLiteralExpr(int value, String lexeme, SourceRange range) implements Expression {
        public IntegerLiteralExpr {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Integer constant whose exact C type is determined by radix, suffix and value. */
    record IntegerConstantExpr(long value, MiniType type, String lexeme, SourceRange range) implements Expression {
        public IntegerConstantExpr {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");
        }
    }

    record LongLiteralExpr(long value, String lexeme, SourceRange range) implements Expression {
        public LongLiteralExpr {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");
        }
    }

    record NameExpr(String name, SourceRange range) implements Expression {
        public NameExpr {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }

    record NullLiteralExpr(String lexeme, SourceRange range) implements Expression {
        public NullLiteralExpr {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");
        }
    }

    record PostfixUpdateExpr(Expression target, TokenType operator, SourceRange range) implements Expression {
        public PostfixUpdateExpr {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(range, "range");
            if (operator != TokenType.PLUS_PLUS && operator != TokenType.MINUS_MINUS) {
                throw new IllegalArgumentException("operator must be ++ or --");
            }
        }
    }

    record SizeofExpr(Expression expression, MiniType queriedType, SourceRange range) implements Expression {
        public SizeofExpr {
            Objects.requireNonNull(range, "range");
            if ((expression == null) == (queriedType == null)) {
                throw new IllegalArgumentException("sizeof must query exactly one expression or type");
            }
        }

        public Optional<Expression> expressionOptional() {
            return Optional.ofNullable(expression);
        }

        public Optional<MiniType> queriedTypeOptional() {
            return Optional.ofNullable(queriedType);
        }
    }

    record StringLiteralExpr(String value, String lexeme, SourceRange range) implements Expression {
        public StringLiteralExpr {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");
        }
    }

    record AggregateInitExpr(List<Expression> values, SourceRange range) implements Expression {
        public AggregateInitExpr {
            Objects.requireNonNull(values, "values");
            Objects.requireNonNull(range, "range");
            values = List.copyOf(values);
        }
    }

    /** One initializer value preceded by one or more .field or [index] designators. */
    record DesignatedInitExpr(List<Designator> designators, Expression value, SourceRange range) implements Expression {
        public DesignatedInitExpr {
            Objects.requireNonNull(designators, "designators");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");
            designators = List.copyOf(designators);
            if (designators.isEmpty()) throw new IllegalArgumentException("designators must not be empty");
        }
    }

    interface Designator {
        SourceRange range();

        record Field(String name, SourceRange range) implements Designator {
            public Field { Objects.requireNonNull(name, "name"); Objects.requireNonNull(range, "range"); }
        }

        record Index(int index, SourceRange range) implements Designator {
            public Index { if (index < 0) throw new IllegalArgumentException("index must be non-negative"); Objects.requireNonNull(range, "range"); }
        }
    }

    record UnaryExpr(TokenType operator, Expression operand, SourceRange range) implements Expression {
        public UnaryExpr {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(operand, "operand");
            Objects.requireNonNull(range, "range");
        }
    }
}
