package minic.compiler.ir;

import minic.compiler.ast.BinaryExpr;
import minic.compiler.ast.BlockStmt;
import minic.compiler.ast.CallExpr;
import minic.compiler.ast.AssignmentExpr;
import minic.compiler.ast.Expression;
import minic.compiler.ast.ExprStmt;
import minic.compiler.ast.FunctionDecl;
import minic.compiler.ast.GroupingExpr;
import minic.compiler.ast.IntegerLiteralExpr;
import minic.compiler.ast.NameExpr;
import minic.compiler.ast.Parameter;
import minic.compiler.ast.Program;
import minic.compiler.ast.ReturnStmt;
import minic.compiler.ast.Statement;
import minic.compiler.ast.VarDeclStmt;
import minic.compiler.lexer.TokenKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将 MiniC AST 降到 v0.1 基础 IR。
 *
 * <p>当前覆盖 return、整数字面量、形参读取、局部变量、赋值、二元算术、括号表达式、
 * 基础函数调用，以及未初始化读取和除零运行时检查插桩。</p>
 */
public final class IrLowerer {
    /**
     * 将程序 AST 降为 IR 模块。
     *
     * @param program 程序 AST
     * @return IR 模块
     */
    public IrModule lower(Program program) {
        Objects.requireNonNull(program, "program");
        ArrayList<IrFunction> functions = new ArrayList<>();
        for (FunctionDecl function : program.functions()) {
            functions.add(lowerFunction(function));
        }
        return new IrModule(functions);
    }

    private IrFunction lowerFunction(FunctionDecl function) {
        FunctionLowering lowering = new FunctionLowering(function);
        return lowering.lower();
    }

    private static final class FunctionLowering {
        private final FunctionDecl function;
        private final ArrayList<IrInstruction> instructions = new ArrayList<>();
        private final Map<String, IrParameterRef> parameterRefs = new HashMap<>();
        private final Deque<Map<String, IrLocal>> localScopes = new ArrayDeque<>();
        private int nextTemporaryIndex;
        private int nextLocalIndex;

        private FunctionLowering(FunctionDecl function) {
            this.function = Objects.requireNonNull(function, "function");
        }

        private IrFunction lower() {
            ArrayList<IrParameter> parameters = new ArrayList<>();
            for (Parameter parameter : function.parameters()) {
                IrParameter irParameter = new IrParameter(parameter.name(), IrType.INT, parameter.range());
                parameters.add(irParameter);
                parameterRefs.put(parameter.name(), irParameter.ref());
            }

            localScopes.push(new HashMap<>());
            lowerBlock(function.body(), false);
            localScopes.pop();
            IrBlock entry = new IrBlock("entry", instructions);
            return new IrFunction(function.name(), parameters, List.of(entry), function.range());
        }

        private void lowerBlock(BlockStmt block, boolean createChildScope) {
            if (createChildScope) {
                localScopes.push(new HashMap<>());
            }
            for (Statement statement : block.statements()) {
                lowerStatement(statement);
            }
            if (createChildScope) {
                localScopes.pop();
            }
        }

        private void lowerStatement(Statement statement) {
            if (statement instanceof ReturnStmt returnStmt) {
                Expression expression = returnStmt.expressionOptional()
                        .orElseThrow(() -> new IllegalArgumentException("return statement must have a value"));
                instructions.add(new IrReturnInstruction(lowerExpression(expression), returnStmt.range()));
                return;
            }
            if (statement instanceof BlockStmt blockStmt) {
                lowerBlock(blockStmt, true);
                return;
            }
            if (statement instanceof ExprStmt exprStmt) {
                lowerExpression(exprStmt.expression());
                return;
            }
            if (statement instanceof VarDeclStmt varDeclStmt) {
                IrLocal local = declareLocal(varDeclStmt);
                instructions.add(new IrDeclareLocalInstruction(local, varDeclStmt.range()));
                varDeclStmt.initializerOptional().ifPresent(initializer -> {
                    IrValue value = lowerExpression(initializer);
                    instructions.add(new IrStoreLocalInstruction(local, value, varDeclStmt.range()));
                });
                return;
            }
            throw new IllegalArgumentException("unsupported statement: " + statement.getClass().getSimpleName());
        }

