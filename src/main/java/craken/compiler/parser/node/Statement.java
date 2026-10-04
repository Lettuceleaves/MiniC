package craken.compiler.parser.node;

import craken.compiler.type.CrakenType;
import craken.compiler.parser.node.Declaration.AlignmentSpec;
import craken.SourceRange;
import craken.compiler.parser.node.Declaration.StructuredBindingDecl;
import craken.compiler.parser.node.Expression.InitializerSyntax;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 语句节点及其全部具体类型。
 */
public interface Statement extends AstNode {

    static final class BlockStmt extends AbstractAstNode implements Statement {
        private final List<Statement> statements;
        private final SourceRange range;

        @AstNodeConstructor({"statements", "range"})
        public BlockStmt(List<Statement> statements, SourceRange range) {
            Objects.requireNonNull(statements, "statements");
            Objects.requireNonNull(range, "range");
            statements = List.copyOf(statements);

            this.statements = statements;
            this.range = range;
        }

        public List<Statement> statements() { return statements; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BlockStmt that)) return false;
            return Objects.equals(statements, that.statements)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(statements);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "BlockStmt[statements=" + statements + ", range=" + range + "]";
        }
    }

    /** Consecutive declarations in one statement. This node never creates a lexical scope. */
    static final class DeclGroupStmt extends AbstractAstNode implements Statement {
        private final List<Statement> statements;
        private final SourceRange range;

        @AstNodeConstructor({"statements", "range"})
        public DeclGroupStmt(List<Statement> statements, SourceRange range) {
            statements=List.copyOf(Objects.requireNonNull(statements,"statements"));
            Objects.requireNonNull(range,"range");
            if(statements.isEmpty())throw new IllegalArgumentException("A declaration group cannot be empty");

            this.statements = statements;
            this.range = range;
        }

        public List<Statement> statements() { return statements; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DeclGroupStmt that)) return false;
            return Objects.equals(statements, that.statements)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(statements);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "DeclGroupStmt[statements=" + statements + ", range=" + range + "]";
        }
    }

    static final class BreakStmt extends AbstractAstNode implements Statement {
        private final SourceRange range;

        @AstNodeConstructor({"range"})
        public BreakStmt(SourceRange range) {
            Objects.requireNonNull(range, "range");

            this.range = range;
        }

        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BreakStmt that)) return false;
            return Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "BreakStmt[range=" + range + "]";
        }
    }

    static final class ContinueStmt extends AbstractAstNode implements Statement {
        private final SourceRange range;

        @AstNodeConstructor({"range"})
        public ContinueStmt(SourceRange range) {
            Objects.requireNonNull(range, "range");

            this.range = range;
        }

        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ContinueStmt that)) return false;
            return Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "ContinueStmt[range=" + range + "]";
        }
    }

    static final class DoWhileStmt extends AbstractAstNode implements Statement {
        private final Statement body;
        private final Expression condition;
        private final SourceRange range;

        @AstNodeConstructor({"body", "condition", "range"})
        public DoWhileStmt(Statement body, Expression condition, SourceRange range) {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(range, "range");

            this.body = body;
            this.condition = condition;
            this.range = range;
        }

        public Statement body() { return body; }
        public Expression condition() { return condition; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DoWhileStmt that)) return false;
            return Objects.equals(body, that.body)
                    && Objects.equals(condition, that.condition)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(body);
            result = 31 * result + Objects.hashCode(condition);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "DoWhileStmt[body=" + body + ", condition=" + condition + ", range=" + range + "]";
        }
    }

    static final class ExprStmt extends AbstractAstNode implements Statement {
        private final Expression expression;
        private final SourceRange range;

        @AstNodeConstructor({"expression", "range"})
        public ExprStmt(Expression expression, SourceRange range) {
            Objects.requireNonNull(expression, "expression");
            Objects.requireNonNull(range, "range");

            this.expression = expression;
            this.range = range;
        }

        public Expression expression() { return expression; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ExprStmt that)) return false;
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
            return "ExprStmt[expression=" + expression + ", range=" + range + "]";
        }
    }

    static final class ForStmt extends AbstractAstNode implements Statement {
        private final Statement initializer;
        private final Expression condition;
        private final Expression step;
        private final Statement body;
        private final SourceRange range;

        @AstNodeConstructor({"initializer", "condition", "step", "body", "range"})
        public ForStmt(Statement initializer, Expression condition, Expression step, Statement body, SourceRange range) {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(range, "range");

            this.initializer = initializer;
            this.condition = condition;
            this.step = step;
            this.body = body;
            this.range = range;
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

        public Statement initializer() { return initializer; }
        public Expression condition() { return condition; }
        public Expression step() { return step; }
        public Statement body() { return body; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ForStmt that)) return false;
            return Objects.equals(initializer, that.initializer)
                    && Objects.equals(condition, that.condition)
                    && Objects.equals(step, that.step)
                    && Objects.equals(body, that.body)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(initializer);
            result = 31 * result + Objects.hashCode(condition);
            result = 31 * result + Objects.hashCode(step);
            result = 31 * result + Objects.hashCode(body);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "ForStmt[initializer=" + initializer + ", condition=" + condition + ", step=" + step + ", body=" + body + ", range=" + range + "]";
        }
    }

    static final class IfStmt extends AbstractAstNode implements Statement {
        private final Expression condition;
        private final Statement thenBranch;
        private final Statement elseBranch;
        private final SourceRange range;

        @AstNodeConstructor({"condition", "thenBranch", "elseBranch", "range"})
        public IfStmt(Expression condition, Statement thenBranch, Statement elseBranch, SourceRange range) {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(thenBranch, "thenBranch");
            Objects.requireNonNull(range, "range");

            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
            this.range = range;
        }

        public Optional<Statement> elseBranchOptional() {
            return Optional.ofNullable(elseBranch);
        }

        public Expression condition() { return condition; }
        public Statement thenBranch() { return thenBranch; }
        public Statement elseBranch() { return elseBranch; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IfStmt that)) return false;
            return Objects.equals(condition, that.condition)
                    && Objects.equals(thenBranch, that.thenBranch)
                    && Objects.equals(elseBranch, that.elseBranch)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(condition);
            result = 31 * result + Objects.hashCode(thenBranch);
            result = 31 * result + Objects.hashCode(elseBranch);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "IfStmt[condition=" + condition + ", thenBranch=" + thenBranch + ", elseBranch=" + elseBranch + ", range=" + range + "]";
        }
    }

    static final class ReturnStmt extends AbstractAstNode implements Statement {
        private final Expression expression;
        private final SourceRange range;

        @AstNodeConstructor({"expression", "range"})
        public ReturnStmt(Expression expression, SourceRange range) {
            Objects.requireNonNull(range, "range");

            this.expression = expression;
            this.range = range;
        }

        public Optional<Expression> expressionOptional() {
            return Optional.ofNullable(expression);
        }

        public Expression expression() { return expression; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ReturnStmt that)) return false;
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
            return "ReturnStmt[expression=" + expression + ", range=" + range + "]";
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

    static final class SwitchStmt extends AbstractAstNode implements Statement {
        private final Expression selector;
        private final List<SwitchCase> cases;
        private final SourceRange range;

        @AstNodeConstructor({"selector", "cases", "range"})
        public SwitchStmt(Expression selector, List<SwitchCase> cases, SourceRange range) {
            Objects.requireNonNull(selector, "selector");
            Objects.requireNonNull(cases, "cases");
            Objects.requireNonNull(range, "range");
            cases = List.copyOf(cases);

            this.selector = selector;
            this.cases = cases;
            this.range = range;
        }

        public Expression selector() { return selector; }
        public List<SwitchCase> cases() { return cases; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SwitchStmt that)) return false;
            return Objects.equals(selector, that.selector)
                    && Objects.equals(cases, that.cases)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(selector);
            result = 31 * result + Objects.hashCode(cases);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "SwitchStmt[selector=" + selector + ", cases=" + cases + ", range=" + range + "]";
        }
    }

    static final class VarDeclStmt extends AbstractAstNode implements Statement {
        private final String name;
        private final CrakenType type;
        private final Expression initializer;
        private final List<AlignmentSpec> alignmentSpecs;
        private final InitializerSyntax initializerSyntax;
        private final boolean staticStorage;
        private final SourceRange range;
        private final boolean constexprSpecifier;

        public VarDeclStmt(String name,CrakenType type,Expression initializer,List<AlignmentSpec> alignmentSpecs,InitializerSyntax initializerSyntax,boolean staticStorage,SourceRange range){this(name,type,initializer,alignmentSpecs,initializerSyntax,staticStorage,range,false);}
        public VarDeclStmt withConstexprSpecifier(boolean value){return new VarDeclStmt(name,value?CrakenType.qualified(type,java.util.Set.of(CrakenType.TypeQualifier.CONST)):type,initializer,alignmentSpecs,initializerSyntax,staticStorage,range,value);}
        @AstNodeConstructor({"name", "type", "initializer", "alignmentSpecs", "initializerSyntax", "staticStorage", "range", "constexprSpecifier"})
        public VarDeclStmt(String name, CrakenType type, Expression initializer, List<AlignmentSpec> alignmentSpecs, InitializerSyntax initializerSyntax, boolean staticStorage, SourceRange range, boolean constexprSpecifier) {
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

            this.name = name;
            this.type = type;
            this.initializer = initializer;
            this.alignmentSpecs = alignmentSpecs;
            this.initializerSyntax = initializerSyntax;
            this.staticStorage = staticStorage;
            this.range = range;
            this.constexprSpecifier = constexprSpecifier;
        }

        public VarDeclStmt(String name, CrakenType type, Expression initializer,
                           List<AlignmentSpec> alignmentSpecs, InitializerSyntax initializerSyntax, SourceRange range) {
            this(name, type, initializer, alignmentSpecs, initializerSyntax, false, range);
        }

        public VarDeclStmt(String name, CrakenType type, Expression initializer,
                           List<AlignmentSpec> alignmentSpecs, SourceRange range) {
            this(name, type, initializer, alignmentSpecs, null, range);
        }

        public Optional<Expression> initializerOptional() {
            return Optional.ofNullable(initializer);
        }

        public VarDeclStmt(String name, CrakenType type, Expression initializer, SourceRange range) {
            this(name, type, initializer, List.of(), range);
        }

        public String name() { return name; }
        public CrakenType type() { return type; }
        public Expression initializer() { return initializer; }
        public List<AlignmentSpec> alignmentSpecs() { return alignmentSpecs; }
        public InitializerSyntax initializerSyntax() { return initializerSyntax; }
        public boolean staticStorage() { return staticStorage; }
        public SourceRange range() { return range; }
        public boolean constexprSpecifier() { return constexprSpecifier; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VarDeclStmt that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(type, that.type)
                    && Objects.equals(initializer, that.initializer)
                    && Objects.equals(alignmentSpecs, that.alignmentSpecs)
                    && Objects.equals(initializerSyntax, that.initializerSyntax)
                    && staticStorage == that.staticStorage
                    && Objects.equals(range, that.range)
                    && constexprSpecifier == that.constexprSpecifier;
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(initializer);
            result = 31 * result + Objects.hashCode(alignmentSpecs);
            result = 31 * result + Objects.hashCode(initializerSyntax);
            result = 31 * result + Boolean.hashCode(staticStorage);
            result = 31 * result + Objects.hashCode(range);
            result = 31 * result + Boolean.hashCode(constexprSpecifier);
            return result;
        }

        @Override public String toString() {
            return "VarDeclStmt[name=" + name + ", type=" + type + ", initializer=" + initializer + ", alignmentSpecs=" + alignmentSpecs + ", initializerSyntax=" + initializerSyntax + ", staticStorage=" + staticStorage + ", range=" + range + ", constexprSpecifier=" + constexprSpecifier + "]";
        }
    }

    static final class TypedefStmt extends AbstractAstNode implements Statement {
        private final String name;
        private final CrakenType type;
        private final SourceRange range;

        @AstNodeConstructor({"name", "type", "range"})
        public TypedefStmt(String name, CrakenType type, SourceRange range) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(range, "range");

            this.name = name;
            this.type = type;
            this.range = range;
        }

        public String name() { return name; }
        public CrakenType type() { return type; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TypedefStmt that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(type, that.type)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "TypedefStmt[name=" + name + ", type=" + type + ", range=" + range + "]";
        }
    }

    static final class WhileStmt extends AbstractAstNode implements Statement {
        private final Expression condition;
        private final Statement body;
        private final SourceRange range;

        @AstNodeConstructor({"condition", "body", "range"})
        public WhileStmt(Expression condition, Statement body, SourceRange range) {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(range, "range");

            this.condition = condition;
            this.body = body;
            this.range = range;
        }

        public Expression condition() { return condition; }
        public Statement body() { return body; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof WhileStmt that)) return false;
            return Objects.equals(condition, that.condition)
                    && Objects.equals(body, that.body)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(condition);
            result = 31 * result + Objects.hashCode(body);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "WhileStmt[condition=" + condition + ", body=" + body + ", range=" + range + "]";
        }
    }

    /**
     * Internal lifetime region. The cleanup is bound in the surrounding scope and runs once
     * after an entered body exits normally or transfers control through return/break/continue.
     * The body has its own lexical scope. A return value is captured before cleanup executes.
     */
    static final class CleanupScopeStmt extends AbstractAstNode implements Statement {
        private final Statement body;
        private final Expression cleanup;
        private final SourceRange range;

        @AstNodeConstructor({"body", "cleanup", "range"})
        public CleanupScopeStmt(Statement body, Expression cleanup, SourceRange range) {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(cleanup, "cleanup");
            Objects.requireNonNull(range, "range");

            this.body = body;
            this.cleanup = cleanup;
            this.range = range;
        }

        public Statement body() { return body; }
        public Expression cleanup() { return cleanup; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CleanupScopeStmt that)) return false;
            return Objects.equals(body, that.body)
                    && Objects.equals(cleanup, that.cleanup)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(body);
            result = 31 * result + Objects.hashCode(cleanup);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "CleanupScopeStmt[body=" + body + ", cleanup=" + cleanup + ", range=" + range + "]";
        }
    }

    /** Source-only range loop; the declaration is initialized from each iterator dereference. */
    static final class RangeForStmt extends AbstractAstNode implements Statement {
        private final Statement declaration;
        private final Expression initializer;
        private final Statement body;
        private final SourceRange range;

        @AstNodeConstructor({"declaration", "initializer", "body", "range"})
        public RangeForStmt(Statement declaration, Expression initializer, Statement body, SourceRange range) {
            Objects.requireNonNull(declaration, "declaration");
            Objects.requireNonNull(initializer, "initializer");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(range, "range");
            if (!(declaration instanceof Statement.VarDeclStmt || declaration instanceof StructuredBindingDecl))
                throw new IllegalArgumentException("Invalid range declaration");
            if (declaration instanceof Statement.VarDeclStmt variable && (variable.initializer()!=null || variable.staticStorage())
                    || declaration instanceof StructuredBindingDecl binding && binding.initializer()!=null)
                throw new IllegalArgumentException("A range declaration has no separate initializer or static storage");

            this.declaration = declaration;
            this.initializer = initializer;
            this.body = body;
            this.range = range;
        }

        public Statement declaration() { return declaration; }
        public Expression initializer() { return initializer; }
        public Statement body() { return body; }
        public SourceRange range() { return range; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RangeForStmt that)) return false;
            return Objects.equals(declaration, that.declaration)
                    && Objects.equals(initializer, that.initializer)
                    && Objects.equals(body, that.body)
                    && Objects.equals(range, that.range);
        }

        @Override public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(declaration);
            result = 31 * result + Objects.hashCode(initializer);
            result = 31 * result + Objects.hashCode(body);
            result = 31 * result + Objects.hashCode(range);
            return result;
        }

        @Override public String toString() {
            return "RangeForStmt[declaration=" + declaration + ", initializer=" + initializer + ", body=" + body + ", range=" + range + "]";
        }
    }
}
