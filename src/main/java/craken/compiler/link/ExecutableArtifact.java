package craken.compiler.link;

import java.nio.file.Path;
import java.util.Objects;

/** Link 阶段生成的可执行文件产物。 */
public record ExecutableArtifact(Path path) {
    public ExecutableArtifact {
        Objects.requireNonNull(path, "path");
    }
}
