package minic.runtime.debug;

import minic.compiler.ir.model.IrModule;

import java.util.Objects;
import java.util.Optional;

/**
 * 外部函数 debug stub 调用上下文。
 *
 * @param module 当前 IR 模块
 */
public record DebugExternalCallContext(IrModule module) {
    /**
     * 创建外部调用上下文。
     */
    public DebugExternalCallContext {
        Objects.requireNonNull(module, "module");
    }

    /**
     * 按字符串标签读取只读字符串数据。
     *
     * @param label 字符串标签
     * @return 字符串内容
     */
    public Optional<String> stringLiteral(String label) {
        Objects.requireNonNull(label, "label");
        return module.stringData().stream()
                .filter(stringData -> stringData.label().equals(label))
                .map(minic.compiler.ir.model.IrStringData::value)
                .findFirst();
    }
}
