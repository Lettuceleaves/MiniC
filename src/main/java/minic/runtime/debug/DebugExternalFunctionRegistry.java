package minic.runtime.debug;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Debugger 外部函数 stub 注册表。
 */
public final class DebugExternalFunctionRegistry {
    private final Map<String, DebugExternalFunctionStub> stubs = new LinkedHashMap<>();

    /**
     * 创建包含默认 stub 的注册表。
     *
     * @return 注册表
     */
    public static DebugExternalFunctionRegistry defaults() {
        DebugExternalFunctionRegistry registry = new DebugExternalFunctionRegistry();
        registry.register("printf", new DebugPrintfStub());
        return registry;
    }

    /**
     * 注册 stub。
     *
     * @param functionName 函数名
     * @param stub stub
     */
    public void register(String functionName, DebugExternalFunctionStub stub) {
        Objects.requireNonNull(functionName, "functionName");
        Objects.requireNonNull(stub, "stub");
        if (functionName.isBlank()) {
            throw new IllegalArgumentException("functionName must not be blank");
        }
        stubs.put(functionName, stub);
    }

    /**
     * 查找 stub。
     *
     * @param functionName 函数名
     * @return stub
     */
    public Optional<DebugExternalFunctionStub> find(String functionName) {
        Objects.requireNonNull(functionName, "functionName");
        return Optional.ofNullable(stubs.get(functionName));
    }
}
