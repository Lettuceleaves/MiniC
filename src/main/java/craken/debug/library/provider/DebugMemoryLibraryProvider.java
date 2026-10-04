package craken.debug;

import craken.compiler.ir.model.IrType;
import craken.debug.DebugLibraryCallResult.Returned;
import craken.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** stdlib 中与调试堆有关的函数。 */
final class DebugMemoryLibraryProvider implements DebugLibraryProvider {
    private final Map<String, DebugLibraryFunction> functions;

    DebugMemoryLibraryProvider() {
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();
        registered.put("malloc", this::malloc);
        registered.put("craken_string_malloc", this::malloc);
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
        try {
            int size = DebugLibrarySupport.allocationSize("malloc", arguments.getFirst().integer());
            return pointer(runtime.allocate(size, 16, "heap", "malloc"));
        } catch (IllegalStateException exception) {
            return outOfMemory(runtime);
        }
    }

    private DebugLibraryCallResult calloc(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("calloc", arguments, 2);
        try {
            long bytes = Math.multiplyExact(arguments.get(0).integer(), arguments.get(1).integer());
            int size = DebugLibrarySupport.allocationSize("calloc", bytes);
            return pointer(runtime.allocateZeroed(size, 16, "heap", "calloc"));
        } catch (ArithmeticException | IllegalStateException exception) {
            return outOfMemory(runtime);
        }
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
        try {
            return pointer(runtime.reallocate(address, Math.toIntExact(requested)));
        } catch (DebugRuntime.HeapAllocationException exception) {
            return outOfMemory(runtime);
        }
    }

    private Returned outOfMemory(DebugRuntime runtime) {
        runtime.setErrno(DebugLibrarySupport.ENOMEM);
        return pointer(0);
    }

    private Returned pointer(long address) {
        return new Returned(Value.of(IrType.POINTER, address));
    }
}
