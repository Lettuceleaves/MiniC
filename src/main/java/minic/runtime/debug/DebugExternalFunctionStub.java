package minic.runtime.debug;

import minic.compiler.ir.value.IrValue;

import java.util.List;

/**
 * Debugger 外部函数 stub。
 */
@FunctionalInterface
public interface DebugExternalFunctionStub {
    /**
     * 执行外部函数调用。
     *
     * @param functionName 函数名
     * @param rawArguments 原始 IR 实参
     * @param arguments 已解析 Debug 值
     * @param context 调用上下文
     * @return 调用结果
     */
    DebugExternalCallResult invoke(
            String functionName,
            List<IrValue> rawArguments,
            List<DebugValue> arguments,
            DebugExternalCallContext context
    );
}
