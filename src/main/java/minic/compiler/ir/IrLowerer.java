package minic.compiler.ir;

import minic.compiler.ast.BinaryExpr;
import minic.compiler.ast.BlockStmt;
import minic.compiler.ast.CallExpr;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将 MiniC AST 降到 v0.1 基础 IR。
 *
 * <p>A051 阶段只覆盖 return、整数字面量、形参读取、二元算术、括号表达式和基础函数调用。
 * 局部变量、赋值和运行时检查会在 A052 中补齐。</p>
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
        private int nextTemporaryIndex;

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

            lowerBlock(function.body());
            IrBlock entry = new IrBlock("entry", instructions);
            return new IrFunction(function.name(), parameters, List.of(entry), function.range());
        }

        private void lowerBlock(BlockStmt block) {
            for (Statement statement : block.statements()) {
                lowerStatement(statement);
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
                lowerBlock(blockStmt);
                return;
            }
            if (statement instanceof ExprStmt exprStmt) {
                lowerExpression(exprStmt.expression());
                return;
            }
            if (statement instanceof VarDeclStmt) {
                throw new IllegalArgumentException("local variables are not supported by A051 lowering");
            }
            throw new IllegalArgumentException("unsupported statement: " + statement.getClass().getSimpleName());
        }

        private IrValue lowerExpression(Expression expression) {
            if (expression instanceof IntegerLiteralExpr integerLiteralExpr) {
                return new IrConstant(integerLiteralExpr.value());
            }
            if (expression instanceof NameExpr nameExpr) {
                IrParameterRef parameterRef = parameterRefs.get(nameExpr.name());
                if (parameterRef == null) {
                    throw new IllegalArgumentException("only parameter references are supported by A051 lowering: "
                            + nameExpr.name());
                }
                return parameterRef;
            }
            if (expression instanceof GroupingExpr groupingExpr) {
                return lowerExpression(groupingExpr.expression());
            }
            if (expression instanceof BinaryExpr binaryExpr) {
                IrValue left = lowerExpression(binaryExpr.left());
                IrValue right = lowerExpression(binaryExpr.right());
                IrTemporary result = newTemporary();
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
