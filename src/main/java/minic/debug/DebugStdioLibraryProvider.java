package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** stdio 中由调试器虚拟标准流实现的函数。 */
final class DebugStdioLibraryProvider implements DebugLibraryProvider {
    private final Map<String, DebugLibraryFunction> functions;

    DebugStdioLibraryProvider() {
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();
        registered.put("printf", this::printf);
        registered.put("scanf", this::scanf);
        functions = Map.copyOf(registered);
    }

    @Override
    public String name() {
        return "stdio";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private DebugLibraryCallResult printf(DebugRuntime runtime, List<Value> arguments) {
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
        return new Returned(Value.of(IrType.INT, rendered.length()));
    }

    private DebugLibraryCallResult scanf(DebugRuntime runtime, List<Value> arguments) {
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
        return new Returned(Value.of(IrType.INT, assigned));
    }

    private boolean scanValue(DebugRuntime runtime, FormatDirective directive, long destination) {
        try {
            return switch (directive.conversion()) {
                case 'd', 'i' -> {
                    String token = runtime.readInputToken(directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    long value = Long.parseLong(token);
                    runtime.write(destination, Value.of(directive.integerType(false), value));
                    yield true;
                }
                case 'u' -> {
                    String token = runtime.readInputToken(directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    long value = Long.parseUnsignedLong(token);
                    runtime.write(destination, Value.of(directive.integerType(true), value));
                    yield true;
                }
                case 'f' -> {
                    String token = runtime.readInputToken(directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    double value = Double.parseDouble(token);
                    runtime.write(destination, Value.of(directive.longFloat() ? IrType.DOUBLE : IrType.FLOAT, value));
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
                default -> throw new IllegalStateException(
                        "Unsupported scanf conversion: %" + directive.conversion()
                );
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
            default -> throw new IllegalStateException(
                    "Unsupported printf conversion: %" + directive.conversion()
            );
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

    private record FormatDirective(char conversion, int width, int precision, String length, int endOffset) {
        int maximumWidth() {
            return width == 0 ? Integer.MAX_VALUE : width;
        }

        boolean longValue() {
            return length.equals("l") || length.equals("ll") || length.equals("I64");
        }

        boolean longFloat() {
            return length.equals("l");
        }

        IrType integerType(boolean unsigned) {
            if (length.equals("ll") || length.equals("I64")) {
                return unsigned ? IrType.UNSIGNED_LONG_LONG : IrType.LONG_LONG;
            }
            if (length.equals("l")) {
                return unsigned ? IrType.UNSIGNED_LONG : IrType.LONG;
            }
            return unsigned ? IrType.UNSIGNED_INT : IrType.INT;
        }
    }
}
