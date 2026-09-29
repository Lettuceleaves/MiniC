package minic.compiler.ir.manager;

import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrParameter;
import minic.compiler.ir.model.IrType;
import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.Parameter;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Statement;
import minic.compiler.semantic.model.StructLayout;
import minic.compiler.type.MiniType;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * 单个函数的 IR 转换辅助器。
 *
 * <p>该对象不持有遍历游标，也不决定下一步做什么。遍历顺序完全由
 * {@code IrLowerer.step()} 控制。</p>
 */
public final class FunctionManager {
    private final FunctionDecl function;
    private final IrFunctionBuilder builder;
    private final StatementLowerer statementLowerer;
    private final ArrayList<IrParameter> parameters = new ArrayList<>();
    private boolean begun;
    private boolean completed;

    public FunctionManager(
            FunctionDecl function,
            StringLiteralRegistry stringLiteralRegistry,
            Map<String, StructLayout> structLayouts,
            Map<Expression, MiniType> expressionTypes,
            Map<String, IrFunctionSignature> functionSignatures,
            Map<String, MiniType> globalTypes
    ) {
        this.function = Objects.requireNonNull(function, "function");
        builder = new IrFunctionBuilder(structLayouts);
        boolean structReturn = function.returnType().isStruct();
        IrType irReturnType = structReturn ? IrType.POINTER : IrTypeLowerer.lower(function.returnType());
        statementLowerer = new StatementLowerer(
                builder,
                Objects.requireNonNull(stringLiteralRegistry, "stringLiteralRegistry"),
                expressionTypes,
                functionSignatures,
                globalTypes,
                irReturnType,
                function.variadic(),
                function.parameters().size() + (structReturn ? 1 : 0)
        );
        if (structReturn) {
            statementLowerer.setStructReturn(((MiniType.StructType) function.returnType().unqualified()).name());
        }
    }

    /** 初始化参数和函数级局部作用域。 */
    public void begin() {
        if (begun) {
            throw new IllegalStateException("function lowering already begun");
        }
        begun = true;
        if (function.returnType().isStruct()) {
            IrParameter retPtr = new IrParameter(
                    "__retptr",
                    function.returnType().pointerTo(),
                    IrType.POINTER,
                    function.range()
            );
            parameters.add(retPtr);
            builder.defineParameter("__retptr", retPtr.ref());
        }
        for (Parameter parameter : function.parameters()) {
            IrType parameterType = parameter.type().isStruct()
                    ? IrType.POINTER
                    : IrTypeLowerer.lower(parameter.type());
            IrParameter irParameter = new IrParameter(
                    parameter.name(),
                    parameter.type(),
                    parameterType,
                    parameter.range()
            );
            parameters.add(irParameter);
            builder.defineParameter(parameter.name(), irParameter.ref());
        }
        builder.pushLocalScope();
    }

    /** 真正将一条语句转换并写入当前函数 IR。 */
    public void lower(Statement statement) {
        requireActive();
        statementLowerer.lowerStatement(Objects.requireNonNull(statement, "statement"));
    }

    /** 完成当前函数并返回不可变 IR 函数。 */
    public IrFunction complete() {
        requireActive();
        if (function.returnType().isVoid()) {
            builder.addVoidReturnIfOpen(function.range());
        }
        builder.popLocalScope();
        completed = true;
        return snapshot();
    }

    /** 返回当前函数已生成内容的快照。 */
    public IrFunction snapshot() {
        if (!begun) {
            throw new IllegalStateException("function lowering has not begun");
        }
        return new IrFunction(
                function.name(),
                function.returnType(),
                parameters,
                function.variadic(),
                builder.buildBlocks(),
                function.range()
        );
    }

    private void requireActive() {
        if (!begun || completed) {
            throw new IllegalStateException("function lowering is not active");
        }
    }
}
