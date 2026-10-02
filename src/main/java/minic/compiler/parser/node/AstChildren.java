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
        List<? extends AstNode> direct = switch (node) {
            case StaticAssertDecl n -> present(n.condition(),n.message());
            case Program n -> n.declarations();
            case NamespaceDecl n -> n.declarations();
            case DeclGroupDecl n -> n.declarations();
            case InternalLinkageDecl n -> present(n.declaration());
            case ClassTemplateDecl n -> prepend(n.record(), n.parameters());
            case FunctionTemplateDecl n -> {
                var children=new ArrayList<AstNode>();children.add(n.function());children.addAll(n.parameters());
                if(n.specializationArguments()!=null)for(var argument:n.specializationArguments())
                    if(argument instanceof minic.compiler.type.TemplateArgument.Value value)children.add(value.expression());
                yield children;
            }
            case TemplateMemberDefinitionDecl n -> prepend(n.declaration(),n.parameters());
            case TemplateMethodMember n -> prepend(n.method(),n.parameters());
            case TemplateConstructorMember n -> prepend(n.constructor(),n.parameters());
            case TemplateIdExpr n -> prepend(n.target(),n.arguments().stream().filter(minic.compiler.type.TemplateArgument.Value.class::isInstance).map(minic.compiler.type.TemplateArgument.Value.class::cast).map(minic.compiler.type.TemplateArgument.Value::expression).toList());
            case PackExpansionExpr n -> present(n.pattern());
            case SizeofPackExpr n -> List.of();
            case Parameter n -> present(n.defaultValue());
            case ClassTemplateDecl.ValueParameter n -> present(n.defaultValue());
            case StructDecl n -> n.recordInfo() == null ? n.fields() : java.util.stream.Stream.concat(n.recordInfo().bases().stream(),n.recordInfo().members().stream()).toList();
            case StaticFieldMember n -> present(n.declaration());
            case OutOfLineStaticFieldDecl n -> present(n.declaration());
            case FieldMember n -> present(n.field(), n.defaultInitializer());
            case MethodMember n -> present(n.method());
            case MemberTypedef n -> present(n.declaration());
            case ConstructorMember n -> {
                var children = new ArrayList<AstNode>(n.parameters());
                if(n.exceptionSpecification().condition()!=null)children.add(n.exceptionSpecification().condition());
                children.addAll(n.initializers());
                if (n.body() != null) children.add(n.body());
                yield List.copyOf(children);
            }
            case DestructorMember n -> present(n.exceptionSpecification().condition(),n.body());
            case MemberInitializer n -> present(n.initializer());
            case OutOfLineConstructorDecl n -> present(n.constructor());
            case OutOfLineDestructorDecl n -> present(n.destructor());
            case OutOfLineMethodDecl n -> present(n.method());
            case FunctionDecl n -> present(n.exceptionSpecification().condition(),n.body());
            case GlobalVarDecl n -> present(n.initializerSyntax() != null ? n.initializerSyntax() : n.initializer());
            case BlockStmt n -> n.statements();
            case DeclGroupStmt n -> n.statements();
            case CleanupScopeStmt n -> present(n.body(), n.cleanup());
            case VarDeclStmt n -> present(n.initializerSyntax() != null ? n.initializerSyntax() : n.initializer());
            case ReturnStmt n -> present(n.expression());
            case ExprStmt n -> present(n.expression());
            case IfStmt n -> present(n.condition(), n.thenBranch(), n.elseBranch());
            case WhileStmt n -> present(n.condition(), n.body());
            case DoWhileStmt n -> present(n.body(), n.condition());
            case ForStmt n -> present(n.initializer(), n.condition(), n.step(), n.body());
            case RangeForStmt n -> present(n.declaration(), n.initializer(), n.body());
            case StructuredBindingDecl n -> prepend(n.initializer(),n.names());
            case LambdaExpr n -> {var children=new ArrayList<AstNode>(n.captures());children.addAll(n.parameters());if(n.exceptionSpecification().condition()!=null)children.add(n.exceptionSpecification().condition());children.add(n.body());yield List.copyOf(children);}
            case LambdaExpr.Capture n -> present(n.initializer());
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
            case InitializerSyntax n -> n.arguments();
            case ConstructionExpr n -> present(n.initializer());
            case TypeQueryExpr n -> n.arguments();
            case NoexceptExpr n -> present(n.operand());
            case DestructorCallExpr n -> present(n.receiver());
            case PlacementNewExpr n -> {
                var children = new ArrayList<AstNode>(n.placementArguments());
                children.add(n.initializer());
                yield List.copyOf(children);
            }
            case DesignatedInitExpr n -> present(n.value());
            default -> List.of();
        };
        List<AstNode> typed = new ArrayList<>();
        typeExpressions(node, typed);
        if (typed.isEmpty()) return direct;
        typed.addAll(direct);
        return List.copyOf(typed);
    }

    private static void typeExpressions(AstNode node, List<AstNode> result) {
        minic.compiler.type.MiniType type = switch (node) {
            case LambdaExpr n -> n.returnType();
            case StructuredBindingDecl n -> n.type();
            case FunctionDecl n -> n.returnType(); case Parameter n -> n.type();
            case BaseSpecifier n -> n.type();
            case GlobalVarDecl n -> n.type(); case StructField n -> n.type();
            case TypedefDecl n -> n.type(); case VarDeclStmt n -> n.type(); case TypedefStmt n -> n.type();
            case CastExpr n -> n.targetType(); case ConstructionExpr n -> n.type();
            case TypeQueryExpr.TypeArgument n -> n.type();
            case SizeofExpr n -> n.queriedType(); case AlignofExpr n -> n.queriedType();
            default -> null;
        };
        placeholderExpressions(type, result);
        if (node instanceof FunctionDecl function) for (var parameter : function.parameters()) placeholderExpressions(parameter.type(), result);
    }
    private static void placeholderExpressions(minic.compiler.type.MiniType type, List<AstNode> result) {
        if (type == null) return;
        switch (type.unqualified()) {
            case minic.compiler.type.MiniType.DecltypeType query -> result.add(query.expression());
            case minic.compiler.type.MiniType.TrailingReturnType trailing -> placeholderExpressions(trailing.type(), result);
            case minic.compiler.type.MiniType.PointerType pointer -> placeholderExpressions(pointer.pointee(), result);
            case minic.compiler.type.MiniType.ReferenceType reference -> placeholderExpressions(reference.referent(), result);
            case minic.compiler.type.MiniType.ArrayType array -> placeholderExpressions(array.elementType(), result);
            case minic.compiler.type.MiniType.FunctionType function -> {
                if(function.exceptionSpecification().condition()!=null)result.add(function.exceptionSpecification().condition());
                placeholderExpressions(function.returnType(), result);
                function.parameterTypes().forEach(parameter -> placeholderExpressions(parameter, result));
            }
            default -> { }
        }
    }

    /**
     * Locates source-only C++ syntax before a core-only stage accepts an AST. Flat Program
     * indexes are checked as well as source order, so malformed manually built indexes cannot
     * hide unsupported nodes. Identity deduplication does not collapse distinct source nodes.
     */
    public static AstNode firstExtendedSyntax(AstNode root) {
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
            if (!onlyReferences && (node instanceof StaticAssertDecl || node instanceof PackExpansionExpr || node instanceof SizeofPackExpr || node instanceof StructuredBindingDecl || node instanceof LambdaExpr || node instanceof RangeForStmt || node instanceof StaticFieldMember || node instanceof OutOfLineStaticFieldDecl || node instanceof TypeMemberExpr || node instanceof InternalLinkageDecl || node instanceof ClassTemplateDecl || node instanceof NamespaceDecl || node instanceof UsingDecl || node instanceof OutOfLineMethodDecl
                    || node instanceof QualifiedNameExpr || node instanceof ThisExpr
                    || node instanceof NoexceptExpr || node instanceof TypeQueryExpr || node instanceof DestructorCallExpr || node instanceof InitializerSyntax || node instanceof ConstructionExpr || node instanceof PlacementNewExpr || node instanceof ConstructorMember
                    || node instanceof OutOfLineConstructorDecl || node instanceof MemberInitializer
                    || node instanceof DestructorMember || node instanceof OutOfLineDestructorDecl
                    || node instanceof FunctionDecl function && (function.constexprSpecifier() || function.operatorName() != null || function.conversionName() != null || function.definitionKind()!=DefinitionKind.ORDINARY || function.exceptionSpecification().specified())
                    || node instanceof FunctionTemplateDecl || node instanceof TemplateMemberDefinitionDecl || node instanceof TemplateIdExpr
                    || node instanceof TemplateValueExpr
                    || node instanceof GlobalVarDecl global && global.constexprSpecifier()
                    || node instanceof VarDeclStmt variable && (variable.staticStorage() || variable.constexprSpecifier())
                    || node instanceof StructDecl record && record.recordInfo() != null)) return node;
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
            case ConstructionExpr n -> n.type();
            case TypeQueryExpr.TypeArgument n -> n.type();
            case TypeMemberExpr n -> n.ownerType();
            case StructuredBindingDecl n -> n.type();
            case DestructorCallExpr n -> n.ownerType();
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
        return type != null && (type.containsReference() || !onlyReferences && (type.containsTemplateType() || type.containsPlaceholder() || type.containsExceptionSpecification()));
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
