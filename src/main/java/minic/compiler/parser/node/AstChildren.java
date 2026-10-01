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
            case ClassTemplateDecl n -> prepend(n.record(), n.parameters());
            case StructDecl n -> n.cppInfo() == null ? n.fields() : n.cppInfo().members();
            case FieldMember n -> present(n.field(), n.defaultInitializer());
            case MethodMember n -> present(n.method());
            case ConstructorMember n -> {
                var children = new ArrayList<AstNode>(n.parameters());
                children.addAll(n.initializers());
                if (n.body() != null) children.add(n.body());
                yield List.copyOf(children);
            }
            case DestructorMember n -> present(n.body());
            case MemberInitializer n -> present(n.initializer());
            case OutOfLineConstructorDecl n -> present(n.constructor());
            case OutOfLineDestructorDecl n -> present(n.destructor());
            case OutOfLineMethodDecl n -> present(n.method());
            case FunctionDecl n -> present(n.body());
            case GlobalVarDecl n -> present(n.cppInitializer() != null ? n.cppInitializer() : n.initializer());
            case BlockStmt n -> n.statements();
            case CleanupScopeStmt n -> present(n.body(), n.cleanup());
            case VarDeclStmt n -> present(n.cppInitializer() != null ? n.cppInitializer() : n.initializer());
            case ReturnStmt n -> present(n.expression());
            case ExprStmt n -> present(n.expression());
            case IfStmt n -> present(n.condition(), n.thenBranch(), n.elseBranch());
            case WhileStmt n -> present(n.condition(), n.body());
            case DoWhileStmt n -> present(n.body(), n.condition());
            case ForStmt n -> present(n.initializer(), n.condition(), n.step(), n.body());
            case SwitchStmt n -> prepend(n.selector(), n.cases());
            case SwitchCase n -> prepend(n.value(), n.statements());
            case AssignmentExpr n -> present(n.target(), n.value());
            case LetExpr n -> present(n.initializer(), n.body());
            case CleanupExpr n -> present(n.value(), n.cleanup());
            case MaterializeExpr n -> present(n.initializer());
            case ObjectInitExpr n -> present(n.body());
            case InitializeExpr n -> present(n.target(), n.value());
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
            case CppInitializer n -> n.arguments();
            case CppConstructionExpr n -> present(n.initializer());
            case CppDestructorCallExpr n -> present(n.receiver());
            case CppNewExpr n -> {
                var children = new ArrayList<AstNode>(n.placementArguments());
                children.add(n.initializer());
                yield List.copyOf(children);
            }
            case DesignatedInitExpr n -> present(n.value());
            default -> List.of();
        };
    }

    /**
     * Locates source-only C++ syntax before a core-only stage accepts an AST. Flat Program
     * indexes are checked as well as source order, so malformed manually built indexes cannot
     * hide unsupported nodes. Identity deduplication does not collapse distinct source nodes.
     */
    public static AstNode firstCppSyntax(AstNode root) {
        return firstSourceSyntax(root, false);
    }

    /** Locates a type-bearing node with a reference, including nested callable signatures. */
    public static AstNode firstReferenceSyntax(AstNode root) {
        return firstSourceSyntax(root, true);
    }

    private static AstNode firstSourceSyntax(AstNode root, boolean onlyReferences) {
        var pending = new java.util.ArrayDeque<AstNode>();
        var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<AstNode, Boolean>());
        pending.add(Objects.requireNonNull(root, "root"));
        while (!pending.isEmpty()) {
            AstNode node = pending.removeFirst();
            if (!visited.add(node)) continue;
            if (!onlyReferences && (node instanceof ClassTemplateDecl || node instanceof NamespaceDecl || node instanceof UsingDecl || node instanceof OutOfLineMethodDecl
                    || node instanceof QualifiedNameExpr || node instanceof ThisExpr
                    || node instanceof CppDestructorCallExpr || node instanceof CppInitializer || node instanceof CppConstructionExpr || node instanceof CppNewExpr || node instanceof ConstructorMember
                    || node instanceof OutOfLineConstructorDecl || node instanceof MemberInitializer
                    || node instanceof DestructorMember || node instanceof OutOfLineDestructorDecl
                    || node instanceof FunctionDecl function && (function.operatorName() != null || function.conversionName() != null)
                    || node instanceof StructDecl record && record.cppInfo() != null)) return node;
            AstNode reference = sourceTypeOwner(node, onlyReferences);
            if (reference != null) return reference;
            pending.addAll(of(node));
            if (node instanceof Program program) {
                pending.addAll(program.structs());
                pending.addAll(program.enums());
                pending.addAll(program.typedefs());
                pending.addAll(program.globals());
                pending.addAll(program.functions());
            }
        }
        return null;
    }

    private static AstNode sourceTypeOwner(AstNode node, boolean onlyReferences) {
        minic.compiler.type.MiniType type = switch (node) {
            case FunctionDecl n -> n.returnType();
            case Parameter n -> n.type();
            case GlobalVarDecl n -> n.type();
            case StructField n -> n.type();
            case TypedefDecl n -> n.type();
            case VarDeclStmt n -> n.type();
            case TypedefStmt n -> n.type();
            case CastExpr n -> n.targetType();
            case CppConstructionExpr n -> n.type();
            case CppDestructorCallExpr n -> n.ownerType();
            case LetExpr n -> n.type();
            case MaterializeExpr n -> n.type();
            case ObjectInitExpr n -> n.type();
            case SizeofExpr n -> n.queriedType();
            case AlignofExpr n -> n.queriedType();
            case VaArgExpr n -> n.requestedType();
            case AlignmentSpec n -> n.type();
            default -> null;
        };
        if (sourceType(type, onlyReferences)) return node;
        // Parameters and alignment operands are intentionally not executable AST children.
        if (node instanceof FunctionDecl function) {
            for (Parameter parameter : function.parameters()) if (sourceType(parameter.type(), onlyReferences)) return parameter;
        }
        List<AlignmentSpec> alignments = switch (node) {
            case GlobalVarDecl n -> n.alignmentSpecs();
            case StructField n -> n.alignmentSpecs();
            case VarDeclStmt n -> n.alignmentSpecs();
            default -> List.of();
        };
        for (AlignmentSpec alignment : alignments) {
            if (sourceType(alignment.type(), onlyReferences)) return alignment;
        }
        return null;
    }

    private static boolean sourceType(minic.compiler.type.MiniType type, boolean onlyReferences) {
        return type != null && (type.containsReference() || !onlyReferences && type.containsTemplateType());
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
