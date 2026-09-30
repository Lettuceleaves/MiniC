package minic.compiler.parser.node;

import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Direct AST children in source order, without names or types being interpreted. */
public final class AstChildren {
    private AstChildren() {}

    public static List<? extends AstNode> of(AstNode node) {
        return switch (node) {
            case Program n -> n.declarations();
            case NamespaceDecl n -> n.declarations();
            case FunctionDecl n -> present(n.body());
            case GlobalVarDecl n -> present(n.initializer());
            case BlockStmt n -> n.statements();
            case VarDeclStmt n -> present(n.initializer());
            case ReturnStmt n -> present(n.expression());
            case ExprStmt n -> present(n.expression());
            case IfStmt n -> present(n.condition(), n.thenBranch(), n.elseBranch());
            case WhileStmt n -> present(n.condition(), n.body());
            case DoWhileStmt n -> present(n.body(), n.condition());
            case ForStmt n -> present(n.initializer(), n.condition(), n.step(), n.body());
            case SwitchStmt n -> prepend(n.selector(), n.cases());
            case SwitchCase n -> prepend(n.value(), n.statements());
            case AssignmentExpr n -> present(n.target(), n.value());
            case BinaryExpr n -> present(n.left(), n.right());
            case ConditionalExpr n -> present(n.condition(), n.thenExpression(), n.elseExpression());
            case CastExpr n -> present(n.operand());
            case CommaExpr n -> n.expressions();
            case GroupingExpr n -> present(n.expression());
            case IndexExpr n -> present(n.target(), n.index());
            case FieldAccessExpr n -> present(n.target());
            case UnaryExpr n -> present(n.operand());
            case PostfixUpdateExpr n -> present(n.target());
            case SizeofExpr n -> present(n.expression());
            case AlignofExpr n -> present(n.expression());
            case CallExpr n -> prepend(n.callee(), n.arguments());
            case VaStartExpr n -> present(n.list(), n.lastParameter());
            case VaArgExpr n -> present(n.list());
            case VaCopyExpr n -> present(n.destination(), n.source());
            case VaEndExpr n -> present(n.list());
            case AggregateInitExpr n -> n.values();
            case DesignatedInitExpr n -> present(n.value());
            default -> List.of();
        };
    }

    private static List<AstNode> present(AstNode... nodes) {
        return Arrays.stream(nodes).filter(Objects::nonNull).toList();
    }

    private static List<AstNode> prepend(AstNode first, List<? extends AstNode> rest) {
        var nodes = new ArrayList<AstNode>();
        if (first != null) nodes.add(first);
        nodes.addAll(rest);
        return List.copyOf(nodes);
    }
}
