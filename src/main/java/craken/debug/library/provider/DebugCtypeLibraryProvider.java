package craken.debug;

import craken.compiler.ir.model.IrType;
import craken.debug.DebugLibraryCallResult.Returned;
import craken.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/** C17 ctype 的 C locale 字节语义；不使用 Java Unicode 字符分类。 */
final class DebugCtypeLibraryProvider implements DebugLibraryProvider {
    private static final int EOF = -1;
    private static final int BLANK_MASK = 0x40;
    private final Map<String, DebugLibraryFunction> functions;

    DebugCtypeLibraryProvider() {
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();
        registerPredicate(registered, "isalnum", value -> isAlpha(value) || isDigit(value));
        registerPredicate(registered, "isalpha", DebugCtypeLibraryProvider::isAlpha);
        registered.put("craken_isctype", this::isCType);
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

    private DebugLibraryCallResult isCType(DebugRuntime runtime, List<Value> arguments) {
        String function = "craken_isctype";
        DebugLibrarySupport.requireCount(function, arguments, 2);
        int value = characterArgument(function, arguments.getFirst());
        long mask = arguments.get(1).integer();
        if (mask != BLANK_MASK) {
            throw new IllegalStateException(function + " only exposes the private _BLANK adapter");
        }
        // MSVCRT handles horizontal tab outside _isctype; in the C locale the
        // _BLANK table entry itself contains only the ordinary space character.
        return new Returned(Value.of(IrType.INT, value == ' ' ? 1 : 0));
    }

    private int argument(String function, List<Value> arguments) {
        DebugLibrarySupport.requireCount(function, arguments, 1);
        return characterArgument(function, arguments.getFirst());
    }

    private int characterArgument(String function, Value argument) {
        long raw = argument.integer();
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
