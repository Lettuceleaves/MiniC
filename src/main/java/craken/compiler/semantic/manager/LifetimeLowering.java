package craken.compiler.semantic.manager;

import craken.SourceRange;
import craken.compiler.lexer.token.TokenType;
import craken.compiler.parser.node.AstNode;
import craken.compiler.parser.node.Expression.CleanupExpr;
import craken.compiler.parser.node.Expression;
import craken.compiler.parser.node.Expression.*;
import craken.compiler.parser.node.Statement.VarDeclStmt;
import craken.compiler.type.CrakenType;

import java.util.ArrayList;
import java.util.List;

/** Registers only completed constructions; the nullable address slots also handle conditional evaluation. */
final class LifetimeLowering {
    interface Context {
        CrakenType type(Expression expression);
        default Expression staticMaterialize(MaterializeExpr temporary) { return null; }
        boolean needsDestruction(CrakenType type);
        Expression destroy(CrakenType type, Expression address, SourceRange range);
        Expression copy(CrakenType type, Expression value, SourceRange range);
        String freshName(String display);
        Expression remap(Expression source, Expression result);
    }

    record Result(Expression expression, List<VarDeclStmt> declarations, Expression scopeCleanup) { }
    private record Registration(String name, CrakenType type, boolean scoped, SourceRange range) { }

    private final Context context;
    private final AstNode sourceOwner;
    private final List<Registration> registrations = new ArrayList<>();

    LifetimeLowering(Context context, AstNode sourceOwner) {
        this.context = context;
        this.sourceOwner = sourceOwner;
    }

    Result lower(Expression expression, boolean resultOwned) {
        if (expression == null) return new Result(null, List.of(), null);
        Expression value = rewrite(expression, resultOwned);
        List<Expression> expressionActions = new ArrayList<>();
        List<Expression> scopeActions = new ArrayList<>();
        for (int index = registrations.size() - 1; index >= 0; index--) {
            Registration registration = registrations.get(index);
            Expression address = storedAddress(registration);
            Expression destruction = context.destroy(registration.type, address, registration.range);
            if (destruction == null) continue;
            Expression action = new ConditionalExpr(address, destruction, noOp(registration.range), registration.range);
            (registration.scoped ? scopeActions : expressionActions).add(action);
        }
        if (!expressionActions.isEmpty()) value = new CleanupExpr(value, sequence(expressionActions), expression.range());
        List<VarDeclStmt> declarations = new ArrayList<>();
        for (int index = registrations.size() - 1; index >= 0; index--) {
            Registration registration = registrations.get(index);
            CrakenType pointer = registration.type.pointerTo();
            Expression zero = new CastExpr(pointer, new NullLiteralExpr("nullptr", registration.range), registration.range);
            if (registration.scoped) {
                declarations.addFirst(new VarDeclStmt(registration.name, pointer, zero, registration.range));
            } else {
                Expression slot = new MaterializeExpr(pointer, zero,
                        new TemporaryLifetime(TemporaryLifetime.Kind.FULL_EXPRESSION, sourceOwner), registration.range);
                value = new LetExpr(registration.name, pointer.pointerTo(), slot, value, expression.range());
            }
        }
        return new Result(value, List.copyOf(declarations), scopeActions.isEmpty() ? null : sequence(scopeActions));
    }

