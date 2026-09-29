package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;

import java.util.List;
import java.util.Map;

/** stdlib/math 中不访问调试内存的标量函数。 */
final class DebugMathLibraryProvider implements DebugLibraryProvider {
    private final Map<String, DebugLibraryFunction> functions = Map.of(
            "abs", this::abs
    );

    @Override
    public String name() {
        return "scalar-math";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private DebugLibraryCallResult abs(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("abs", arguments, 1);
        return new Returned(Value.of(IrType.INT, Math.abs((int) arguments.getFirst().integer())));
    }
}
