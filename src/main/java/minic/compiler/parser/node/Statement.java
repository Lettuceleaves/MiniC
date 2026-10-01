package minic.compiler.parser.node;

import minic.compiler.type.MiniType;
import minic.compiler.parser.node.Declaration.AlignmentSpec;
import minic.SourceRange;

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
            CppInitializer cppInitializer,
            boolean staticStorage,
            SourceRange range
    ) implements Statement {
        public VarDeclStmt {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(alignmentSpecs, "alignmentSpecs");
            Objects.requireNonNull(range, "range");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            alignmentSpecs = List.copyOf(alignmentSpecs);
            if (cppInitializer != null && !cppInitializer.isCompatibilityProjection(initializer)) {
                throw new IllegalArgumentException("C++ initialization operands must match the compatibility projection");
            }
        }

        public VarDeclStmt(String name, MiniType type, Expression initializer,
                           List<AlignmentSpec> alignmentSpecs, CppInitializer cppInitializer, SourceRange range) {
            this(name, type, initializer, alignmentSpecs, cppInitializer, false, range);
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
}
