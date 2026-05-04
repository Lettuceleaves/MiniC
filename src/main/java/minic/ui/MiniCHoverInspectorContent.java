package minic.ui;

import minic.uiapi.UiSourceSpanDto;

import java.util.List;
import java.util.Objects;

/**
 * 底部 hover inspector 的一次展示内容。
 *
 * @param title 标题
 * @param metadata 元数据行
 * @param source 源码全文
 * @param range 当前元素对应的源码范围；可为 {@code null}
 * @param explanation 右侧解释文本
 */
public record MiniCHoverInspectorContent(
        String title,
        List<String> metadata,
        String source,
        UiSourceSpanDto range,
        String explanation
) {
    public MiniCHoverInspectorContent {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(explanation, "explanation");
        metadata = List.copyOf(metadata);
    }

    /**
     * 空内容。
     */
    public static MiniCHoverInspectorContent empty() {
        return new MiniCHoverInspectorContent("", List.of(), "", null, "");
    }

    /**
     * 是否没有可显示内容。
     *
     * @return 空则为 {@code true}
     */
    public boolean emptyContent() {
        return title.isBlank() && metadata.isEmpty() && source.isBlank() && explanation.isBlank();
    }
}
