package minic.compiler.parser.node;

import minic.compiler.type.MiniType;
import minic.compiler.parser.node.Declaration.AlignmentSpec;
import minic.SourceRange;
import minic.compiler.parser.node.Declaration.StructuredBindingDecl;
import minic.compiler.parser.node.Expression.InitializerSyntax;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 语句节点及其全部具体类型。
 */
public interface Statement extends AstNode {

    record BlockStmt(List<Statement> statements, SourceRange range) implements Statement {
        public BlockStmt {
            Objects.requireNonNull(statements, "statements");
            Objects.requireNonNull(range, "range");
            statements = List.copyOf(statements);
        }
    }

    /** Consecutive declarations in one statement. This node never creates a lexical scope. */
    record DeclGroupStmt(List<Statement> statements,SourceRange range) implements Statement {
        public DeclGroupStmt {
            statements=List.copyOf(Objects.requireNonNull(statements,"statements"));
            Objects.requireNonNull(range,"range");
            if(statements.isEmpty())throw new IllegalArgumentException("A declaration group cannot be empty");
        }
    }

    record BreakStmt(SourceRange range) implements Statement {
        public BreakStmt {
            Objects.requireNonNull(range, "range");
        }
    }

    record ContinueStmt(SourceRange range) implements Statement {
        public ContinueStmt {
            Objects.requireNonNull(range, "range");
        }
    }

    record DoWhileStmt(Statement body, Expression condition, SourceRange range) implements Statement {
        public DoWhileStmt {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(range, "range");
        }
    }

    record ExprStmt(Expression expression, SourceRange range) implements Statement {
        public ExprStmt {
            Objects.requireNonNull(expression, "expression");
            Objects.requireNonNull(range, "range");
        }
    }

    record ForStmt(
            Statement initializer,
            Expression condition,
            Expression step,
            Statement body,
            SourceRange range
    ) implements Statement {
        public ForStmt {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(range, "range");
        }

        public Optional<Statement> initializerOptional() {
            return Optional.ofNullable(initializer);
        }

        public Optional<Expression> conditionOptional() {
            return Optional.ofNullable(condition);
        }

        public Optional<Expression> stepOptional() {
            return Optional.ofNullable(step);
        }
    }

    record IfStmt(
            Expression condition,
            Statement thenBranch,
            Statement elseBranch,
            SourceRange range
    ) implements Statement {
        public IfStmt {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(thenBranch, "thenBranch");
            Objects.requireNonNull(range, "range");
        }

        public Optional<Statement> elseBranchOptional() {
            return Optional.ofNullable(elseBranch);
        }
    }

    record ReturnStmt(Expression expression, SourceRange range) implements Statement {
        public ReturnStmt {
            Objects.requireNonNull(range, "range");
        }

        public Optional<Expression> expressionOptional() {
            return Optional.ofNullable(expression);
        }
    }

    record SwitchCase(Expression value, List<Statement> statements, SourceRange range) implements AstNode {
        public SwitchCase {
            Objects.requireNonNull(statements, "statements");
            Objects.requireNonNull(range, "range");
            statements = List.copyOf(statements);
        }

        public Optional<Expression> valueOptional() {
            return Optional.ofNullable(value);
        }

        public boolean defaultCase() {
            return value == null;
        }
    }

    record SwitchStmt(Expression selector, List<SwitchCase> cases, SourceRange range) implements Statement {
        public SwitchStmt {
            Objects.requireNonNull(selector, "selector");
            Objects.requireNonNull(cases, "cases");
            Objects.requireNonNull(range, "range");
            cases = List.copyOf(cases);
        }
    }

    record VarDeclStmt(
            String name,
            MiniType type,
            Expression initializer,
            List<AlignmentSpec> alignmentSpecs,
            InitializerSyntax initializerSyntax,
            boolean staticStorage,
            SourceRange range,boolean constexprSpecifier
    ) implements Statement {
        public VarDeclStmt(String name,MiniType type,Expression initializer,List<AlignmentSpec> alignmentSpecs,InitializerSyntax initializerSyntax,boolean staticStorage,SourceRange range){this(name,type,initializer,alignmentSpecs,initializerSyntax,staticStorage,range,false);}
        public VarDeclStmt withConstexprSpecifier(boolean value){return new VarDeclStmt(name,value?MiniType.qualified(type,java.util.Set.of(MiniType.TypeQualifier.CONST)):type,initializer,alignmentSpecs,initializerSyntax,staticStorage,range,value);}
        public VarDeclStmt {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(alignmentSpecs, "alignmentSpecs");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            alignmentSpecs = List.copyOf(alignmentSpecs);
            if (initializerSyntax != null && !initializerSyntax.isCompatibilityProjection(initializer)) {
                throw new IllegalArgumentException("Initializer syntax operands must match the core initializer");
            }
        }

        public VarDeclStmt(String name, MiniType type, Expression initializer,
                           List<AlignmentSpec> alignmentSpecs, InitializerSyntax initializerSyntax, SourceRange range) {
            this(name, type, initializer, alignmentSpecs, initializerSyntax, false, range);
        }

        public VarDeclStmt(String name, MiniType type, Expression initializer,
                           List<AlignmentSpec> alignmentSpecs, SourceRange range) {
            this(name, type, initializer, alignmentSpecs, null, range);
        }

        public Optional<Expression> initializerOptional() {
            return Optional.ofNullable(initializer);
        }

        public VarDeclStmt(String name, MiniType type, Expression initializer, SourceRange range) {
            this(name, type, initializer, List.of(), range);
        }
    }

    record TypedefStmt(String name, MiniType type, SourceRange range) implements Statement {
        public TypedefStmt {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(range, "range");
        }
    }

    record WhileStmt(Expression condition, Statement body, SourceRange range) implements Statement {
        public WhileStmt {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(range, "range");
        }
    }

    /**
     * Internal lifetime region. The cleanup is bound in the surrounding scope and runs once
     * after an entered body exits normally or transfers control through return/break/continue.
     * The body has its own lexical scope. A return value is captured before cleanup executes.
     */
    record CleanupScopeStmt(Statement body, Expression cleanup, SourceRange range) implements Statement {
        public CleanupScopeStmt {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(cleanup, "cleanup");
            Objects.requireNonNull(range, "range");
        }
    }

    /** Source-only range loop; the declaration is initialized from each iterator dereference. */
    record RangeForStmt(Statement declaration, Expression initializer,
                        Statement body, SourceRange range) implements Statement {
        public RangeForStmt {
            Objects.requireNonNull(declaration, "declaration");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(range, "range");
            if (!(declaration instanceof Statement.VarDeclStmt || declaration instanceof StructuredBindingDecl))
                throw new IllegalArgumentException("Invalid range declaration");
            if (declaration instanceof Statement.VarDeclStmt variable && (variable.initializer()!=null || variable.staticStorage())
                    || declaration instanceof StructuredBindingDecl binding && binding.initializer()!=null)
                throw new IllegalArgumentException("A range declaration has no separate initializer or static storage");
        }
    }
}
