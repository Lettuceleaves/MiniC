package craken.compiler.obj;

import craken.compiler.obj.coff.CoffObjectFile;
import craken.compiler.Stage;

import java.nio.file.Path;
import java.util.Optional;

/** Obj 阶段的最终结果，也是 Link 阶段的唯一输入。 */
public record ObjResult(
        Path assemblyPath,
        Path objectPath,
        CoffObjectFile objectFile,
        String entrySymbol
) implements Stage.Context {
    public Optional<Path> assemblyPathOptional() {
        return Optional.ofNullable(assemblyPath);
    }

    public Optional<Path> objectPathOptional() {
        return Optional.ofNullable(objectPath);
    }
}
