package minic.compiler.obj;

import minic.compiler.obj.coff.CoffObjectFile;
import minic.diagnostics.Diagnostic;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Obj 阶段的最终结果，也是 Link 阶段的唯一输入。 */
public record ObjResult(
        Path assemblyPath,
        Path objectPath,
        CoffObjectFile objectFile,
        String entrySymbol,
        List<Diagnostic> diagnostics
) {
    public ObjResult {
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }

    public Optional<Path> assemblyPathOptional() {
        return Optional.ofNullable(assemblyPath);
    }

    public Optional<Path> objectPathOptional() {
        return Optional.ofNullable(objectPath);
    }
}
