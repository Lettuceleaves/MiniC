package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;

import java.util.List;
import java.util.Map;

/** stdio.h 中基于宿主文件系统、以 errno 报告失败的路径操作。 */
final class DebugStdioFileLibraryProvider implements DebugLibraryProvider {
    private final Map<String, DebugLibraryFunction> functions = Map.of(
            "remove", this::remove,
            "rename", this::rename
    );

    @Override
    public String name() {
        return "stdio-files";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private DebugLibraryCallResult remove(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("remove", arguments, 1);
        int result = runtime.removeFile(runtime.readCString(arguments.getFirst().integer()));
        return new Returned(Value.of(IrType.INT, result));
    }

    private DebugLibraryCallResult rename(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("rename", arguments, 2);
        int result = runtime.renameFile(
                runtime.readCString(arguments.get(0).integer()),
                runtime.readCString(arguments.get(1).integer())
        );
        return new Returned(Value.of(IrType.INT, result));
    }
}
