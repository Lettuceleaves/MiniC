package craken.compiler.parser.node;

import craken.compiler.lexer.token.TokenType;
import craken.compiler.type.CrakenType;
import craken.SourceRange;
import craken.compiler.type.TemplateArgument;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 表达式节点及其全部具体类型。
 */
public interface Expression extends AstNode {

    /** Compiler-only first initialization of existing storage; ordinary assignment stays cv-checked. */
    static final class InitializeExpr extends AbstractAstNode implements Expression {
        private final Expression target;
        private final Expression value;
        private final SourceRange range;

        @AstNodeConstructor({"target", "value", "range"})
        public InitializeExpr(Expression target, Expression value, SourceRange range) {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");

            this.target = target;
            this.value = value;
            this.range = range;
        }

        public Expression target() { return target; }
        public Expression value() { return value; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InitializeExpr that)) return false;
            return Objects.equals(target, that.target)
                    && Objects.equals(value, that.value)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(target);
            result = 31 * result + Objects.hashCode(value);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "InitializeExpr[target=" + target + ", value=" + value + ", range=" + range + "]";
        }
    }

    /** Compiler-only construction in a supplied destination; the capture is an immutable pointer value. */
    static final class ObjectInitExpr extends AbstractAstNode implements Expression {
        private final CrakenType type;
        private final String destinationName;
        private final Expression body;
        private final SourceRange range;

        @AstNodeConstructor({"type", "destinationName", "body", "range"})
        public ObjectInitExpr(CrakenType type, String destinationName, Expression body, SourceRange range) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(destinationName, "destinationName");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(range, "range");
            if (destinationName.isBlank()) throw new IllegalArgumentException("destination name must not be blank");

            this.type = type;
            this.destinationName = destinationName;
            this.body = body;
            this.range = range;
        }

        /** Follow only object-result paths, not construction used in operands or call arguments. */
        public static boolean occursInResultOf(Expression expression) {
            return switch (expression) {
                case null -> false;
                case ObjectInitExpr ignored -> true;
                case CleanupExpr cleanup -> occursInResultOf(cleanup.value());
                case LetExpr capture -> occursInResultOf(capture.body());
                case GroupingExpr group -> occursInResultOf(group.expression());
                case CommaExpr comma -> occursInResultOf(comma.expressions().getLast());
                case ConditionalExpr conditional -> occursInResultOf(conditional.thenExpression())
                        || occursInResultOf(conditional.elseExpression());
                default -> false;
            };
        }

        public CrakenType type() { return type; }
        public String destinationName() { return destinationName; }
        public Expression body() { return body; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ObjectInitExpr that)) return false;
            return Objects.equals(type, that.type)
                    && Objects.equals(destinationName, that.destinationName)
                    && Objects.equals(body, that.body)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(destinationName);
            result = 31 * result + Objects.hashCode(body);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "ObjectInitExpr[type=" + type + ", destinationName=" + destinationName + ", body=" + body + ", range=" + range + "]";
        }
    }

    /** Source ownership for later cleanup insertion; the owner is not an executable child. */
    record TemporaryLifetime(Kind kind, AstNode sourceOwner) {
        public TemporaryLifetime {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(sourceOwner, "sourceOwner");
        }

        public enum Kind { REFERENCE_SCOPE, FULL_EXPRESSION }
    }

    /** Compiler-only object initialization whose result is the address of its stack storage. */
    static final class MaterializeExpr extends AbstractAstNode
            implements Expression {
        private final CrakenType type;
        private final Expression initializer;
        private final TemporaryLifetime lifetime;
        private final SourceRange range;

        @AstNodeConstructor({"type", "initializer", "lifetime", "range"})
        public MaterializeExpr(CrakenType type, Expression initializer, TemporaryLifetime lifetime, SourceRange range) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(lifetime, "lifetime");
            Objects.requireNonNull(range, "range");

            this.type = type;
            this.initializer = initializer;
            this.lifetime = lifetime;
            this.range = range;
        }

        public CrakenType type() { return type; }
        public Expression initializer() { return initializer; }
        public TemporaryLifetime lifetime() { return lifetime; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MaterializeExpr that)) return false;
            return Objects.equals(type, that.type)
                    && Objects.equals(initializer, that.initializer)
                    && Objects.equals(lifetime, that.lifetime)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(initializer);
            result = 31 * result + Objects.hashCode(lifetime);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "MaterializeExpr[type=" + type + ", initializer=" + initializer + ", lifetime=" + lifetime + ", range=" + range + "]";
        }
    }

    /** Compiler-only, expression-scoped value capture; it does not create an addressable object. */
    static final class LetExpr extends AbstractAstNode
            implements Expression {
        private final String name;
        private final CrakenType type;
        private final Expression initializer;
        private final Expression body;
        private final SourceRange range;

        @AstNodeConstructor({"name", "type", "initializer", "body", "range"})
        public LetExpr(String name, CrakenType type, Expression initializer, Expression body, SourceRange range) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) throw new IllegalArgumentException("capture name must not be blank");

            this.name = name;
            this.type = type;
            this.initializer = initializer;
            this.body = body;
            this.range = range;
        }

        public String name() { return name; }
        public CrakenType type() { return type; }
        public Expression initializer() { return initializer; }
        public Expression body() { return body; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LetExpr that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(type, that.type)
                    && Objects.equals(initializer, that.initializer)
                    && Objects.equals(body, that.body)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(initializer);
            result = 31 * result + Objects.hashCode(body);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "LetExpr[name=" + name + ", type=" + type + ", initializer=" + initializer + ", body=" + body + ", range=" + range + "]";
        }
    }

    static final class AssignmentExpr extends AbstractAstNode implements Expression {
        private final Expression target;
        private final TokenType operator;
        private final Expression value;
        private final SourceRange range;

        @AstNodeConstructor({"target", "operator", "value", "range"})
        public AssignmentExpr(Expression target, TokenType operator, Expression value, SourceRange range) {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");

            this.target = target;
            this.operator = operator;
            this.value = value;
            this.range = range;
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

        public Expression target() { return target; }
        public TokenType operator() { return operator; }
        public Expression value() { return value; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AssignmentExpr that)) return false;
            return Objects.equals(target, that.target)
                    && Objects.equals(operator, that.operator)
                    && Objects.equals(value, that.value)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(target);
            result = 31 * result + Objects.hashCode(operator);
            result = 31 * result + Objects.hashCode(value);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "AssignmentExpr[target=" + target + ", operator=" + operator + ", value=" + value + ", range=" + range + "]";
        }
    }

    static final class BinaryExpr extends AbstractAstNode implements Expression {
        private final Expression left;
        private final TokenType operator;
        private final Expression right;
        private final SourceRange range;

        @AstNodeConstructor({"left", "operator", "right", "range"})
        public BinaryExpr(Expression left, TokenType operator, Expression right, SourceRange range) {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(right, "right");
            Objects.requireNonNull(range, "range");

            this.left = left;
            this.operator = operator;
            this.right = right;
            this.range = range;
        }

        public Expression left() { return left; }
        public TokenType operator() { return operator; }
        public Expression right() { return right; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BinaryExpr that)) return false;
            return Objects.equals(left, that.left)
                    && Objects.equals(operator, that.operator)
                    && Objects.equals(right, that.right)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(left);
            result = 31 * result + Objects.hashCode(operator);
            result = 31 * result + Objects.hashCode(right);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "BinaryExpr[left=" + left + ", operator=" + operator + ", right=" + right + ", range=" + range + "]";
        }
    }

    static final class BoolLiteralExpr extends AbstractAstNode implements Expression {
        private final boolean value;
        private final String lexeme;
        private final SourceRange range;

        @AstNodeConstructor({"value", "lexeme", "range"})
        public BoolLiteralExpr(boolean value, String lexeme, SourceRange range) {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");

            this.value = value;
            this.lexeme = lexeme;
            this.range = range;
        }

        public boolean value() { return value; }
        public String lexeme() { return lexeme; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BoolLiteralExpr that)) return false;
            return value == that.value
                    && Objects.equals(lexeme, that.lexeme)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Boolean.hashCode(value);
            result = 31 * result + Objects.hashCode(lexeme);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "BoolLiteralExpr[value=" + value + ", lexeme=" + lexeme + ", range=" + range + "]";
        }
    }

    /** Arguments retain ABI order; evaluation order is a validated permutation, after evaluating the callee. */
    static final class CallExpr extends AbstractAstNode implements Expression {
        private final Expression callee;
        private final List<Expression> arguments;
        private final List<Integer> argumentEvaluationOrder;
        private final SourceRange range;

        public CallExpr(Expression callee, List<Expression> arguments, SourceRange range) {
            this(callee, arguments, java.util.stream.IntStream.range(0, arguments.size()).boxed().toList(), range);
        }
        @AstNodeConstructor({"callee", "arguments", "argumentEvaluationOrder", "range"})
        public CallExpr(Expression callee, List<Expression> arguments, List<Integer> argumentEvaluationOrder, SourceRange range) {
            Objects.requireNonNull(callee, "callee");
            Objects.requireNonNull(arguments, "arguments");
            Objects.requireNonNull(range, "range");
            arguments = List.copyOf(arguments);
            argumentEvaluationOrder = List.copyOf(argumentEvaluationOrder);
            int argumentCount = arguments.size();
            if (argumentEvaluationOrder.size() != argumentCount
                    || new java.util.HashSet<>(argumentEvaluationOrder).size() != argumentCount
                    || argumentEvaluationOrder.stream().anyMatch(index -> index < 0 || index >= argumentCount))
                throw new IllegalArgumentException("Argument evaluation order must be a permutation of the argument indices");

            this.callee = callee;
            this.arguments = arguments;
            this.argumentEvaluationOrder = argumentEvaluationOrder;
            this.range = range;
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

        public Expression callee() { return callee; }
        public List<Expression> arguments() { return arguments; }
        public List<Integer> argumentEvaluationOrder() { return argumentEvaluationOrder; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CallExpr that)) return false;
            return Objects.equals(callee, that.callee)
                    && Objects.equals(arguments, that.arguments)
                    && Objects.equals(argumentEvaluationOrder, that.argumentEvaluationOrder)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(callee);
            result = 31 * result + Objects.hashCode(arguments);
            result = 31 * result + Objects.hashCode(argumentEvaluationOrder);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "CallExpr[callee=" + callee + ", arguments=" + arguments + ", argumentEvaluationOrder=" + argumentEvaluationOrder + ", range=" + range + "]";
        }
    }

    enum LiteralEncoding { ORDINARY, UTF8, UTF16, UTF32 }

    static final class CharLiteralExpr extends AbstractAstNode implements Expression {
        private final int value;
        private final LiteralEncoding encoding;
        private final String lexeme;
        private final SourceRange range;

        @AstNodeConstructor({"value", "encoding", "lexeme", "range"})
        public CharLiteralExpr(int value, LiteralEncoding encoding, String lexeme, SourceRange range) {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(encoding, "encoding");
            Objects.requireNonNull(range, "range");

            this.value = value;
            this.encoding = encoding;
            this.lexeme = lexeme;
            this.range = range;
        }

        public CharLiteralExpr(char value, String lexeme, SourceRange range) {
            this(value, LiteralEncoding.ORDINARY, lexeme, range);
        }

        public int value() { return value; }
        public LiteralEncoding encoding() { return encoding; }
        public String lexeme() { return lexeme; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CharLiteralExpr that)) return false;
            return value == that.value
                    && Objects.equals(encoding, that.encoding)
                    && Objects.equals(lexeme, that.lexeme)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Integer.hashCode(value);
            result = 31 * result + Objects.hashCode(encoding);
            result = 31 * result + Objects.hashCode(lexeme);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "CharLiteralExpr[value=" + value + ", encoding=" + encoding + ", lexeme=" + lexeme + ", range=" + range + "]";
        }
    }

    static final class ConditionalExpr extends AbstractAstNode implements Expression {
        private final Expression condition;
        private final Expression thenExpression;
        private final Expression elseExpression;
        private final SourceRange range;

        @AstNodeConstructor({"condition", "thenExpression", "elseExpression", "range"})
        public ConditionalExpr(Expression condition, Expression thenExpression, Expression elseExpression, SourceRange range) {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(thenExpression, "thenExpression");
            Objects.requireNonNull(elseExpression, "elseExpression");
            Objects.requireNonNull(range, "range");

            this.condition = condition;
            this.thenExpression = thenExpression;
            this.elseExpression = elseExpression;
            this.range = range;
        }

        public Expression condition() { return condition; }
        public Expression thenExpression() { return thenExpression; }
        public Expression elseExpression() { return elseExpression; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ConditionalExpr that)) return false;
            return Objects.equals(condition, that.condition)
                    && Objects.equals(thenExpression, that.thenExpression)
                    && Objects.equals(elseExpression, that.elseExpression)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(condition);
            result = 31 * result + Objects.hashCode(thenExpression);
            result = 31 * result + Objects.hashCode(elseExpression);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "ConditionalExpr[condition=" + condition + ", thenExpression=" + thenExpression + ", elseExpression=" + elseExpression + ", range=" + range + "]";
        }
    }

    /** Explicit C cast expression. */
    static final class CastExpr extends AbstractAstNode implements Expression {
        private final CrakenType targetType;
        private final Expression operand;
        private final SourceRange range;

        @AstNodeConstructor({"targetType", "operand", "range"})
        public CastExpr(CrakenType targetType, Expression operand, SourceRange range) {
            Objects.requireNonNull(targetType, "targetType");
            Objects.requireNonNull(operand, "operand");
            Objects.requireNonNull(range, "range");

            this.targetType = targetType;
            this.operand = operand;
            this.range = range;
        }

        public CrakenType targetType() { return targetType; }
        public Expression operand() { return operand; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CastExpr that)) return false;
            return Objects.equals(targetType, that.targetType)
                    && Objects.equals(operand, that.operand)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(targetType);
            result = 31 * result + Objects.hashCode(operand);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "CastExpr[targetType=" + targetType + ", operand=" + operand + ", range=" + range + "]";
        }
    }

    /** Left-to-right comma expression; its value is the final operand. */
    static final class CommaExpr extends AbstractAstNode implements Expression {
        private final List<Expression> expressions;
        private final SourceRange range;

        @AstNodeConstructor({"expressions", "range"})
        public CommaExpr(List<Expression> expressions, SourceRange range) {
            Objects.requireNonNull(expressions, "expressions");
            Objects.requireNonNull(range, "range");
            expressions = List.copyOf(expressions);
            if (expressions.size() < 2) {
                throw new IllegalArgumentException("comma expression needs at least two operands");
            }

            this.expressions = expressions;
            this.range = range;
        }

        public List<Expression> expressions() { return expressions; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CommaExpr that)) return false;
            return Objects.equals(expressions, that.expressions)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(expressions);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "CommaExpr[expressions=" + expressions + ", range=" + range + "]";
        }
    }

    static final class DoubleLiteralExpr extends AbstractAstNode implements Expression {
        private final double value;
        private final String lexeme;
        private final SourceRange range;

        public CrakenType literalType() { return lexeme.endsWith("L") || lexeme.endsWith("l") ? CrakenType.LONG_DOUBLE : CrakenType.DOUBLE; }
        @AstNodeConstructor({"value", "lexeme", "range"})
        public DoubleLiteralExpr(double value, String lexeme, SourceRange range) {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");

            this.value = value;
            this.lexeme = lexeme;
            this.range = range;
        }

        public double value() { return value; }
        public String lexeme() { return lexeme; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DoubleLiteralExpr that)) return false;
            return Double.compare(value, that.value) == 0
                    && Objects.equals(lexeme, that.lexeme)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Double.hashCode(value);
            result = 31 * result + Objects.hashCode(lexeme);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "DoubleLiteralExpr[value=" + value + ", lexeme=" + lexeme + ", range=" + range + "]";
        }
    }

    static final class FieldAccessExpr extends AbstractAstNode implements Expression {
        private final Expression target;
        private final String fieldName;
        private final boolean viaPointer;
        private final SourceRange range;

        @AstNodeConstructor({"target", "fieldName", "viaPointer", "range"})
        public FieldAccessExpr(Expression target, String fieldName, boolean viaPointer, SourceRange range) {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(fieldName, "fieldName");
            Objects.requireNonNull(range, "range");
            if (fieldName.isBlank()) {
                throw new IllegalArgumentException("fieldName must not be blank");
            }

            this.target = target;
            this.fieldName = fieldName;
            this.viaPointer = viaPointer;
            this.range = range;
        }

        public Expression target() { return target; }
        public String fieldName() { return fieldName; }
        public boolean viaPointer() { return viaPointer; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FieldAccessExpr that)) return false;
            return Objects.equals(target, that.target)
                    && Objects.equals(fieldName, that.fieldName)
                    && viaPointer == that.viaPointer
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(target);
            result = 31 * result + Objects.hashCode(fieldName);
            result = 31 * result + Boolean.hashCode(viaPointer);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "FieldAccessExpr[target=" + target + ", fieldName=" + fieldName + ", viaPointer=" + viaPointer + ", range=" + range + "]";
        }
    }

    static final class FloatLiteralExpr extends AbstractAstNode implements Expression {
        private final float value;
        private final String lexeme;
        private final SourceRange range;

        @AstNodeConstructor({"value", "lexeme", "range"})
        public FloatLiteralExpr(float value, String lexeme, SourceRange range) {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");

            this.value = value;
            this.lexeme = lexeme;
            this.range = range;
        }

        public float value() { return value; }
        public String lexeme() { return lexeme; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FloatLiteralExpr that)) return false;
            return Float.compare(value, that.value) == 0
                    && Objects.equals(lexeme, that.lexeme)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Float.hashCode(value);
            result = 31 * result + Objects.hashCode(lexeme);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "FloatLiteralExpr[value=" + value + ", lexeme=" + lexeme + ", range=" + range + "]";
        }
    }

    static final class GroupingExpr extends AbstractAstNode implements Expression {
        private final Expression expression;
        private final SourceRange range;

        @AstNodeConstructor({"expression", "range"})
        public GroupingExpr(Expression expression, SourceRange range) {
            Objects.requireNonNull(expression, "expression");
            Objects.requireNonNull(range, "range");

            this.expression = expression;
            this.range = range;
        }

        public Expression expression() { return expression; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof GroupingExpr that)) return false;
            return Objects.equals(expression, that.expression)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(expression);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "GroupingExpr[expression=" + expression + ", range=" + range + "]";
        }
    }

    static final class IndexExpr extends AbstractAstNode implements Expression {
        private final Expression target;
        private final Expression index;
        private final SourceRange range;

        @AstNodeConstructor({"target", "index", "range"})
        public IndexExpr(Expression target, Expression index, SourceRange range) {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(range, "range");

            this.target = target;
            this.index = index;
            this.range = range;
        }

        public Expression target() { return target; }
        public Expression index() { return index; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IndexExpr that)) return false;
            return Objects.equals(target, that.target)
                    && Objects.equals(index, that.index)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(target);
            result = 31 * result + Objects.hashCode(index);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "IndexExpr[target=" + target + ", index=" + index + ", range=" + range + "]";
        }
    }

    static final class IntegerLiteralExpr extends AbstractAstNode implements Expression {
        private final int value;
        private final String lexeme;
        private final SourceRange range;

        @AstNodeConstructor({"value", "lexeme", "range"})
        public IntegerLiteralExpr(int value, String lexeme, SourceRange range) {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");

            this.value = value;
            this.lexeme = lexeme;
            this.range = range;
        }

        public int value() { return value; }
        public String lexeme() { return lexeme; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IntegerLiteralExpr that)) return false;
            return value == that.value
                    && Objects.equals(lexeme, that.lexeme)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Integer.hashCode(value);
            result = 31 * result + Objects.hashCode(lexeme);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "IntegerLiteralExpr[value=" + value + ", lexeme=" + lexeme + ", range=" + range + "]";
        }
    }

    /** Integer constant whose exact C type is determined by radix, suffix and value. */
    static final class IntegerConstantExpr extends AbstractAstNode implements Expression {
        private final long value;
        private final CrakenType type;
        private final String lexeme;
        private final SourceRange range;

        @AstNodeConstructor({"value", "type", "lexeme", "range"})
        public IntegerConstantExpr(long value, CrakenType type, String lexeme, SourceRange range) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");

            this.value = value;
            this.type = type;
            this.lexeme = lexeme;
            this.range = range;
        }

        public long value() { return value; }
        public CrakenType type() { return type; }
        public String lexeme() { return lexeme; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IntegerConstantExpr that)) return false;
            return value == that.value
                    && Objects.equals(type, that.type)
                    && Objects.equals(lexeme, that.lexeme)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Long.hashCode(value);
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(lexeme);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "IntegerConstantExpr[value=" + value + ", type=" + type + ", lexeme=" + lexeme + ", range=" + range + "]";
        }
    }

    static final class LongLiteralExpr extends AbstractAstNode implements Expression {
        private final long value;
        private final String lexeme;
        private final SourceRange range;

        @AstNodeConstructor({"value", "lexeme", "range"})
        public LongLiteralExpr(long value, String lexeme, SourceRange range) {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");

            this.value = value;
            this.lexeme = lexeme;
            this.range = range;
        }

        public long value() { return value; }
        public String lexeme() { return lexeme; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LongLiteralExpr that)) return false;
            return value == that.value
                    && Objects.equals(lexeme, that.lexeme)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Long.hashCode(value);
            result = 31 * result + Objects.hashCode(lexeme);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "LongLiteralExpr[value=" + value + ", lexeme=" + lexeme + ", range=" + range + "]";
        }
    }

    /** Source receiver expression; no implicit core variable is invented before binding. */
    static final class ThisExpr extends AbstractAstNode implements Expression {
        private final SourceRange range;

        @AstNodeConstructor({"range"})
        public ThisExpr(SourceRange range) { Objects.requireNonNull(range, "range");
            this.range = range;
        }

        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ThisExpr that)) return false;
            return Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "ThisExpr[range=" + range + "]";
        }
    }

    static final class NameExpr extends AbstractAstNode implements Expression {
        private final String name;
        private final SourceRange range;

        @AstNodeConstructor({"name", "range"})
        public NameExpr(String name, SourceRange range) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }

            this.name = name;
            this.range = range;
        }

        public String name() { return name; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NameExpr that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "NameExpr[name=" + name + ", range=" + range + "]";
        }
    }

    /** A source-spelled qualified reference, deliberately not treated as a direct resolved callee. */
    static final class QualifiedNameExpr extends AbstractAstNode implements Expression {
        private final QualifiedName name;

        @AstNodeConstructor({"name"})
        public QualifiedNameExpr(QualifiedName name) { Objects.requireNonNull(name, "name");
            this.name = name;
        }
        @Override public SourceRange range() { return name.range(); }

        public QualifiedName name() { return name; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof QualifiedNameExpr that)) return false;
            return Objects.equals(name, that.name);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            return result;
        }

        @Override public String toString() {
            return "QualifiedNameExpr[name=" + name + "]";
        }
    }

    static final class NullLiteralExpr extends AbstractAstNode implements Expression {
        private final String lexeme;
        private final SourceRange range;

        @AstNodeConstructor({"lexeme", "range"})
        public NullLiteralExpr(String lexeme, SourceRange range) {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");

            this.lexeme = lexeme;
            this.range = range;
        }

        public String lexeme() { return lexeme; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NullLiteralExpr that)) return false;
            return Objects.equals(lexeme, that.lexeme)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(lexeme);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "NullLiteralExpr[lexeme=" + lexeme + ", range=" + range + "]";
        }
    }

    static final class PostfixUpdateExpr extends AbstractAstNode implements Expression {
        private final Expression target;
        private final TokenType operator;
        private final SourceRange range;

        @AstNodeConstructor({"target", "operator", "range"})
        public PostfixUpdateExpr(Expression target, TokenType operator, SourceRange range) {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(range, "range");
            if (operator != TokenType.PLUS_PLUS && operator != TokenType.MINUS_MINUS) {
                throw new IllegalArgumentException("operator must be ++ or --");
            }

            this.target = target;
            this.operator = operator;
            this.range = range;
        }

        public Expression target() { return target; }
        public TokenType operator() { return operator; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PostfixUpdateExpr that)) return false;
            return Objects.equals(target, that.target)
                    && Objects.equals(operator, that.operator)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(target);
            result = 31 * result + Objects.hashCode(operator);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "PostfixUpdateExpr[target=" + target + ", operator=" + operator + ", range=" + range + "]";
        }
    }

    static final class SizeofExpr extends AbstractAstNode implements Expression {
        private final Expression expression;
        private final CrakenType queriedType;
        private final SourceRange range;

        @AstNodeConstructor({"expression", "queriedType", "range"})
        public SizeofExpr(Expression expression, CrakenType queriedType, SourceRange range) {
            Objects.requireNonNull(range, "range");
            if ((expression == null) == (queriedType == null)) {
                throw new IllegalArgumentException("sizeof must query exactly one expression or type");
            }

            this.expression = expression;
            this.queriedType = queriedType;
            this.range = range;
        }

        public Optional<Expression> expressionOptional() {
            return Optional.ofNullable(expression);
        }

        public Optional<CrakenType> queriedTypeOptional() {
            return Optional.ofNullable(queriedType);
        }

        public Expression expression() { return expression; }
        public CrakenType queriedType() { return queriedType; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SizeofExpr that)) return false;
            return Objects.equals(expression, that.expression)
                    && Objects.equals(queriedType, that.queriedType)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(expression);
            result = 31 * result + Objects.hashCode(queriedType);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "SizeofExpr[expression=" + expression + ", queriedType=" + queriedType + ", range=" + range + "]";
        }
    }

    static final class AlignofExpr extends AbstractAstNode implements Expression {
        private final Expression expression;
        private final CrakenType queriedType;
        private final SourceRange range;

        @AstNodeConstructor({"expression", "queriedType", "range"})
        public AlignofExpr(Expression expression, CrakenType queriedType, SourceRange range) {
            if ((expression == null) == (queriedType == null)) {
                throw new IllegalArgumentException("alignof requires exactly one operand");
            }
            Objects.requireNonNull(range, "range");

            this.expression = expression;
            this.queriedType = queriedType;
            this.range = range;
        }

        public Optional<Expression> expressionOptional() {
            return Optional.ofNullable(expression);
        }

        public Optional<CrakenType> queriedTypeOptional() {
            return Optional.ofNullable(queriedType);
        }

        public Expression expression() { return expression; }
        public CrakenType queriedType() { return queriedType; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AlignofExpr that)) return false;
            return Objects.equals(expression, that.expression)
                    && Objects.equals(queriedType, that.queriedType)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(expression);
            result = 31 * result + Objects.hashCode(queriedType);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "AlignofExpr[expression=" + expression + ", queriedType=" + queriedType + ", range=" + range + "]";
        }
    }

    /** Initializes a va_list cursor immediately after the function's final named argument. */
    static final class VaStartExpr extends AbstractAstNode implements Expression {
        private final Expression list;
        private final Expression lastParameter;
        private final SourceRange range;

        @AstNodeConstructor({"list", "lastParameter", "range"})
        public VaStartExpr(Expression list, Expression lastParameter, SourceRange range) {
            Objects.requireNonNull(list, "list");
            Objects.requireNonNull(lastParameter, "lastParameter");
            Objects.requireNonNull(range, "range");

            this.list = list;
            this.lastParameter = lastParameter;
            this.range = range;
        }

        public Expression list() { return list; }
        public Expression lastParameter() { return lastParameter; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VaStartExpr that)) return false;
            return Objects.equals(list, that.list)
                    && Objects.equals(lastParameter, that.lastParameter)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(list);
            result = 31 * result + Objects.hashCode(lastParameter);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "VaStartExpr[list=" + list + ", lastParameter=" + lastParameter + ", range=" + range + "]";
        }
    }

    /** Reads one promoted argument and advances the va_list cursor by one Windows x64 slot. */
    static final class VaArgExpr extends AbstractAstNode implements Expression {
        private final Expression list;
        private final CrakenType requestedType;
        private final SourceRange range;

        @AstNodeConstructor({"list", "requestedType", "range"})
        public VaArgExpr(Expression list, CrakenType requestedType, SourceRange range) {
            Objects.requireNonNull(list, "list");
            Objects.requireNonNull(requestedType, "requestedType");
            Objects.requireNonNull(range, "range");

            this.list = list;
            this.requestedType = requestedType;
            this.range = range;
        }

        public Expression list() { return list; }
        public CrakenType requestedType() { return requestedType; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VaArgExpr that)) return false;
            return Objects.equals(list, that.list)
                    && Objects.equals(requestedType, that.requestedType)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(list);
            result = 31 * result + Objects.hashCode(requestedType);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "VaArgExpr[list=" + list + ", requestedType=" + requestedType + ", range=" + range + "]";
        }
    }

    /** Copies a variadic cursor; both cursors subsequently advance independently. */
    static final class VaCopyExpr extends AbstractAstNode implements Expression {
        private final Expression destination;
        private final Expression source;
        private final SourceRange range;

        @AstNodeConstructor({"destination", "source", "range"})
        public VaCopyExpr(Expression destination, Expression source, SourceRange range) {
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(range, "range");

            this.destination = destination;
            this.source = source;
            this.range = range;
        }

        public Expression destination() { return destination; }
        public Expression source() { return source; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VaCopyExpr that)) return false;
            return Objects.equals(destination, that.destination)
                    && Objects.equals(source, that.source)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(destination);
            result = 31 * result + Objects.hashCode(source);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "VaCopyExpr[destination=" + destination + ", source=" + source + ", range=" + range + "]";
        }
    }

    /** Ends use of a variadic cursor. */
    static final class VaEndExpr extends AbstractAstNode implements Expression {
        private final Expression list;
        private final SourceRange range;

        @AstNodeConstructor({"list", "range"})
        public VaEndExpr(Expression list, SourceRange range) {
            Objects.requireNonNull(list, "list");
            Objects.requireNonNull(range, "range");

            this.list = list;
            this.range = range;
        }

        public Expression list() { return list; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VaEndExpr that)) return false;
            return Objects.equals(list, that.list)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(list);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "VaEndExpr[list=" + list + ", range=" + range + "]";
        }
    }

    static final class StringLiteralExpr extends AbstractAstNode implements Expression {
        private final String value;
        private final LiteralEncoding encoding;
        private final String lexeme;
        private final SourceRange range;

        @AstNodeConstructor({"value", "encoding", "lexeme", "range"})
        public StringLiteralExpr(String value, LiteralEncoding encoding, String lexeme, SourceRange range) {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(encoding, "encoding");
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");

            this.value = value;
            this.encoding = encoding;
            this.lexeme = lexeme;
            this.range = range;
        }

        public StringLiteralExpr(String value, String lexeme, SourceRange range) {
            this(value, LiteralEncoding.ORDINARY, lexeme, range);
        }

        public String value() { return value; }
        public LiteralEncoding encoding() { return encoding; }
        public String lexeme() { return lexeme; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StringLiteralExpr that)) return false;
            return Objects.equals(value, that.value)
                    && Objects.equals(encoding, that.encoding)
                    && Objects.equals(lexeme, that.lexeme)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            result = 31 * result + Objects.hashCode(encoding);
            result = 31 * result + Objects.hashCode(lexeme);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "StringLiteralExpr[value=" + value + ", encoding=" + encoding + ", lexeme=" + lexeme + ", range=" + range + "]";
        }
    }

    static final class AggregateInitExpr extends AbstractAstNode implements Expression {
        private final List<Expression> values;
        private final SourceRange range;

        @AstNodeConstructor({"values", "range"})
        public AggregateInitExpr(List<Expression> values, SourceRange range) {
            Objects.requireNonNull(values, "values");
            Objects.requireNonNull(range, "range");
            values = List.copyOf(values);

            this.values = values;
            this.range = range;
        }

        public List<Expression> values() { return values; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AggregateInitExpr that)) return false;
            return Objects.equals(values, that.values)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(values);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "AggregateInitExpr[values=" + values + ", range=" + range + "]";
        }
    }

    /** One initializer value preceded by one or more .field or [index] designators. */
    static final class DesignatedInitExpr extends AbstractAstNode implements Expression {
        private final List<Designator> designators;
        private final Expression value;
        private final SourceRange range;

        @AstNodeConstructor({"designators", "value", "range"})
        public DesignatedInitExpr(List<Designator> designators, Expression value, SourceRange range) {
            Objects.requireNonNull(designators, "designators");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");
            designators = List.copyOf(designators);
            if (designators.isEmpty()) throw new IllegalArgumentException("designators must not be empty");

            this.designators = designators;
            this.value = value;
            this.range = range;
        }

        public List<Designator> designators() { return designators; }
        public Expression value() { return value; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DesignatedInitExpr that)) return false;
            return Objects.equals(designators, that.designators)
                    && Objects.equals(value, that.value)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(designators);
            result = 31 * result + Objects.hashCode(value);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "DesignatedInitExpr[designators=" + designators + ", value=" + value + ", range=" + range + "]";
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

    static final class UnaryExpr extends AbstractAstNode implements Expression {
        private final TokenType operator;
        private final Expression operand;
        private final SourceRange range;

        @AstNodeConstructor({"operator", "operand", "range"})
        public UnaryExpr(TokenType operator, Expression operand, SourceRange range) {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(operand, "operand");
            Objects.requireNonNull(range, "range");

            this.operator = operator;
            this.operand = operand;
            this.range = range;
        }

        public TokenType operator() { return operator; }
        public Expression operand() { return operand; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof UnaryExpr that)) return false;
            return Objects.equals(operator, that.operator)
                    && Objects.equals(operand, that.operand)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(operator);
            result = 31 * result + Objects.hashCode(operand);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "UnaryExpr[operator=" + operator + ", operand=" + operand + ", range=" + range + "]";
        }
    }

    /**
     * Internal expression boundary: capture the value (or construct directly at its supplied
     * destination), execute a void cleanup, then yield that value. Reference results are already
     * normalized to pointer values. Registration and lifetime-extension policy belong to the binder.
     */
    static final class CleanupExpr extends AbstractAstNode implements Expression {
        private final Expression value;
        private final Expression cleanup;
        private final SourceRange range;

        @AstNodeConstructor({"value", "cleanup", "range"})
        public CleanupExpr(Expression value, Expression cleanup, SourceRange range) {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(cleanup, "cleanup");
            Objects.requireNonNull(range, "range");

            this.value = value;
            this.cleanup = cleanup;
            this.range = range;
        }

        public Expression value() { return value; }
        public Expression cleanup() { return cleanup; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CleanupExpr that)) return false;
            return Objects.equals(value, that.value)
                    && Objects.equals(cleanup, that.cleanup)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            result = 31 * result + Objects.hashCode(cleanup);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "CleanupExpr[value=" + value + ", cleanup=" + cleanup + ", range=" + range + "]";
        }
    }

    /** Source-only functional conversion or object construction, preserving its written initializer. */
    static final class ConstructionExpr extends AbstractAstNode implements Expression {
        private final CrakenType type;
        private final InitializerSyntax initializer;
        private final SourceRange typeRange;
        private final SourceRange range;

        @AstNodeConstructor({"type", "initializer", "typeRange", "range"})
        public ConstructionExpr(CrakenType type, InitializerSyntax initializer, SourceRange typeRange, SourceRange range) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(typeRange, "typeRange");
            Objects.requireNonNull(range, "range");
            if (initializer.kind() != InitializerSyntax.Kind.DIRECT_PAREN
                    && initializer.kind() != InitializerSyntax.Kind.DIRECT_LIST) {
                throw new IllegalArgumentException("A construction expression requires direct initialization");
            }

            this.type = type;
            this.initializer = initializer;
            this.typeRange = typeRange;
            this.range = range;
        }

        public CrakenType type() { return type; }
        public InitializerSyntax initializer() { return initializer; }
        public SourceRange typeRange() { return typeRange; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ConstructionExpr that)) return false;
            return Objects.equals(type, that.type)
                    && Objects.equals(initializer, that.initializer)
                    && Objects.equals(typeRange, that.typeRange)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(initializer);
            result = 31 * result + Objects.hashCode(typeRange);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "ConstructionExpr[type=" + type + ", initializer=" + initializer + ", typeRange=" + typeRange + ", range=" + range + "]";
        }
    }

    /** Source-only explicit destructor invocation, never an ordinary member field access. */
    static final class DestructorCallExpr extends AbstractAstNode implements Expression {
        private final Expression receiver;
        private final CrakenType ownerType;
        private final QualifiedName destructorName;
        private final boolean viaPointer;
        private final SourceRange nameRange;
        private final SourceRange range;

        @AstNodeConstructor({"receiver", "ownerType", "destructorName", "viaPointer", "nameRange", "range"})
        public DestructorCallExpr(Expression receiver, CrakenType ownerType, QualifiedName destructorName, boolean viaPointer, SourceRange nameRange, SourceRange range) {
            Objects.requireNonNull(receiver, "receiver");
            Objects.requireNonNull(destructorName, "destructorName");
            Objects.requireNonNull(nameRange, "nameRange");
            Objects.requireNonNull(range, "range");
            // ownerType is the surrounding-scope candidate, if any. Binding must also look up
            // the injected class name of receiver; N::A* p can legally call p->~A() outside N.

            this.receiver = receiver;
            this.ownerType = ownerType;
            this.destructorName = destructorName;
            this.viaPointer = viaPointer;
            this.nameRange = nameRange;
            this.range = range;
        }

        public Expression receiver() { return receiver; }
        public CrakenType ownerType() { return ownerType; }
        public QualifiedName destructorName() { return destructorName; }
        public boolean viaPointer() { return viaPointer; }
        public SourceRange nameRange() { return nameRange; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DestructorCallExpr that)) return false;
            return Objects.equals(receiver, that.receiver)
                    && Objects.equals(ownerType, that.ownerType)
                    && Objects.equals(destructorName, that.destructorName)
                    && viaPointer == that.viaPointer
                    && Objects.equals(nameRange, that.nameRange)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(receiver);
            result = 31 * result + Objects.hashCode(ownerType);
            result = 31 * result + Objects.hashCode(destructorName);
            result = 31 * result + Boolean.hashCode(viaPointer);
            result = 31 * result + Objects.hashCode(nameRange);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "DestructorCallExpr[receiver=" + receiver + ", ownerType=" + ownerType + ", destructorName=" + destructorName + ", viaPointer=" + viaPointer + ", nameRange=" + nameRange + ", range=" + range + "]";
        }
    }

    /** Source-only initialization grammar; binding must consume it before core expression analysis. */
    static final class InitializerSyntax extends AbstractAstNode implements Expression {
        private final Kind kind;
        private final List<Expression> arguments;
        private final SourceRange range;

        public enum Kind { DEFAULT, COPY, DIRECT_PAREN, DIRECT_LIST, COPY_LIST }

        @AstNodeConstructor({"kind", "arguments", "range"})
        public InitializerSyntax(Kind kind, List<Expression> arguments, SourceRange range) {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(range, "range");
            arguments = List.copyOf(arguments);
            if (kind == Kind.DEFAULT && !arguments.isEmpty()) {
                throw new IllegalArgumentException("Default initialization has no initializer arguments");
            }
            if (kind == Kind.COPY && arguments.size() != 1) {
                throw new IllegalArgumentException("Copy initialization requires one expression");
            }

            this.kind = kind;
            this.arguments = arguments;
            this.range = range;
        }

        /** The old execution view must share the source operands rather than copying or rebinding them. */
        public boolean isCompatibilityProjection(Expression expression) {
            // Only newly introduced direct forms need the current binder's explicit execution guard.
            if (expression == this) return kind == Kind.DIRECT_PAREN || kind == Kind.DIRECT_LIST;
            return switch (kind) {
                case DEFAULT -> expression == null;
                case COPY -> expression == arguments.getFirst();
                case DIRECT_PAREN -> arguments.size() == 1 && expression instanceof Expression.GroupingExpr group
                        && group.expression() == arguments.getFirst();
                case DIRECT_LIST, COPY_LIST -> expression instanceof Expression.AggregateInitExpr aggregate
                        && sameObjects(arguments, aggregate.values());
            };
        }

        private static boolean sameObjects(List<Expression> first, List<Expression> second) {
            if (first.size() != second.size()) return false;
            for (int index = 0; index < first.size(); index++) if (first.get(index) != second.get(index)) return false;
            return true;
        }

        public Kind kind() { return kind; }
        public List<Expression> arguments() { return arguments; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InitializerSyntax that)) return false;
            return Objects.equals(kind, that.kind)
                    && Objects.equals(arguments, that.arguments)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(kind);
            result = 31 * result + Objects.hashCode(arguments);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "InitializerSyntax[kind=" + kind + ", arguments=" + arguments + ", range=" + range + "]";
        }
    }

    /** Source closure expression. Capture storage is assigned during lexical binding. */
    static final class LambdaExpr extends AbstractAstNode implements Expression {
        private final CaptureDefault captureDefault;
        private final List<Capture> captures;
        private final List<Declaration.Parameter> parameters;
        private final boolean variadic;
        private final boolean mutable;
        private final CrakenType returnType;
        private final Statement.BlockStmt body;
        private final SourceRange range;
        private final CrakenType.ExceptionSpecification exceptionSpecification;
        private final boolean constexprSpecifier;

        public LambdaExpr(CaptureDefault captureDefault,List<Capture> captures,List<Declaration.Parameter> parameters,boolean variadic,boolean mutable,CrakenType returnType,Statement.BlockStmt body,SourceRange range,CrakenType.ExceptionSpecification exceptionSpecification){this(captureDefault,captures,parameters,variadic,mutable,returnType,body,range,exceptionSpecification,false);}
        public LambdaExpr(CaptureDefault captureDefault,List<Capture> captures,List<Declaration.Parameter> parameters,
                             boolean variadic,boolean mutable,CrakenType returnType,Statement.BlockStmt body,SourceRange range) {
            this(captureDefault,captures,parameters,variadic,mutable,returnType,body,range,CrakenType.ExceptionSpecification.UNSPECIFIED);
        }
        public enum CaptureDefault { NONE, COPY, REFERENCE }
        public enum CaptureKind { COPY, REFERENCE, THIS, THIS_COPY }
        public static final class Capture extends AbstractAstNode implements AstNode {
            private final String name;
            private final CaptureKind kind;
            private final InitializerSyntax initializer;
            private final SourceRange range;

            @AstNodeConstructor({"name", "kind", "initializer", "range"})
            public Capture(String name, CaptureKind kind, InitializerSyntax initializer, SourceRange range) {Objects.requireNonNull(name);Objects.requireNonNull(kind);Objects.requireNonNull(range);
                this.name = name;
                this.kind = kind;
                this.initializer = initializer;
                this.range = range;
            }

            public String name() { return name; }
            public CaptureKind kind() { return kind; }
            public InitializerSyntax initializer() { return initializer; }
            public SourceRange range() { return range; }

            @Override public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Capture that)) return false;
                return Objects.equals(name, that.name)
                        && Objects.equals(kind, that.kind)
                        && Objects.equals(initializer, that.initializer)
                        && Objects.equals(range, that.range);
            }

            @Override public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(name);
                result = 31 * result + Objects.hashCode(kind);
                result = 31 * result + Objects.hashCode(initializer);
                result = 31 * result + Objects.hashCode(range);
                return result;
            }

            @Override public String toString() {
                return "Capture[name=" + name + ", kind=" + kind + ", initializer=" + initializer + ", range=" + range + "]";
            }
        }
        @AstNodeConstructor({"captureDefault", "captures", "parameters", "variadic", "mutable", "returnType", "body", "range", "exceptionSpecification", "constexprSpecifier"})
        public LambdaExpr(CaptureDefault captureDefault, List<Capture> captures, List<Declaration.Parameter> parameters, boolean variadic, boolean mutable, CrakenType returnType, Statement.BlockStmt body, SourceRange range, CrakenType.ExceptionSpecification exceptionSpecification, boolean constexprSpecifier) {
            Objects.requireNonNull(exceptionSpecification);Objects.requireNonNull(captureDefault);captures=List.copyOf(captures);parameters=List.copyOf(parameters);
            Objects.requireNonNull(returnType);Objects.requireNonNull(body);Objects.requireNonNull(range);

            this.captureDefault = captureDefault;
            this.captures = captures;
            this.parameters = parameters;
            this.variadic = variadic;
            this.mutable = mutable;
            this.returnType = returnType;
            this.body = body;
            this.range = range;
            this.exceptionSpecification = exceptionSpecification;
            this.constexprSpecifier = constexprSpecifier;
        }

        public CaptureDefault captureDefault() { return captureDefault; }
        public List<Capture> captures() { return captures; }
        public List<Declaration.Parameter> parameters() { return parameters; }
        public boolean variadic() { return variadic; }
        public boolean mutable() { return mutable; }
        public CrakenType returnType() { return returnType; }
        public Statement.BlockStmt body() { return body; }
        public SourceRange range() { return range; }
        public CrakenType.ExceptionSpecification exceptionSpecification() { return exceptionSpecification; }
        public boolean constexprSpecifier() { return constexprSpecifier; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LambdaExpr that)) return false;
            return Objects.equals(captureDefault, that.captureDefault)
                    && Objects.equals(captures, that.captures)
                    && Objects.equals(parameters, that.parameters)
                    && variadic == that.variadic
                    && mutable == that.mutable
                    && Objects.equals(returnType, that.returnType)
                    && Objects.equals(body, that.body)
                    && Objects.equals(range, that.range)
                    && Objects.equals(exceptionSpecification, that.exceptionSpecification)
                    && constexprSpecifier == that.constexprSpecifier;
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(captureDefault);
            result = 31 * result + Objects.hashCode(captures);
            result = 31 * result + Objects.hashCode(parameters);
            result = 31 * result + Boolean.hashCode(variadic);
            result = 31 * result + Boolean.hashCode(mutable);
            result = 31 * result + Objects.hashCode(returnType);
            result = 31 * result + Objects.hashCode(body);
            result = 31 * result + Objects.hashCode(range);
            result = 31 * result + Objects.hashCode(exceptionSpecification);
            result = 31 * result + Boolean.hashCode(constexprSpecifier);
            return result;
        }

        @Override public String toString() {
            return "LambdaExpr[captureDefault=" + captureDefault + ", captures=" + captures + ", parameters=" + parameters + ", variadic=" + variadic + ", mutable=" + mutable + ", returnType=" + returnType + ", body=" + body + ", range=" + range + ", exceptionSpecification=" + exceptionSpecification + ", constexprSpecifier=" + constexprSpecifier + "]";
        }
    }

    /** Source-only new expression. Empty placement arguments select allocating new. */
    static final class PlacementNewExpr extends AbstractAstNode implements Expression {
        private final CrakenType type;
        private final List<Expression> placementArguments;
        private final InitializerSyntax initializer;
        private final boolean global;
        private final Expression arrayBound;
        private final SourceRange typeRange;
        private final SourceRange range;

        @AstNodeConstructor({"type", "placementArguments", "initializer", "global", "arrayBound", "typeRange", "range"})
        public PlacementNewExpr(CrakenType type, List<Expression> placementArguments, InitializerSyntax initializer, boolean global, Expression arrayBound, SourceRange typeRange, SourceRange range) {
            Objects.requireNonNull(type, "type");
            placementArguments = List.copyOf(placementArguments);
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(typeRange, "typeRange");
            Objects.requireNonNull(range, "range");

            this.type = type;
            this.placementArguments = placementArguments;
            this.initializer = initializer;
            this.global = global;
            this.arrayBound = arrayBound;
            this.typeRange = typeRange;
            this.range = range;
        }

        public CrakenType type() { return type; }
        public List<Expression> placementArguments() { return placementArguments; }
        public InitializerSyntax initializer() { return initializer; }
        public boolean global() { return global; }
        public Expression arrayBound() { return arrayBound; }
        public SourceRange typeRange() { return typeRange; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PlacementNewExpr that)) return false;
            return Objects.equals(type, that.type)
                    && Objects.equals(placementArguments, that.placementArguments)
                    && Objects.equals(initializer, that.initializer)
                    && global == that.global
                    && Objects.equals(arrayBound, that.arrayBound)
                    && Objects.equals(typeRange, that.typeRange)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(placementArguments);
            result = 31 * result + Objects.hashCode(initializer);
            result = 31 * result + Boolean.hashCode(global);
            result = 31 * result + Objects.hashCode(arrayBound);
            result = 31 * result + Objects.hashCode(typeRange);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "PlacementNewExpr[type=" + type + ", placementArguments=" + placementArguments + ", initializer=" + initializer + ", global=" + global + ", arrayBound=" + arrayBound + ", typeRange=" + typeRange + ", range=" + range + "]";
        }
    }

    static final class DeleteExpr extends AbstractAstNode implements Expression {
        private final Expression operand;
        private final boolean array;
        private final boolean global;
        private final SourceRange range;

        @AstNodeConstructor({"operand", "array", "global", "range"})
        public DeleteExpr(Expression operand, boolean array, boolean global, SourceRange range) { Objects.requireNonNull(operand); Objects.requireNonNull(range);
            this.operand = operand;
            this.array = array;
            this.global = global;
            this.range = range;
        }

        public Expression operand() { return operand; }
        public boolean array() { return array; }
        public boolean global() { return global; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DeleteExpr that)) return false;
            return Objects.equals(operand, that.operand)
                    && array == that.array
                    && global == that.global
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(operand);
            result = 31 * result + Boolean.hashCode(array);
            result = 31 * result + Boolean.hashCode(global);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "DeleteExpr[operand=" + operand + ", array=" + array + ", global=" + global + ", range=" + range + "]";
        }
    }

    /** The operand is checked for validity but never evaluated or odr-used. */
    static final class NoexceptExpr extends AbstractAstNode implements Expression {
        private final Expression operand;
        private final SourceRange range;

        @AstNodeConstructor({"operand", "range"})
        public NoexceptExpr(Expression operand, SourceRange range) { Objects.requireNonNull(operand); Objects.requireNonNull(range);
            this.operand = operand;
            this.range = range;
        }

        public Expression operand() { return operand; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NoexceptExpr that)) return false;
            return Objects.equals(operand, that.operand)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(operand);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "NoexceptExpr[operand=" + operand + ", range=" + range + "]";
        }
    }

    /** Source-only pack syntax, consumed by template substitution. */
    static final class PackExpansionExpr extends AbstractAstNode implements Expression {
        private final Expression pattern;
        private final SourceRange range;

        @AstNodeConstructor({"pattern", "range"})
        public PackExpansionExpr(Expression pattern, SourceRange range) { Objects.requireNonNull(pattern); Objects.requireNonNull(range);
            this.pattern = pattern;
            this.range = range;
        }

        public Expression pattern() { return pattern; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PackExpansionExpr that)) return false;
            return Objects.equals(pattern, that.pattern)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(pattern);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "PackExpansionExpr[pattern=" + pattern + ", range=" + range + "]";
        }
    }

    /** Source-only pack syntax, consumed by template substitution. */
    static final class SizeofPackExpr extends AbstractAstNode implements Expression {
        private final String name;
        private final SourceRange range;

        @AstNodeConstructor({"name", "range"})
        public SizeofPackExpr(String name, SourceRange range) { Objects.requireNonNull(name); Objects.requireNonNull(range);
            this.name = name;
            this.range = range;
        }

        public String name() { return name; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SizeofPackExpr that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "SizeofPackExpr[name=" + name + ", range=" + range + "]";
        }
    }

    /** Explicit template arguments on a function or member reference. */
    static final class TemplateIdExpr extends AbstractAstNode implements Expression {
        private final Expression target;
        private final List<TemplateArgument> arguments;
        private final SourceRange range;

        @AstNodeConstructor({"target", "arguments", "range"})
        public TemplateIdExpr(Expression target, List<TemplateArgument> arguments, SourceRange range) {Objects.requireNonNull(target);arguments=List.copyOf(arguments);Objects.requireNonNull(range);
            this.target = target;
            this.arguments = arguments;
            this.range = range;
        }

        public Expression target() { return target; }
        public List<TemplateArgument> arguments() { return arguments; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TemplateIdExpr that)) return false;
            return Objects.equals(target, that.target)
                    && Objects.equals(arguments, that.arguments)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(target);
            result = 31 * result + Objects.hashCode(arguments);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "TemplateIdExpr[target=" + target + ", arguments=" + arguments + ", range=" + range + "]";
        }
    }

    /** Bound identity of a non-type template parameter; it never reaches core lowering. */
    static final class TemplateValueExpr extends AbstractAstNode implements Expression {
        private final CrakenType.TemplateParameterType parameter;
        private final CrakenType valueType;
        private final SourceRange range;

        @AstNodeConstructor({"parameter", "valueType", "range"})
        public TemplateValueExpr(CrakenType.TemplateParameterType parameter, CrakenType valueType, SourceRange range) { Objects.requireNonNull(parameter); Objects.requireNonNull(valueType); Objects.requireNonNull(range);
            this.parameter = parameter;
            this.valueType = valueType;
            this.range = range;
        }

        public CrakenType.TemplateParameterType parameter() { return parameter; }
        public CrakenType valueType() { return valueType; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TemplateValueExpr that)) return false;
            return Objects.equals(parameter, that.parameter)
                    && Objects.equals(valueType, that.valueType)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(parameter);
            result = 31 * result + Objects.hashCode(valueType);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "TemplateValueExpr[parameter=" + parameter + ", valueType=" + valueType + ", range=" + range + "]";
        }
    }

    /** A structured type-id qualifier, including template arguments, on a value member name. */
    static final class TypeMemberExpr extends AbstractAstNode implements Expression {
        private final CrakenType ownerType;
        private final String memberName;
        private final SourceRange nameRange;
        private final SourceRange range;

        @AstNodeConstructor({"ownerType", "memberName", "nameRange", "range"})
        public TypeMemberExpr(CrakenType ownerType, String memberName, SourceRange nameRange, SourceRange range) {
            Objects.requireNonNull(ownerType, "ownerType");
            Objects.requireNonNull(memberName, "memberName");
            Objects.requireNonNull(nameRange, "nameRange");
            Objects.requireNonNull(range, "range");

            this.ownerType = ownerType;
            this.memberName = memberName;
            this.nameRange = nameRange;
            this.range = range;
        }

        public CrakenType ownerType() { return ownerType; }
        public String memberName() { return memberName; }
        public SourceRange nameRange() { return nameRange; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TypeMemberExpr that)) return false;
            return Objects.equals(ownerType, that.ownerType)
                    && Objects.equals(memberName, that.memberName)
                    && Objects.equals(nameRange, that.nameRange)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(ownerType);
            result = 31 * result + Objects.hashCode(memberName);
            result = 31 * result + Objects.hashCode(nameRange);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "TypeMemberExpr[ownerType=" + ownerType + ", memberName=" + memberName + ", nameRange=" + nameRange + ", range=" + range + "]";
        }
    }

    /** Unevaluated compiler type query. Types remain source types until template substitution/binding. */
    static final class TypeQueryExpr extends AbstractAstNode implements Expression {
        private final Kind kind;
        private final List<TypeArgument> arguments;
        private final SourceRange nameRange;
        private final SourceRange range;

        public enum Kind {
            CONSTRUCTIBLE("__is_constructible"), ASSIGNABLE("__is_assignable"), CONVERTIBLE("__is_convertible");
            private final String spelling;
            Kind(String spelling) { this.spelling = spelling; }
            public String spelling() { return spelling; }
            public static Kind fromSpelling(String text) {
                for (Kind kind : values()) if (kind.spelling.equals(text)) return kind;
                return null;
            }
            public boolean acceptsArity(int count) { return this == CONSTRUCTIBLE ? count >= 1 : count == 2; }
        }

        public static final class TypeArgument extends AbstractAstNode implements AstNode {
            private final CrakenType type;
            private final boolean packExpansion;
            private final SourceRange range;

            @AstNodeConstructor({"type", "packExpansion", "range"})
            public TypeArgument(CrakenType type, boolean packExpansion, SourceRange range) { Objects.requireNonNull(type); Objects.requireNonNull(range);
                this.type = type;
                this.packExpansion = packExpansion;
                this.range = range;
            }
            public TypeArgument(CrakenType type, SourceRange range) { this(type, false, range); }

            public CrakenType type() { return type; }
            public boolean packExpansion() { return packExpansion; }
            public SourceRange range() { return range; }

            @Override public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof TypeArgument that)) return false;
                return Objects.equals(type, that.type)
                        && packExpansion == that.packExpansion
                        && Objects.equals(range, that.range);
            }

            @Override public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(type);
                result = 31 * result + Boolean.hashCode(packExpansion);
                result = 31 * result + Objects.hashCode(range);
                return result;
            }

            @Override public String toString() {
                return "TypeArgument[type=" + type + ", packExpansion=" + packExpansion + ", range=" + range + "]";
            }
        }

        @AstNodeConstructor({"kind", "arguments", "nameRange", "range"})
        public TypeQueryExpr(Kind kind, List<TypeArgument> arguments, SourceRange nameRange, SourceRange range) {
            Objects.requireNonNull(kind); Objects.requireNonNull(nameRange); Objects.requireNonNull(range);
            arguments = List.copyOf(arguments);
            if (arguments.stream().noneMatch(TypeArgument::packExpansion) && !kind.acceptsArity(arguments.size())) throw new IllegalArgumentException("Invalid type-query arity");

            this.kind = kind;
            this.arguments = arguments;
            this.nameRange = nameRange;
            this.range = range;
        }

        public Kind kind() { return kind; }
        public List<TypeArgument> arguments() { return arguments; }
        public SourceRange nameRange() { return nameRange; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TypeQueryExpr that)) return false;
            return Objects.equals(kind, that.kind)
                    && Objects.equals(arguments, that.arguments)
                    && Objects.equals(nameRange, that.nameRange)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(kind);
            result = 31 * result + Objects.hashCode(arguments);
            result = 31 * result + Objects.hashCode(nameRange);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "TypeQueryExpr[kind=" + kind + ", arguments=" + arguments + ", nameRange=" + nameRange + ", range=" + range + "]";
        }
    }
}
