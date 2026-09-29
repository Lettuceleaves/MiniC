package minic.compiler.link;

import minic.diagnostics.Diagnostic;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Link 阶段的最终结果。 */
public record LinkResult(
        Path objectPath,
        ExecutableArtifact executableArtifact,
        List<Diagnostic> diagnostics
) {
    public LinkResult {
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }

    public Optional<Path> objectPathOptional() { return Optional.ofNullable(objectPath); }
    public Optional<ExecutableArtifact> executableArtifactOptional() { return Optional.ofNullable(executableArtifact); }
}
