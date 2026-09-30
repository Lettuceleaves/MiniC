package minic.compiler.link;

import minic.compiler.Stage;

import java.nio.file.Path;
import java.util.Optional;

/** Link 阶段的最终结果。 */
public record LinkResult(
        Path objectPath,
        ExecutableArtifact executableArtifact
) implements Stage.Context {
    public Optional<Path> objectPathOptional() { return Optional.ofNullable(objectPath); }
    public Optional<ExecutableArtifact> executableArtifactOptional() { return Optional.ofNullable(executableArtifact); }
}
