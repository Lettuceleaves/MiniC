package craken.debug;

import craken.SourceRange;
import craken.compiler.type.CrakenType;

import java.util.Objects;

/**
 * 调试预处理阶段为源码变量打下的静态标记。
 *
 * <p>一个标记绑定“定义位置 → 完整递归类型”。运行时快照只提供名称、值或地址；
 * 类型信息在递归投影时始终从这里取得，不需要插桩。</p>
 *
 * @param sourceName 源码中的变量名
 * @param declaredType 完整递归的声明类型
 * @param kind 存储类别
 * @param function 所属函数的源码显示名；全局变量为空串
 * @param definition 定义处的源码范围
 * @param synthetic 是否为编译器生成的内部槽位（如 {@code __copy#N}）
 */
public record DebugVariable(
        String sourceName,
        CrakenType declaredType,
        Kind kind,
        String function,
        SourceRange definition,
        boolean synthetic
) {
    public enum Kind { LOCAL, PARAMETER, GLOBAL }

    public DebugVariable {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(declaredType, "declaredType");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(definition, "definition");
        if (sourceName.isBlank()) throw new IllegalArgumentException("sourceName must not be blank");
        if (kind == Kind.GLOBAL && !function.isEmpty())
            throw new IllegalArgumentException("global variables have no owning function");
    }
}
