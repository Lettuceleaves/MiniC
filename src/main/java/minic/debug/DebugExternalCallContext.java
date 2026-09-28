package minic.debug;

import minic.compiler.ir.IrResult;

import java.util.Objects;
import java.util.Optional;

/**
 * 外部函数 debug stub 调用上下文。
 *
 * @param irResult 当前 IR 结果
 */
public record DebugExternalCallContext(IrResult irResult) {
    /**
     * 创建外部调用上下文。
     */
    public DebugExternalCallContext {
        Objects.requireNonNull(irResult, "irResult");
    }

    /**
     * 按字符串标签读取只读字符串数据。
     *
     * @param label 字符串标签
     * @return 字符串内容
     */
    public Optional<String> stringLiteral(String label) {
        Objects.requireNonNull(label, "label");
        return irResult.stringData().stream()
                .filter(stringData -> stringData.label().equals(label))
                .map(minic.compiler.ir.model.IrStringData::value)
                .findFirst();
    }
}
