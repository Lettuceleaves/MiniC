package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** stdlib/math 中不访问调试内存的标量函数。 */
final class DebugMathLibraryProvider implements DebugLibraryProvider {
    private final Map<String, DebugLibraryFunction> functions;

    DebugMathLibraryProvider() {
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();
        registered.put("abs", this::abs);
        registered.put("labs", this::labs);
        registered.put("llabs", this::llabs);
        functions = Map.copyOf(registered);
    }

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

    private DebugLibraryCallResult labs(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("labs", arguments, 1);
        int value = (int) arguments.getFirst().integer();
        return new Returned(Value.of(IrType.LONG, Math.abs(value)));
    }

    private DebugLibraryCallResult llabs(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("llabs", arguments, 1);
        return new Returned(Value.of(
                IrType.LONG_LONG,
                Math.abs(arguments.getFirst().integer())
        ));
    }
}
