package minic.runtime.debug.visual;

import java.util.List;
import java.util.Objects;

/**
 * 高级数据结构 descriptor。
 *
 * @param id descriptor ID
 * @param kind 高级结构类别
 * @param primitiveType 底层基元类型
 * @param defaultLayout 默认布局
 * @param decorators 默认装饰器
 * @param validators 默认校验器
 * @param explanation 教学解释
 */
public record DataStructureDescriptor(
        String id,
        String kind,
        VisualStructureType primitiveType,
        String defaultLayout,
        List<VisualDecorator> decorators,
        List<VisualValidator> validators,
        String explanation
) {
    public DataStructureDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(primitiveType, "primitiveType");
        Objects.requireNonNull(defaultLayout, "defaultLayout");
        Objects.requireNonNull(decorators, "decorators");
        Objects.requireNonNull(validators, "validators");
        Objects.requireNonNull(explanation, "explanation");
        if (id.isBlank() || kind.isBlank() || defaultLayout.isBlank()) {
            throw new IllegalArgumentException("descriptor id, kind and defaultLayout must not be blank");
        }
        decorators = List.copyOf(decorators);
        validators = List.copyOf(validators);
    }
}
