package craken.debug;

import craken.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * IR 调试器使用的模块化系统库注册表。
 *
 * <p>函数语义由各 {@link DebugLibraryProvider} 按模块提供；本类只负责合并、校验和
 * 名称分派，不再了解 malloc、printf 等具体函数。</p>
 */
final class DebugSystemLibrary {
    private final Map<String, DebugLibraryFunction> functions;

    DebugSystemLibrary() {
        this(List.of(
                new DebugMemoryLibraryProvider(),
                new DebugStdioLibraryProvider(),
                new DebugStdioFileLibraryProvider(),
                new DebugTimeLibraryProvider(),
                new DebugLocaleLibraryProvider(),
                new DebugMathLibraryProvider(),
                new DebugFloatingMathLibraryProvider(),
                new DebugStdlibConversionProvider(),
                new DebugStdlibStateProvider(),
                new DebugTerminationLibraryProvider(),
                new DebugCtypeLibraryProvider(),
                new DebugStringLibraryProvider()
        ));
    }

    DebugSystemLibrary(List<? extends DebugLibraryProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();
        for (DebugLibraryProvider provider : providers) {
            Objects.requireNonNull(provider, "providers must not contain null");
            for (Map.Entry<String, DebugLibraryFunction> entry : provider.functions().entrySet()) {
                String symbol = Objects.requireNonNull(entry.getKey(), "system library symbol");
                DebugLibraryFunction function = Objects.requireNonNull(
                        entry.getValue(),
                        "system library function"
                );
                if (registered.putIfAbsent(symbol, function) != null) {
                    throw new IllegalArgumentException(
                            "duplicate debug system library function: " + symbol
                                    + " (provider " + provider.name() + ")"
                    );
                }
            }
        }
        functions = Map.copyOf(registered);
    }

    Optional<DebugLibraryCallResult> invoke(String name, DebugRuntime runtime, List<Value> arguments) {
        DebugLibraryFunction function = functions.get(name);
        return function == null
                ? Optional.empty()
                : Optional.of(function.invoke(runtime, arguments));
    }

    Set<String> functionNames() {
        return functions.keySet();
    }
}