        private IrValue lowerExpression(Expression expression) {
            if (expression instanceof IntegerLiteralExpr integerLiteralExpr) {
                return new IrConstant(integerLiteralExpr.value());
            }
            if (expression instanceof NameExpr nameExpr) {
                IrLocal local = resolveLocal(nameExpr.name());
                if (local != null) {
                    instructions.add(new IrCheckInitializedInstruction(local, nameExpr.range()));
                    IrTemporary result = newTemporary();
                    instructions.add(new IrLoadLocalInstruction(result, local, nameExpr.range()));
                    return result;
                }
                return resolveParameter(nameExpr.name());
            }
            if (expression instanceof GroupingExpr groupingExpr) {
                return lowerExpression(groupingExpr.expression());
            }
            if (expression instanceof AssignmentExpr assignmentExpr) {
                IrLocal local = resolveLocal(assignmentExpr.targetName());
                if (local == null) {
                    throw new IllegalArgumentException("assignment target must be a local variable: "
                            + assignmentExpr.targetName());
                }
                IrValue value = lowerExpression(assignmentExpr.value());
                instructions.add(new IrStoreLocalInstruction(local, value, assignmentExpr.range()));
                return value;
            }
            if (expression instanceof BinaryExpr binaryExpr) {
                IrValue left = lowerExpression(binaryExpr.left());
                IrValue right = lowerExpression(binaryExpr.right());
                IrTemporary result = newTemporary();
                if (binaryExpr.operator() == TokenKind.SLASH) {
                    instructions.add(new IrCheckNonZeroInstruction(right, binaryExpr.range()));
                }
                instructions.add(new IrBinaryInstruction(
                        result,
                        lowerOperator(binaryExpr.operator()),
                        left,
                        right,
                        binaryExpr.range()
                ));
                return result;
            }
            if (expression instanceof CallExpr callExpr) {
                ArrayList<IrValue> arguments = new ArrayList<>();
                for (Expression argument : callExpr.arguments()) {
                    arguments.add(lowerExpression(argument));
                }
                IrTemporary result = newTemporary();
                instructions.add(new IrCallInstruction(result, callExpr.calleeName(), arguments, callExpr.range()));
                return result;
            }
            throw new IllegalArgumentException("unsupported expression: " + expression.getClass().getSimpleName());
        }

        private IrLocal declareLocal(VarDeclStmt varDeclStmt) {
            IrLocal local = new IrLocal(
                    varDeclStmt.name() + "#" + nextLocalIndex++,
                    varDeclStmt.name(),
                    IrType.INT,
                    varDeclStmt.range()
            );
            localScopes.peek().put(varDeclStmt.name(), local);
            return local;
        }

        private IrLocal resolveLocal(String name) {
            for (Map<String, IrLocal> scope : localScopes) {
                IrLocal local = scope.get(name);
                if (local != null) {
                    return local;
                }
            }
            return null;
        }

        private IrParameterRef resolveParameter(String name) {
            IrParameterRef parameterRef = parameterRefs.get(name);
            if (parameterRef == null) {
                throw new IllegalArgumentException("unresolved value: " + name);
            }
            return parameterRef;
        }

        private IrTemporary newTemporary() {
            return new IrTemporary("%" + nextTemporaryIndex++, IrType.INT);
        }

        private IrBinaryOperator lowerOperator(TokenKind operator) {
            return switch (operator) {
                case PLUS -> IrBinaryOperator.ADD;
                case MINUS -> IrBinaryOperator.SUBTRACT;
                case STAR -> IrBinaryOperator.MULTIPLY;
                case SLASH -> IrBinaryOperator.DIVIDE;
                default -> throw new IllegalArgumentException("unsupported binary operator: " + operator);
            };
        }
    }
}
