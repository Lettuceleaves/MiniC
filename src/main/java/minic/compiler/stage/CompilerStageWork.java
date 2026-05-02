package minic.compiler.stage;

/**
 * 编译阶段工作数据标记接口。
 *
 * <p>工作数据仅供编译层内部使用，可保存游标、builder、上下文栈等可变状态；
 * 兼容层和 UI 层不得直接暴露该对象。</p>
 */
public interface CompilerStageWork {
}
