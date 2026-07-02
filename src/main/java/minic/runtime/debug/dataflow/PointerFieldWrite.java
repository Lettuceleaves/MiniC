package minic.runtime.debug.dataflow;

import java.util.Objects;

/**
 * 结构化指针字段写入事件。
 *
 * @param ownerAddress 拥有该字段的结构体实例地址
 * @param fieldInfo 字段元数据
 * @param oldTargetAddress 写入前指针目标地址；未初始化或非指针值为空字符串
 * @param newTargetAddress 写入后指针目标地址；NULL 为 {@code null}
 */
public record PointerFieldWrite(
        String ownerAddress,
        DebugFieldInfo fieldInfo,
        String oldTargetAddress,
        String newTargetAddress
) {
    public PointerFieldWrite {
        Objects.requireNonNull(ownerAddress, "ownerAddress");
        Objects.requireNonNull(fieldInfo, "fieldInfo");
        Objects.requireNonNull(oldTargetAddress, "oldTargetAddress");
        Objects.requireNonNull(newTargetAddress, "newTargetAddress");
        if (ownerAddress.isBlank()) {
            throw new IllegalArgumentException("ownerAddress must not be blank");
        }
    }
}
