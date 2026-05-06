package minic.runtime.debug.visual;

import java.util.Map;
import java.util.Objects;

/**
 * 混合结构 part 关系。
 *
 * @param id link ID
 * @param fromPartId 起点 part
 * @param toPartId 终点 part
 * @param relation 关系类别
 * @param explanation 教学解释
 * @param metadata 点击元数据
 */
public record CompositeLink(
        String id,
        String fromPartId,
        String toPartId,
        String relation,
        String explanation,
        Map<String, String> metadata
) {
    public CompositeLink {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromPartId, "fromPartId");
        Objects.requireNonNull(toPartId, "toPartId");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(metadata, "metadata");
        if (id.isBlank() || fromPartId.isBlank() || toPartId.isBlank() || relation.isBlank()) {
            throw new IllegalArgumentException("link id, fromPartId, toPartId and relation must not be blank");
        }
        metadata = Map.copyOf(metadata);
    }
}
