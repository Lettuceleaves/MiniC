package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/** C17 ctype 的 C locale 字节语义；不使用 Java Unicode 字符分类。 */
final class DebugCtypeLibraryProvider implements DebugLibraryProvider {
    private static final int EOF = -1;
    private final Map<String, DebugLibraryFunction> functions;

    DebugCtypeLibraryProvider() {
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();
        registerPredicate(registered, "isalnum", value -> isAlpha(value) || isDigit(value));
        registerPredicate(registered, "isalpha", DebugCtypeLibraryProvider::isAlpha);
        registerPredicate(registered, "isblank", value -> value == ' ' || value == '\t');
        registerPredicate(registered, "iscntrl", value -> value >= 0 && (value < 32 || value == 127));
        registerPredicate(registered, "isdigit", DebugCtypeLibraryProvider::isDigit);
        registerPredicate(registered, "isgraph", value -> value >= 33 && value <= 126);
        registerPredicate(registered, "islower", value -> value >= 'a' && value <= 'z');
        registerPredicate(registered, "isprint", value -> value >= 32 && value <= 126);
        registerPredicate(registered, "ispunct", value -> value >= 33 && value <= 126
                && !isAlpha(value) && !isDigit(value));
        registerPredicate(registered, "isspace", value -> value == ' ' || value >= '\t' && value <= '\r');
        registerPredicate(registered, "isupper", value -> value >= 'A' && value <= 'Z');
        registerPredicate(registered, "isxdigit", value -> isDigit(value)
                || value >= 'A' && value <= 'F'
                || value >= 'a' && value <= 'f');
        registered.put("tolower", this::toLower);
        registered.put("toupper", this::toUpper);
        functions = Map.copyOf(registered);
    }

    @Override
    public String name() {
        return "ctype";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private void registerPredicate(
            Map<String, DebugLibraryFunction> registered,
            String name,
            IntPredicate predicate
    ) {
        registered.put(name, (runtime, arguments) -> {
            int value = argument(name, arguments);
            return new Returned(Value.of(IrType.INT, predicate.test(value) ? 1 : 0));
        });
    }

    private DebugLibraryCallResult toLower(DebugRuntime runtime, List<Value> arguments) {
        int value = argument("tolower", arguments);
        if (value >= 'A' && value <= 'Z') {
            value += 'a' - 'A';
        }
        return new Returned(Value.of(IrType.INT, value));
    }

    private DebugLibraryCallResult toUpper(DebugRuntime runtime, List<Value> arguments) {
        int value = argument("toupper", arguments);
        if (value >= 'a' && value <= 'z') {
            value -= 'a' - 'A';
        }
        return new Returned(Value.of(IrType.INT, value));
    }

    private int argument(String function, List<Value> arguments) {
        DebugLibrarySupport.requireCount(function, arguments, 1);
        long raw = arguments.getFirst().integer();
        if (raw < EOF || raw > 255) {
            throw new IllegalStateException(function + " argument is neither EOF nor an unsigned byte: " + raw);
        }
        return (int) raw;
    }

    private static boolean isAlpha(int value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z';
    }

    private static boolean isDigit(int value) {
        return value >= '0' && value <= '9';
    }
}
