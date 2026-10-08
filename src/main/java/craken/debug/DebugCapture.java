package craken.debug;

import craken.SourceRange;

import java.util.Objects;

/**
 * 一次被选中变量的 IR 捕获事件。
 *
 * <p>插桩在调试 IR 副本中进行，捕获指令不改变程序语义：事件只记录“变量定义、访问类别、
 * 所在函数、访问点源码范围、捕获时存储地址”。数值由展示层在停止点读取不可变快照得到，
 * 因此同一次停止的多次访问只保留顺序与最后类别，不复制内存。</p>
 *
 * @param variable 命中的源码变量定义
 * @param kind 创建、读取、写入或离开作用域
 * @param function 访问点所属函数的源码显示名
 * @param range 访问点的源码范围
 * @param address 捕获时的存储地址；0 表示当前不可解析
 */
public record DebugCapture(DebugVariable variable, Kind kind, String function, SourceRange range, long address,
                           int offset) {
    public enum Kind { CREATE, READ, WRITE, REMOVE }

    /** 只有变量基址的事件：元素偏移未知。 */
    public DebugCapture(DebugVariable variable, Kind kind, String function, SourceRange range, long address) {
        this(variable, kind, function, range, address, -1);
    }

    public DebugCapture {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(kind, "kind");
        function = Objects.requireNonNullElse(function, "");
        Objects.requireNonNull(range, "range");
        if (address < 0) throw new IllegalArgumentException("address must not be negative");
        if (offset < -1) throw new IllegalArgumentException("offset must be -1 or non-negative");
    }
}
