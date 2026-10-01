package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** stdlib/errno 中需要持久运行时状态的函数。 */
final class DebugStdlibStateProvider implements DebugLibraryProvider {
    private final Map<String, DebugLibraryFunction> functions;

    DebugStdlibStateProvider() {
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();
        registered.put("rand", this::rand);
        registered.put("srand", this::srand);
        registered.put("minic_errno_location", this::errnoLocation);
        // The adapter saves/restores this slot; sharing the virtual errno cell
        // models the visible result without inventing a second host CRT state.
        registered.put("minic_ucrt_errno_location", this::errnoLocation);
        functions = Map.copyOf(registered);
    }

    @Override
    public String name() {
        return "stdlib-state";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private DebugLibraryCallResult rand(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("rand", arguments, 0);
        return new Returned(Value.of(IrType.INT, runtime.nextRandom()));
    }

    private DebugLibraryCallResult srand(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("srand", arguments, 1);
        runtime.seedRandom(arguments.getFirst().integer());
        return new Returned(null);
    }

    private DebugLibraryCallResult errnoLocation(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("minic_errno_location", arguments, 0);
        return new Returned(Value.of(IrType.POINTER, runtime.errnoAddress()));
    }
}
