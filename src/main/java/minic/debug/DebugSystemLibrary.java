package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * IR 调试器使用的系统库语义提供器。
 *
 * <p>Debugger 只做名称分派；堆、I/O 和数学函数的解释语义集中在这里，原生链接仍由
 * compiler.library 中的数据目录决定。</p>
 */
final class DebugSystemLibrary {
    private final Map<String, ExternalFunction> functions;

    DebugSystemLibrary() {
        LinkedHashMap<String, ExternalFunction> registered = new LinkedHashMap<>();
        registered.put("malloc", this::malloc);
        registered.put("calloc", this::calloc);
        registered.put("free", this::free);
        registered.put("abs", this::abs);
        registered.put("printf", this::printf);
        registered.put("scanf", this::scanf);
        functions = Map.copyOf(registered);
    }

    Optional<Value> invoke(String name, DebugRuntime runtime, List<Value> arguments) {
        ExternalFunction function = functions.get(name);
        return function == null ? Optional.empty() : Optional.of(function.invoke(runtime, arguments));
    }

    private Value malloc(DebugRuntime runtime, List<Value> arguments) {
        requireCount("malloc", arguments, 1);
        int size = allocationSize("malloc", arguments.getFirst().integer());
        return Value.of(IrType.POINTER, runtime.allocate(size, 16, "heap", "malloc"));
    }

    private Value calloc(DebugRuntime runtime, List<Value> arguments) {
        requireCount("calloc", arguments, 2);
        long bytes = Math.multiplyExact(arguments.get(0).integer(), arguments.get(1).integer());
        int size = allocationSize("calloc", bytes);
        return Value.of(IrType.POINTER, runtime.allocateZeroed(size, 16, "heap", "calloc"));
    }

    private Value free(DebugRuntime runtime, List<Value> arguments) {
        requireCount("free", arguments, 1);
        runtime.release(arguments.getFirst().integer());
        return Value.of(IrType.INT, 0);
    }

    private Value abs(DebugRuntime runtime, List<Value> arguments) {
        requireCount("abs", arguments, 1);
        return Value.of(IrType.INT, Math.abs((int) arguments.getFirst().integer()));
    }

    private Value printf(DebugRuntime runtime, List<Value> arguments) {
        if (arguments.isEmpty()) {
            throw new IllegalStateException("printf requires a format argument");
        }
        String format = runtime.readCString(arguments.getFirst().integer());
        StringBuilder rendered = new StringBuilder();
        int argumentIndex = 1;
        for (int index = 0; index < format.length(); index++) {
            char current = format.charAt(index);
            if (current != '%') {
                rendered.append(current);
                continue;
            }
            if (index + 1 < format.length() && format.charAt(index + 1) == '%') {
                rendered.append('%');
                index++;
                continue;
            }
            FormatDirective directive = parseDirective(format, index + 1);
            index = directive.endOffset();
            if (argumentIndex >= arguments.size()) {
                throw new IllegalStateException("printf argument count does not match format");
            }
            Value argument = arguments.get(argumentIndex++);
            rendered.append(formatValue(runtime, directive, argument));
        }
        runtime.appendOutput(rendered.toString());
        return Value.of(IrType.INT, rendered.length());
    }

    private Value scanf(DebugRuntime runtime, List<Value> arguments) {
        if (arguments.isEmpty()) {
            throw new IllegalStateException("scanf requires a format argument");
        }
        String format = runtime.readCString(arguments.getFirst().integer());
        int argumentIndex = 1;
        int assigned = 0;
        for (int index = 0; index < format.length(); index++) {
            char current = format.charAt(index);
            if (Character.isWhitespace(current)) {
                runtime.skipInputWhitespace();
                continue;
            }
            if (current != '%') {
                int input = runtime.readInputCharacter();
                if (input != current) {
                    break;
                }
                continue;
            }
            if (index + 1 < format.length() && format.charAt(index + 1) == '%') {
                if (runtime.readInputCharacter() != '%') {
                    break;
                }
                index++;
                continue;
            }
            FormatDirective directive = parseDirective(format, index + 1);
            index = directive.endOffset();
            if (argumentIndex >= arguments.size()) {
                throw new IllegalStateException("scanf argument count does not match format");
            }
            long destination = arguments.get(argumentIndex++).integer();
            if (!scanValue(runtime, directive, destination)) {
                break;
            }
            assigned++;
        }
        return Value.of(IrType.INT, assigned);
    }

