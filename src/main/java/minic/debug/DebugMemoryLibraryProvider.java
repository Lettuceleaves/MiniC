package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** stdlib 中与调试堆有关的函数。 */
final class DebugMemoryLibraryProvider implements DebugLibraryProvider {
    private final Map<String, DebugLibraryFunction> functions;

    DebugMemoryLibraryProvider() {
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();
        registered.put("malloc", this::malloc);
        registered.put("calloc", this::calloc);
        registered.put("realloc", this::realloc);
        registered.put("free", this::free);
        functions = Map.copyOf(registered);
    }

    @Override
    public String name() {
        return "stdlib-memory";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private DebugLibraryCallResult malloc(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("malloc", arguments, 1);
        int size = DebugLibrarySupport.allocationSize("malloc", arguments.getFirst().integer());
        return new Returned(Value.of(IrType.POINTER, runtime.allocate(size, 16, "heap", "malloc")));
    }

    private DebugLibraryCallResult calloc(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("calloc", arguments, 2);
        long bytes = Math.multiplyExact(arguments.get(0).integer(), arguments.get(1).integer());
        int size = DebugLibrarySupport.allocationSize("calloc", bytes);
        return new Returned(Value.of(
                IrType.POINTER,
                runtime.allocateZeroed(size, 16, "heap", "calloc")
        ));
    }

    private DebugLibraryCallResult free(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("free", arguments, 1);
        runtime.release(arguments.getFirst().integer());
        return new Returned(null);
    }

    private DebugLibraryCallResult realloc(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("realloc", arguments, 2);
        long address = arguments.get(0).integer();
        long requested = arguments.get(1).integer();
        if (requested == 0) {
            runtime.release(address);
            return new Returned(Value.of(IrType.POINTER, 0));
        }
        if (requested < 0 || requested > 16L * 1024 * 1024) {
            runtime.setErrno(DebugLibrarySupport.ENOMEM);
            return new Returned(Value.of(IrType.POINTER, 0));
        }
        return new Returned(Value.of(
                IrType.POINTER,
                runtime.reallocate(address, Math.toIntExact(requested))
        ));
    }
}
