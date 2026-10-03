package minic.compiler.parser.node;

import minic.compiler.lexer.token.TokenType;
import minic.compiler.type.MiniType;
import minic.SourceRange;
import minic.compiler.type.TemplateArgument;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 表达式节点及其全部具体类型。
 */
public interface Expression extends AstNode {

    /** Compiler-only first initialization of existing storage; ordinary assignment stays cv-checked. */
    record InitializeExpr(Expression target, Expression value, SourceRange range) implements Expression {
        public InitializeExpr {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Compiler-only construction in a supplied destination; the capture is an immutable pointer value. */
    record ObjectInitExpr(MiniType type, String destinationName, Expression body, SourceRange range) implements Expression {
        public ObjectInitExpr {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(destinationName, "destinationName");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(range, "range");
            if (destinationName.isBlank()) throw new IllegalArgumentException("destination name must not be blank");
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
    record MaterializeExpr(MiniType type, Expression initializer, TemporaryLifetime lifetime, SourceRange range)
            implements Expression {
        public MaterializeExpr {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(lifetime, "lifetime");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Compiler-only, expression-scoped value capture; it does not create an addressable object. */
    record LetExpr(String name, MiniType type, Expression initializer, Expression body, SourceRange range)
            implements Expression {
        public LetExpr {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) throw new IllegalArgumentException("capture name must not be blank");
        }
    }

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

    /** Arguments retain ABI order; evaluation order is a validated permutation, after evaluating the callee. */
    record CallExpr(Expression callee, List<Expression> arguments, List<Integer> argumentEvaluationOrder,
                    SourceRange range) implements Expression {
        public CallExpr(Expression callee, List<Expression> arguments, SourceRange range) {
            this(callee, arguments, java.util.stream.IntStream.range(0, arguments.size()).boxed().toList(), range);
        }
        public CallExpr {
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

    enum LiteralEncoding { ORDINARY, UTF8, UTF16, UTF32 }

    record CharLiteralExpr(int value, LiteralEncoding encoding, String lexeme, SourceRange range) implements Expression {
        public CharLiteralExpr {
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(encoding, "encoding");
            Objects.requireNonNull(range, "range");
        }

        public CharLiteralExpr(char value, String lexeme, SourceRange range) {
            this(value, LiteralEncoding.ORDINARY, lexeme, range);
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

    /** Source receiver expression; no implicit core variable is invented before binding. */
    record ThisExpr(SourceRange range) implements Expression {
        public ThisExpr { Objects.requireNonNull(range, "range"); }
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

    /** A source-spelled qualified reference, deliberately not treated as a direct resolved callee. */
    record QualifiedNameExpr(QualifiedName name) implements Expression {
        public QualifiedNameExpr { Objects.requireNonNull(name, "name"); }
        @Override public SourceRange range() { return name.range(); }
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

    record AlignofExpr(Expression expression, MiniType queriedType, SourceRange range) implements Expression {
        public AlignofExpr {
            if ((expression == null) == (queriedType == null)) {
                throw new IllegalArgumentException("alignof requires exactly one operand");
            }
            Objects.requireNonNull(range, "range");
        }

        public Optional<Expression> expressionOptional() {
            return Optional.ofNullable(expression);
        }

        public Optional<MiniType> queriedTypeOptional() {
            return Optional.ofNullable(queriedType);
        }
    }

    /** Initializes a va_list cursor immediately after the function's final named argument. */
    record VaStartExpr(Expression list, Expression lastParameter, SourceRange range) implements Expression {
        public VaStartExpr {
            Objects.requireNonNull(list, "list");
            Objects.requireNonNull(lastParameter, "lastParameter");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Reads one promoted argument and advances the va_list cursor by one Windows x64 slot. */
    record VaArgExpr(Expression list, MiniType requestedType, SourceRange range) implements Expression {
        public VaArgExpr {
            Objects.requireNonNull(list, "list");
            Objects.requireNonNull(requestedType, "requestedType");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Copies a variadic cursor; both cursors subsequently advance independently. */
    record VaCopyExpr(Expression destination, Expression source, SourceRange range) implements Expression {
        public VaCopyExpr {
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Ends use of a variadic cursor. */
    record VaEndExpr(Expression list, SourceRange range) implements Expression {
        public VaEndExpr {
            Objects.requireNonNull(list, "list");
            Objects.requireNonNull(range, "range");
        }
    }

    record StringLiteralExpr(String value, LiteralEncoding encoding, String lexeme, SourceRange range) implements Expression {
        public StringLiteralExpr {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(encoding, "encoding");
            Objects.requireNonNull(lexeme, "lexeme");
            Objects.requireNonNull(range, "range");
        }

        public StringLiteralExpr(String value, String lexeme, SourceRange range) {
            this(value, LiteralEncoding.ORDINARY, lexeme, range);
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

    /**
     * Internal expression boundary: capture the value (or construct directly at its supplied
     * destination), execute a void cleanup, then yield that value. Reference results are already
     * normalized to pointer values. Registration and lifetime-extension policy belong to the binder.
     */
    record CleanupExpr(Expression value, Expression cleanup, SourceRange range) implements Expression {
        public CleanupExpr {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(cleanup, "cleanup");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Source-only functional conversion or object construction, preserving its written initializer. */
    record ConstructionExpr(MiniType type, InitializerSyntax initializer,
                            SourceRange typeRange, SourceRange range) implements Expression {
        public ConstructionExpr {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(typeRange, "typeRange");
            Objects.requireNonNull(range, "range");
            if (initializer.kind() != InitializerSyntax.Kind.DIRECT_PAREN
                    && initializer.kind() != InitializerSyntax.Kind.DIRECT_LIST) {
                throw new IllegalArgumentException("A construction expression requires direct initialization");
            }
        }
    }

    /** Source-only explicit destructor invocation, never an ordinary member field access. */
    record DestructorCallExpr(Expression receiver, MiniType ownerType, QualifiedName destructorName,
                              boolean viaPointer, SourceRange nameRange, SourceRange range) implements Expression {
        public DestructorCallExpr {
            Objects.requireNonNull(receiver, "receiver");
            Objects.requireNonNull(destructorName, "destructorName");
            Objects.requireNonNull(nameRange, "nameRange");
            Objects.requireNonNull(range, "range");
            // ownerType is the surrounding-scope candidate, if any. Binding must also look up
            // the injected class name of receiver; N::A* p can legally call p->~A() outside N.
        }
    }

    /** Source-only initialization grammar; binding must consume it before core expression analysis. */
    record InitializerSyntax(Kind kind, List<Expression> arguments, SourceRange range) implements Expression {
        public enum Kind { DEFAULT, COPY, DIRECT_PAREN, DIRECT_LIST, COPY_LIST }

        public InitializerSyntax {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(range, "range");
            arguments = List.copyOf(arguments);
            if (kind == Kind.DEFAULT && !arguments.isEmpty()) {
                throw new IllegalArgumentException("Default initialization has no initializer arguments");
            }
            if (kind == Kind.COPY && arguments.size() != 1) {
                throw new IllegalArgumentException("Copy initialization requires one expression");
            }
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
    }

    /** Source closure expression. Capture storage is assigned during lexical binding. */
    record LambdaExpr(CaptureDefault captureDefault,List<Capture> captures,
                      List<Declaration.Parameter> parameters,boolean variadic,boolean mutable,
                      MiniType returnType,Statement.BlockStmt body,SourceRange range,
                      MiniType.ExceptionSpecification exceptionSpecification,boolean constexprSpecifier) implements Expression {
        public LambdaExpr(CaptureDefault captureDefault,List<Capture> captures,List<Declaration.Parameter> parameters,boolean variadic,boolean mutable,MiniType returnType,Statement.BlockStmt body,SourceRange range,MiniType.ExceptionSpecification exceptionSpecification){this(captureDefault,captures,parameters,variadic,mutable,returnType,body,range,exceptionSpecification,false);}
        public LambdaExpr(CaptureDefault captureDefault,List<Capture> captures,List<Declaration.Parameter> parameters,
                             boolean variadic,boolean mutable,MiniType returnType,Statement.BlockStmt body,SourceRange range) {
            this(captureDefault,captures,parameters,variadic,mutable,returnType,body,range,MiniType.ExceptionSpecification.UNSPECIFIED);
        }
        public enum CaptureDefault { NONE, COPY, REFERENCE }
        public enum CaptureKind { COPY, REFERENCE, THIS, THIS_COPY }
        public record Capture(String name,CaptureKind kind,InitializerSyntax initializer,SourceRange range) implements AstNode {
            public Capture {Objects.requireNonNull(name);Objects.requireNonNull(kind);Objects.requireNonNull(range);}
        }
        public LambdaExpr {
            Objects.requireNonNull(exceptionSpecification);Objects.requireNonNull(captureDefault);captures=List.copyOf(captures);parameters=List.copyOf(parameters);
            Objects.requireNonNull(returnType);Objects.requireNonNull(body);Objects.requireNonNull(range);
        }
    }

    /** Source-only placement construction; allocation lookup and object lifetime require binding. */
    record PlacementNewExpr(MiniType type, List<Expression> placementArguments, InitializerSyntax initializer,
                            boolean global, SourceRange typeRange, SourceRange range) implements Expression {
        public PlacementNewExpr {
            Objects.requireNonNull(type, "type");
            placementArguments = List.copyOf(placementArguments);
            if (placementArguments.isEmpty()) throw new IllegalArgumentException("Placement arguments must not be empty");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(typeRange, "typeRange");
            Objects.requireNonNull(range, "range");
        }
    }

    /** The operand is checked for validity but never evaluated or odr-used. */
    record NoexceptExpr(Expression operand,SourceRange range) implements Expression {
        public NoexceptExpr { Objects.requireNonNull(operand); Objects.requireNonNull(range); }
    }

    /** Source-only pack syntax, consumed by template substitution. */
    record PackExpansionExpr(Expression pattern, SourceRange range) implements Expression {
        public PackExpansionExpr { Objects.requireNonNull(pattern); Objects.requireNonNull(range); }
    }

    /** Source-only pack syntax, consumed by template substitution. */
    record SizeofPackExpr(String name, SourceRange range) implements Expression {
        public SizeofPackExpr { Objects.requireNonNull(name); Objects.requireNonNull(range); }
    }

    /** Explicit template arguments on a function or member reference. */
    record TemplateIdExpr(Expression target,List<TemplateArgument> arguments,SourceRange range) implements Expression {
        public TemplateIdExpr {Objects.requireNonNull(target);arguments=List.copyOf(arguments);Objects.requireNonNull(range);}
    }

    /** Bound identity of a non-type template parameter; it never reaches core lowering. */
    record TemplateValueExpr(MiniType.TemplateParameterType parameter, MiniType valueType,
                             SourceRange range) implements Expression {
        public TemplateValueExpr { Objects.requireNonNull(parameter); Objects.requireNonNull(valueType); Objects.requireNonNull(range); }
    }

    /** A structured type-id qualifier, including template arguments, on a value member name. */
    record TypeMemberExpr(MiniType ownerType, String memberName, SourceRange nameRange,
                          SourceRange range) implements Expression {
        public TypeMemberExpr {
            Objects.requireNonNull(ownerType, "ownerType");
            Objects.requireNonNull(memberName, "memberName");
            Objects.requireNonNull(nameRange, "nameRange");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Unevaluated compiler type query. Types remain source types until template substitution/binding. */
    record TypeQueryExpr(Kind kind, List<TypeArgument> arguments,
                         SourceRange nameRange, SourceRange range) implements Expression {
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

        public record TypeArgument(MiniType type, boolean packExpansion, SourceRange range) implements AstNode {
            public TypeArgument { Objects.requireNonNull(type); Objects.requireNonNull(range); }
            public TypeArgument(MiniType type, SourceRange range) { this(type, false, range); }
        }

        public TypeQueryExpr {
            Objects.requireNonNull(kind); Objects.requireNonNull(nameRange); Objects.requireNonNull(range);
            arguments = List.copyOf(arguments);
            if (arguments.stream().noneMatch(TypeArgument::packExpansion) && !kind.acceptsArity(arguments.size())) throw new IllegalArgumentException("Invalid type-query arity");
        }
    }
}
