package minic.runtime.debug.visual;

import java.util.Map;
import java.util.Objects;

/**
 * 混合结构组成部分。
 *
 * @param id part ID
 * @param structureId 被引用结构 ID
 * @param role part 角色
 * @param metadata 点击元数据
 */
public record CompositePart(
        String id,
        String structureId,
        String role,
        Map<String, String> metadata
) {
    public CompositePart {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(structureId, "structureId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(metadata, "metadata");
        if (id.isBlank() || structureId.isBlank() || role.isBlank()) {
            throw new IllegalArgumentException("part id, structureId and role must not be blank");
        }
        metadata = Map.copyOf(metadata);
    }
}