    private boolean scanValue(DebugRuntime runtime, FormatDirective directive, long destination) {
        try {
            return switch (directive.conversion()) {
                case 'd', 'i' -> {
                    String token = runtime.readInputToken(directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    long value = Long.parseLong(token);
                    runtime.write(destination, Value.of(directive.longValue() ? IrType.LONG : IrType.INT, value));
                    yield true;
                }
                case 'u' -> {
                    String token = runtime.readInputToken(directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    long value = Long.parseUnsignedLong(token);
                    runtime.write(destination, Value.of(directive.longValue() ? IrType.LONG : IrType.INT, value));
                    yield true;
                }
                case 'f' -> {
                    String token = runtime.readInputToken(directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    double value = Double.parseDouble(token);
                    runtime.write(destination, Value.of(directive.longValue() ? IrType.DOUBLE : IrType.FLOAT, value));
                    yield true;
                }
                case 's' -> {
                    String token = runtime.readInputToken(directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    for (int index = 0; index < token.length(); index++) {
                        runtime.write(destination + index, Value.of(IrType.CHAR, (int) token.charAt(index)));
                    }
                    runtime.write(destination + token.length(), Value.of(IrType.CHAR, 0));
                    yield true;
                }
                case 'c' -> {
                    int value = runtime.readInputCharacter();
                    if (value < 0) yield false;
                    runtime.write(destination, Value.of(IrType.CHAR, value));
                    yield true;
                }
                default -> throw new IllegalStateException("Unsupported scanf conversion: %" + directive.conversion());
            };
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String formatValue(DebugRuntime runtime, FormatDirective directive, Value value) {
        return switch (directive.conversion()) {
            case 'd', 'i' -> Long.toString(directive.longValue()
                    ? value.integer()
                    : (int) value.integer());
            case 'u' -> directive.longValue()
                    ? Long.toUnsignedString(value.integer())
                    : Integer.toUnsignedString((int) value.integer());
            case 'x', 'X' -> {
                String digits = directive.longValue()
                        ? Long.toUnsignedString(value.integer(), 16)
                        : Integer.toUnsignedString((int) value.integer(), 16);
                yield directive.conversion() == 'X' ? digits.toUpperCase(Locale.ROOT) : digits;
            }
            case 'f' -> String.format(Locale.ROOT, "%." + directive.precision() + "f", value.real());
            case 's' -> runtime.readCString(value.integer());
            case 'c' -> Character.toString((char) value.integer());
            case 'p' -> "0x" + Long.toUnsignedString(value.integer(), 16);
            default -> throw new IllegalStateException("Unsupported printf conversion: %" + directive.conversion());
        };
    }

    private FormatDirective parseDirective(String format, int start) {
        int index = start;
        while (index < format.length() && "-+ #0".indexOf(format.charAt(index)) >= 0) {
            index++;
        }
        int width = 0;
        while (index < format.length() && Character.isDigit(format.charAt(index))) {
            width = width * 10 + format.charAt(index++) - '0';
        }
        int precision = 6;
        if (index < format.length() && format.charAt(index) == '.') {
            index++;
            precision = 0;
            while (index < format.length() && Character.isDigit(format.charAt(index))) {
                precision = precision * 10 + format.charAt(index++) - '0';
            }
        }
        String length = "";
        if (index + 2 < format.length() && format.startsWith("I64", index)) {
            length = "I64";
            index += 3;
        } else if (index < format.length() && (format.charAt(index) == 'l' || format.charAt(index) == 'h')) {
            char marker = format.charAt(index++);
            length = Character.toString(marker);
            if (index < format.length() && format.charAt(index) == marker) {
                length += marker;
                index++;
            }
        }
        if (index >= format.length()) {
            throw new IllegalStateException("Incomplete format directive");
        }
        return new FormatDirective(format.charAt(index), width, precision, length, index);
    }

    private int allocationSize(String function, long size) {
        if (size <= 0 || size > 16 * 1024 * 1024) {
            throw new IllegalStateException(function + " allocation size is invalid: " + size);
        }
        return Math.toIntExact(size);
    }

    private void requireCount(String function, List<Value> arguments, int count) {
        if (arguments.size() != count) {
            throw new IllegalStateException(function + " argument count: " + arguments.size());
        }
    }

    @FunctionalInterface
    private interface ExternalFunction {
        Value invoke(DebugRuntime runtime, List<Value> arguments);
    }

    private record FormatDirective(char conversion, int width, int precision, String length, int endOffset) {
        int maximumWidth() {
            return width == 0 ? Integer.MAX_VALUE : width;
        }

        boolean longValue() {
            return length.equals("l") || length.equals("ll") || length.equals("I64");
        }
    }
}