    private Expression rewrite(Expression expression, boolean resultOwned) {
        Expression result = switch (expression) {
            case MaterializeExpr temporary -> {
                Expression initializer = rewrite(temporary.initializer(), true);
                MaterializeExpr rewritten = new MaterializeExpr(temporary.type(), initializer, temporary.lifetime(), temporary.range());
                Expression staticAddress = context.staticMaterialize(rewritten);
                if (staticAddress != null) yield staticAddress;
                Expression materialized = rewritten;
                if (context.needsDestruction(temporary.type())) {
                    Registration registration = register(temporary.type(),
                            temporary.lifetime().kind() == TemporaryLifetime.Kind.REFERENCE_SCOPE, temporary.range());
                    materialized = new AssignmentExpr(storedAddress(registration), TokenType.EQUAL, materialized, temporary.range());
                }
                yield materialized;
            }
            case ObjectInitExpr construction -> {
                Expression body = rewrite(construction.body(), false);
                if (!resultOwned && context.needsDestruction(construction.type())) {
                    Registration registration = register(construction.type(), false, construction.range());
                    Expression address = new NameExpr(construction.destinationName(), construction.range());
                    Expression remember = new AssignmentExpr(storedAddress(registration), TokenType.EQUAL, address, construction.range());
                    body = new CommaExpr(List.of(body, new CastExpr(CrakenType.VOID, remember, construction.range())), construction.range());
                }
                yield new ObjectInitExpr(construction.type(), construction.destinationName(), body, construction.range());
            }
            case CallExpr call -> {
                Expression callee = rewrite(call.callee(), false);
                List<Expression> arguments = new ArrayList<>(java.util.Collections.nCopies(call.arguments().size(), null));
                for (int index : call.argumentEvaluationOrder()) {
                    Expression argument = call.arguments().get(index);
                    CrakenType type = context.type(argument);
                    if (type != null && type.isStruct() && context.needsDestruction(type)
                            && !ObjectInitExpr.occursInResultOf(argument)) {
                        argument = context.copy(type, argument, argument.range());
                    }
                    arguments.set(index, rewrite(argument, false));
                }
                yield new CallExpr(callee, arguments, call.argumentEvaluationOrder(), call.range());
            }
            case InitializeExpr initialization -> new InitializeExpr(rewrite(initialization.target(), false),
                    rewrite(initialization.value(), true), initialization.range());
            case LetExpr capture -> new LetExpr(capture.name(), capture.type(), rewrite(capture.initializer(), false),
                    rewrite(capture.body(), resultOwned), capture.range());
            case GroupingExpr grouping -> new GroupingExpr(rewrite(grouping.expression(), resultOwned), grouping.range());
            case CommaExpr comma -> {
                List<Expression> items = new ArrayList<>();
                for (int index = 0; index < comma.expressions().size(); index++) {
                    items.add(rewrite(comma.expressions().get(index), resultOwned && index == comma.expressions().size() - 1));
                }
                yield new CommaExpr(items, comma.range());
            }
            case ConditionalExpr conditional -> new ConditionalExpr(rewrite(conditional.condition(), false),
                    rewrite(conditional.thenExpression(), resultOwned), rewrite(conditional.elseExpression(), resultOwned), conditional.range());
            case AssignmentExpr assignment -> new AssignmentExpr(rewrite(assignment.target(), false), assignment.operator(),
                    rewrite(assignment.value(), false), assignment.range());
            case BinaryExpr binary -> new BinaryExpr(rewrite(binary.left(), false), binary.operator(), rewrite(binary.right(), false), binary.range());
            case UnaryExpr unary -> new UnaryExpr(unary.operator(), rewrite(unary.operand(), false), unary.range());
            case PostfixUpdateExpr update -> new PostfixUpdateExpr(rewrite(update.target(), false), update.operator(), update.range());
            case CastExpr cast -> new CastExpr(cast.targetType(), rewrite(cast.operand(), resultOwned), cast.range());
            case FieldAccessExpr field -> new FieldAccessExpr(rewrite(field.target(), false), field.fieldName(), field.viaPointer(), field.range());
            case IndexExpr index -> new IndexExpr(rewrite(index.target(), false), rewrite(index.index(), false), index.range());
            case AggregateInitExpr aggregate -> new AggregateInitExpr(aggregate.values().stream().map(item -> rewrite(item, true)).toList(), aggregate.range());
            case DesignatedInitExpr designated -> new DesignatedInitExpr(designated.designators(), rewrite(designated.value(), resultOwned), designated.range());
            case VaStartExpr start -> new VaStartExpr(rewrite(start.list(), false), rewrite(start.lastParameter(), false), start.range());
            case VaArgExpr argument -> new VaArgExpr(rewrite(argument.list(), false), argument.requestedType(), argument.range());
            case VaCopyExpr copy -> new VaCopyExpr(rewrite(copy.destination(), false), rewrite(copy.source(), false), copy.range());
            case VaEndExpr end -> new VaEndExpr(rewrite(end.list(), false), end.range());
            // A sizeof/alignof operand is not executed; existing cleanup boundaries are already bound.
            case SizeofExpr ignored -> expression;
            case AlignofExpr ignored -> expression;
            case CleanupExpr ignored -> expression;
            default -> expression;
        };
        return result == expression ? expression : context.remap(expression, result);
    }

    private Registration register(CrakenType type, boolean scoped, SourceRange range) {
        Registration registration = new Registration(context.freshName("temporary_lifetime"), type, scoped, range);
        registrations.add(registration);
        return registration;
    }

    private Expression storedAddress(Registration registration) {
        Expression name = new NameExpr(registration.name, registration.range);
        return registration.scoped ? name : new UnaryExpr(TokenType.STAR, name, registration.range);
    }

    private static Expression sequence(List<Expression> expressions) {
        return expressions.size() == 1 ? expressions.getFirst() : new CommaExpr(expressions, expressions.getFirst().range());
    }

    private static Expression noOp(SourceRange range) {
        return new CastExpr(CrakenType.VOID, new IntegerLiteralExpr(0, "0", range), range);
    }
}
