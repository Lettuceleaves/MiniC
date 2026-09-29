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
        registered.put("getchar", this::getchar);
        registered.put("putchar", this::putchar);
        registered.put("puts", this::puts);
        registered.put("sprintf", this::sprintf);
        registered.put("snprintf", this::snprintf);
        registered.put("sscanf", this::sscanf);
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
        String rendered = render(runtime, arguments, 0);
        runtime.appendOutput(rendered);
        return integerResult(rendered.length());
    }

    private DebugLibraryCallResult getchar(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("getchar", arguments, 0);
        return integerResult(runtime.readInputCharacter());
    }

    private DebugLibraryCallResult putchar(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("putchar", arguments, 1);
        int character = (int) arguments.getFirst().integer() & 0xff;
        runtime.appendOutput(Character.toString((char) character));
        return integerResult(character);
    }

    private DebugLibraryCallResult puts(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("puts", arguments, 1);
        runtime.appendOutput(runtime.readCString(arguments.getFirst().integer()) + "\n");
        return integerResult(0);
    }

    private DebugLibraryCallResult sprintf(DebugRuntime runtime, List<Value> arguments) {
        requireAtLeast("sprintf", arguments, 2);
        String rendered = render(runtime, arguments, 1);
        runtime.writeCString(arguments.getFirst().integer(), rendered, rendered.length() + 1L);
        return integerResult(rendered.length());
    }

    private DebugLibraryCallResult snprintf(DebugRuntime runtime, List<Value> arguments) {
        requireAtLeast("snprintf", arguments, 3);
        long maximumSize = arguments.get(1).integer();
        String rendered = render(runtime, arguments, 2);
        if (maximumSize != 0) {
            runtime.writeCString(arguments.getFirst().integer(), rendered, maximumSize);
        }
        return integerResult(rendered.length());
    }

    private String render(DebugRuntime runtime, List<Value> arguments, int formatIndex) {
        String format = runtime.readCString(arguments.get(formatIndex).integer());
        StringBuilder rendered = new StringBuilder();
        int argumentIndex = formatIndex + 1;
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
            validatePrintfDirective(directive);
            index = directive.endOffset();
            if (argumentIndex >= arguments.size()) {
                throw new IllegalStateException("printf argument count does not match format");
            }
            Value argument = arguments.get(argumentIndex++);
            rendered.append(formatValue(runtime, directive, argument));
        }
        return rendered.toString();
    }

    private DebugLibraryCallResult scanf(DebugRuntime runtime, List<Value> arguments) {
        if (arguments.isEmpty()) {
            throw new IllegalStateException("scanf requires a format argument");
        }
        return integerResult(scan(
                runtime,
                new RuntimeScanInput(runtime),
                runtime.readCString(arguments.getFirst().integer()),
                arguments,
                1
        ));
    }

    private DebugLibraryCallResult sscanf(DebugRuntime runtime, List<Value> arguments) {
        requireAtLeast("sscanf", arguments, 2);
        String input = runtime.readCString(arguments.get(0).integer());
        String format = runtime.readCString(arguments.get(1).integer());
        return integerResult(scan(runtime, new StringScanInput(input), format, arguments, 2));
    }

    private int scan(
            DebugRuntime runtime,
            ScanInput input,
            String format,
            List<Value> arguments,
            int firstArgument
    ) {
        int argumentIndex = firstArgument;
        int assigned = 0;
        for (int index = 0; index < format.length(); index++) {
            char current = format.charAt(index);
            if (Character.isWhitespace(current)) {
                input.skipWhitespace();
                continue;
            }
            if (current != '%') {
                int character = input.readCharacter();
                if (character != current) {
                    break;
                }
                continue;
            }
            if (index + 1 < format.length() && format.charAt(index + 1) == '%') {
                if (input.readCharacter() != '%') {
                    break;
                }
                index++;
                continue;
            }
            FormatDirective directive = parseDirective(format, index + 1);
            validateScanfDirective(directive);
            index = directive.endOffset();
            if (argumentIndex >= arguments.size()) {
                throw new IllegalStateException("scanf argument count does not match format");
            }
            long destination = arguments.get(argumentIndex++).integer();
            if (!scanValue(runtime, input, directive, destination)) {
                break;
            }
            assigned++;
        }
        return assigned;
    }

    private boolean scanValue(
            DebugRuntime runtime,
            ScanInput input,
            FormatDirective directive,
            long destination
    ) {
        try {
            return switch (directive.conversion()) {
                case 'd', 'i' -> {
                    String token = input.readToken(directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    long value = Long.parseLong(token);
                    runtime.write(destination, Value.of(directive.integerType(false), value));
                    yield true;
                }
                case 'u' -> {
                    String token = input.readToken(directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    long value = Long.parseUnsignedLong(token);
                    runtime.write(destination, Value.of(directive.integerType(true), value));
                    yield true;
                }
                case 'f' -> {
                    String token = input.readToken(directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    double value = Double.parseDouble(token);
                    runtime.write(destination, Value.of(directive.longFloat() ? IrType.DOUBLE : IrType.FLOAT, value));
                    yield true;
                }
                case 's' -> {
                    String token = input.readToken(directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    for (int index = 0; index < token.length(); index++) {
                        runtime.write(destination + index, Value.of(IrType.CHAR, (int) token.charAt(index)));
                    }
                    runtime.write(destination + token.length(), Value.of(IrType.CHAR, 0));
                    yield true;
                }
                case 'c' -> {
                    if (directive.width() > 1) {
                        throw new IllegalStateException("Unsupported scanf width for %c");
                    }
                    int value = input.readCharacter();
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

    private void validatePrintfDirective(FormatDirective directive) {
        if (!directive.flags().isEmpty()) {
            throw new IllegalStateException("Unsupported printf flags: " + directive.flags());
        }
        if (directive.width() != 0) {
            throw new IllegalStateException("Unsupported printf width: " + directive.width());
        }
        if (directive.precisionSpecified() && directive.conversion() != 'f') {
            throw new IllegalStateException(
                    "Unsupported printf precision for %" + directive.conversion()
            );
        }
        boolean validLength = switch (directive.conversion()) {
            case 'd', 'i', 'u', 'x', 'X' -> directive.length().isEmpty()
                    || directive.length().equals("l")
                    || directive.length().equals("ll")
                    || directive.length().equals("I64");
            case 'f' -> directive.length().isEmpty() || directive.length().equals("l");
            case 's', 'c', 'p' -> directive.length().isEmpty();
            default -> true;
        };
        if (!validLength) {
            throw new IllegalStateException(
                    "Unsupported printf length: %" + directive.length() + directive.conversion()
            );
        }
    }

    private void validateScanfDirective(FormatDirective directive) {
        if (!directive.flags().isEmpty() || directive.precisionSpecified()) {
            throw new IllegalStateException(
                    "Unsupported scanf format modifier for %" + directive.conversion()
            );
        }
        boolean validLength = switch (directive.conversion()) {
            case 'd', 'i', 'u' -> directive.length().isEmpty()
                    || directive.length().equals("h")
                    || directive.length().equals("hh")
                    || directive.length().equals("l")
                    || directive.length().equals("ll")
                    || directive.length().equals("I64");
            case 'f' -> directive.length().isEmpty() || directive.length().equals("l");
            case 's', 'c' -> directive.length().isEmpty();
            default -> true;
        };
        if (!validLength) {
            throw new IllegalStateException(
                    "Unsupported scanf length: %" + directive.length() + directive.conversion()
            );
        }
    }

    private FormatDirective parseDirective(String format, int start) {
        int index = start;
        int flagsStart = index;
        while (index < format.length() && "-+ #0".indexOf(format.charAt(index)) >= 0) {
            index++;
        }
        String flags = format.substring(flagsStart, index);
        int width = 0;
        while (index < format.length() && Character.isDigit(format.charAt(index))) {
            width = width * 10 + format.charAt(index++) - '0';
        }
        int precision = 6;
        boolean precisionSpecified = false;
        if (index < format.length() && format.charAt(index) == '.') {
            precisionSpecified = true;
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
        return new FormatDirective(
                format.charAt(index),
                flags,
                width,
                precision,
                precisionSpecified,
                length,
                index
        );
    }

    private DebugLibraryCallResult integerResult(int value) {
        return new Returned(Value.of(IrType.INT, value));
    }

    private void requireAtLeast(String function, List<Value> arguments, int count) {
        if (arguments.size() < count) {
            throw new IllegalStateException(function + " argument count: " + arguments.size());
        }
    }

    private interface ScanInput {
        void skipWhitespace();
        String readToken(int maximumLength);
        int readCharacter();
    }

    private record RuntimeScanInput(DebugRuntime runtime) implements ScanInput {
        @Override
        public void skipWhitespace() {
            runtime.skipInputWhitespace();
        }

        @Override
        public String readToken(int maximumLength) {
            return runtime.readInputToken(maximumLength);
        }

        @Override
        public int readCharacter() {
            return runtime.readInputCharacter();
        }
    }

    private static final class StringScanInput implements ScanInput {
        private final String input;
        private int offset;

        private StringScanInput(String input) {
            this.input = input;
        }

        @Override
        public void skipWhitespace() {
            while (offset < input.length() && Character.isWhitespace(input.charAt(offset))) {
                offset++;
            }
        }

        @Override
        public String readToken(int maximumLength) {
            skipWhitespace();
            int start = offset;
            while (offset < input.length()
                    && !Character.isWhitespace(input.charAt(offset))
                    && offset - start < maximumLength) {
                offset++;
            }
            return input.substring(start, offset);
        }

        @Override
        public int readCharacter() {
            return offset >= input.length() ? -1 : input.charAt(offset++);
        }
    }

    private record FormatDirective(
            char conversion,
            String flags,
            int width,
            int precision,
            boolean precisionSpecified,
            String length,
            int endOffset
    ) {
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
            if (length.equals("h")) {
                return unsigned ? IrType.UNSIGNED_SHORT : IrType.SHORT;
            }
            if (length.equals("hh")) {
                return unsigned ? IrType.UNSIGNED_CHAR : IrType.SIGNED_CHAR;
            }
            return unsigned ? IrType.UNSIGNED_INT : IrType.INT;
        }
    }
}
