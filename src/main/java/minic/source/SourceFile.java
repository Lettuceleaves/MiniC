package minic.source;

import java.util.Objects;

/**
 * 表示一份源码文本，并提供 offset 到行列位置的映射。
 *
 * @param path 源码路径或显示名称
 * @param content 源码完整内容
 */
public record SourceFile(String path, String content) {
    /**
     * 创建源码文件模型。
     *
     * @param path 源码路径或显示名称
     * @param content 源码完整内容
     */
    public SourceFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
    }

    /**
     * 将 0-based offset 映射为 1-based line/column。
     *
     * @param offset 源码内容中的 offset，允许等于内容长度
     * @return 对应的源码位置
     */
    public SourcePosition positionAt(int offset) {
        if (offset < 0 || offset > content.length()) {
            throw new IllegalArgumentException("offset out of bounds: " + offset);
        }

        int line = 1;
        int lineStartOffset = 0;
        for (int index = 0; index < offset; index++) {
            if (content.charAt(index) == '\n') {
                line++;
                lineStartOffset = index + 1;
            }
        }

        return new SourcePosition(offset, line, offset - lineStartOffset + 1);
    }
}
