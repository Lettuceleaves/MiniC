package minic.compiler.semantic.cpp;

import minic.SourceRange;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.type.MiniType;

import java.util.ArrayList;
import java.util.List;

/** Core-only static storage lifecycle, shared by native lowering and the source-IR debugger. */
final class CppStaticLifetime {
    interface Context {
        String fresh(String display);
        void global(GlobalVarDecl variable);
        void function(FunctionDecl function);
    }

    private final Context context;
    private final SourceRange range;
    private final List<Statement> startup = new ArrayList<>();
    private String countName, callbacksName, shutdownName, exitName;
    private int callbackCount;
    private boolean abortDeclared;

    CppStaticLifetime(Context context, SourceRange range) {
        this.context = context;
        this.range = range;
    }

    void startup(Statement action) { startup.add(action); }

    /** Every static object contributes at most one completed-construction registration. */
    Expression register(Expression cleanup, SourceRange source) {
        if (cleanup == null) return null;
        ensureRegistry();
        String callback = context.fresh("static_destructor");
        context.function(new FunctionDecl(callback, MiniType.VOID, List.of(), false,
                new BlockStmt(List.of(new ExprStmt(cleanup, source)), source), false, source));
        callbackCount++;
        Expression count = name(countName, source);
        Expression slot = new IndexExpr(name(callbacksName, source), count, source);
        return new CommaExpr(List.of(
                new AssignmentExpr(slot, TokenType.EQUAL, name(callback, source), source),
                new PostfixUpdateExpr(count, TokenType.PLUS_PLUS, source)), source);
    }

    /** Local statics use 0=unentered, 1=initializing, 2=complete. Recursive initialization is diagnosed. */
    Statement once(List<Statement> initialize, SourceRange source) {
        String guard = context.fresh("static_guard");
        context.global(new GlobalVarDecl(guard, MiniType.INT, null, false, List.of(), source));
        if (!abortDeclared) {
            context.function(new FunctionDecl("abort", MiniType.VOID, List.of(), false, null, true, true, source));
            abortDeclared = true;
        }
        Expression value = name(guard, source);
        List<Statement> actions = new ArrayList<>();
        actions.add(new IfStmt(new BinaryExpr(value, TokenType.EQUAL_EQUAL, integer(1, source), source),
                new ExprStmt(call("abort", List.of(), source), source), null, source));
        actions.add(new ExprStmt(new AssignmentExpr(value, TokenType.EQUAL, integer(1, source), source), source));
        actions.addAll(initialize);
        actions.add(new ExprStmt(new AssignmentExpr(value, TokenType.EQUAL, integer(2, source), source), source));
        return new IfStmt(new BinaryExpr(value, TokenType.BANG_EQUAL, integer(2, source), source),
                new BlockStmt(actions, source), null, source);
    }

    /** Source references to the real C exit function go through this function, including indirect calls. */
    String exitFunction() {
        ensureRegistry();
        if (exitName == null) exitName = context.fresh("static_exit");
        return exitName;
    }

    String finish() {
        if (countName != null) {
            MiniType callbackType = MiniType.function(MiniType.VOID, List.of(), false).pointerTo();
            context.global(new GlobalVarDecl(countName, MiniType.INT, null, false, List.of(), range));
            context.global(new GlobalVarDecl(callbacksName, callbackType.arrayOf(Math.max(1, callbackCount)),
                    null, false, List.of(), range));
            Expression count = name(countName, range);
            Statement body = new BlockStmt(List.of(
                    new ExprStmt(new PostfixUpdateExpr(count, TokenType.MINUS_MINUS, range), range),
                    new ExprStmt(new CallExpr(new IndexExpr(name(callbacksName, range), count, range), List.of(), range), range)), range);
            // Pop before invoking: exit() called by a destructor cannot re-run that destructor.
            context.function(new FunctionDecl(shutdownName, MiniType.VOID, List.of(), false,
                    new BlockStmt(List.of(new WhileStmt(new BinaryExpr(count, TokenType.GREATER,
                            integer(0, range), range), body, range)), range), false, range));
        }
        if (exitName != null) {
            context.function(new FunctionDecl("exit", MiniType.VOID,
                    List.of(new Parameter("status", MiniType.INT, range)), false, null, true, true, range));
            String status = context.fresh("exit_status");
            context.function(new FunctionDecl(exitName, MiniType.VOID,
                    List.of(new Parameter(status, MiniType.INT, range)), false,
                    new BlockStmt(List.of(new ExprStmt(call(shutdownName, List.of(), range), range),
                            new ExprStmt(call("exit", List.of(name(status, range)), range), range)), range),
                    false, true, range));
        }
        if (startup.isEmpty() && callbackCount == 0) return "main";
        String entry = context.fresh("program_entry");
        String returned = context.fresh("main_result");
        List<Statement> body = new ArrayList<>(startup);
        body.add(new VarDeclStmt(returned, MiniType.INT, call("main", List.of(), range), range));
        if (shutdownName != null) body.add(new ExprStmt(call(shutdownName, List.of(), range), range));
        body.add(new ReturnStmt(name(returned, range), range));
        context.function(new FunctionDecl(entry, MiniType.INT, List.of(), false,
                new BlockStmt(body, range), false, range));
        return entry;
    }

    private void ensureRegistry() {
        if (countName != null) return;
        countName = context.fresh("static_count");
        callbacksName = context.fresh("static_callbacks");
        shutdownName = context.fresh("static_shutdown");
    }

    private static NameExpr name(String name, SourceRange range) { return new NameExpr(name, range); }
    private static IntegerLiteralExpr integer(int value, SourceRange range) {
        return new IntegerLiteralExpr(value, Integer.toString(value), range);
    }
    private static CallExpr call(String name, List<Expression> args, SourceRange range) {
        return new CallExpr(name(name, range), args, range);
    }
}
