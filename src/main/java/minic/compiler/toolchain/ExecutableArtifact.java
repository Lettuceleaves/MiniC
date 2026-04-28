package minic.compiler.toolchain;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 可执行文件产物模型。
 *
 * @param path 可执行文件路径
 */
public record ExecutableArtifact(Path path) {
    /**
     * 创建可执行文件产物模型。
     *
     * @param path 可执行文件路径
     */
    public ExecutableArtifact {
        Objects.requireNonNull(path, "path");
    }
}
