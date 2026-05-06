package minic.runtime.debug.visual;

import java.util.List;

/**
 * 数据结构图形化基元。
 */
public interface VisualStructure {
    /**
     * 结构 ID。
     *
     * @return ID
     */
    String id();

    /**
     * 用户可读名称。
     *
     * @return 名称
     */
    String name();

    /**
     * 高级结构类别。
     *
     * @return 类别
     */
    String kind();

    /**
     * 底层基元类型。
     *
     * @return 基元类型
     */
    VisualStructureType type();

    /**
     * 装饰器。
     *
     * @return 装饰器列表
     */
    List<VisualDecorator> decorators();

    /**
     * 校验器。
     *
     * @return 校验器列表
     */
    List<VisualValidator> validators();

    /**
     * 稳定摘要。
     *
     * @return 摘要
     */
    String summary();
}
