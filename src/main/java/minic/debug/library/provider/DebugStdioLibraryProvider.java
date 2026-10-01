package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** stdio 中由调试器虚拟标准流实现的函数。 */
final class DebugStdioLibraryProvider implements DebugLibraryProvider {
    private final Map<String, DebugLibraryFunction> functions;

    DebugStdioLibraryProvider() {
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();
        registered.put("printf", this::printf);
        registered.put("scanf", this::scanf);
        registered.put("getchar", this::getchar);
        registered.put("minic_iob_base", this::iobBase);
        registered.put("fgetc", this::fgetc);
        registered.put("fputc", this::fputc);
        registered.put("ungetc", this::ungetc);
        registered.put("fflush", this::fflush);
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
        appendNarrowOutput(runtime, rendered);
        return integerResult(rendered.length());
    }

    private DebugLibraryCallResult getchar(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("getchar", arguments, 0);
        return integerResult(runtime.readInputCharacter());
    }

    private DebugLibraryCallResult putchar(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("putchar", arguments, 1);
        int character = (int) arguments.getFirst().integer() & 0xff;
        runtime.appendOutputByte(character, false);
        return integerResult(character);
    }

    private DebugLibraryCallResult iobBase(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("minic_iob_base", arguments, 0);
        return new Returned(Value.of(IrType.POINTER, runtime.standardStreamsAddress()));
    }

    private DebugLibraryCallResult fgetc(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("fgetc", arguments, 1);
        if (runtime.standardStream(arguments.getFirst().integer()) != 0) return integerResult(-1);
        return integerResult(runtime.readInputCharacter());
    }

    private DebugLibraryCallResult fputc(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("fputc", arguments, 2);
        int stream = runtime.standardStream(arguments.get(1).integer());
        if (stream == 0) return integerResult(-1);
        int character = (int) arguments.getFirst().integer() & 0xff;
        runtime.appendOutputByte(character, stream == 2);
        return integerResult(character);
    }

    private DebugLibraryCallResult ungetc(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("ungetc", arguments, 2);
        if (runtime.standardStream(arguments.get(1).integer()) != 0) return integerResult(-1);
        return integerResult(runtime.unreadInputCharacter((int) arguments.getFirst().integer()));
    }

    private DebugLibraryCallResult fflush(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("fflush", arguments, 1);
        // Virtual output is already committed to its byte stream. NULL flushes all.
        if (arguments.getFirst().integer() != 0) runtime.standardStream(arguments.getFirst().integer());
        return integerResult(0);
    }

    private DebugLibraryCallResult puts(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("puts", arguments, 1);
        appendNarrowOutput(runtime, runtime.readCString(arguments.getFirst().integer()) + "\n");
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
        return DebugPrintfFormatter.render(runtime, arguments, formatIndex);
    }

    private void appendNarrowOutput(DebugRuntime runtime, String bytes) {
        for (int i = 0; i < bytes.length(); i++) runtime.appendOutputByte(bytes.charAt(i), false);
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
            if (scanWhitespace(current)) {
                input.skipWhitespace();
                continue;
            }
            if (current != '%') {
                int character = input.peekCharacter();
                if (character < 0) return assigned == 0 ? -1 : assigned;
                if (character != current) {
                    break;
                }
                input.readCharacter();
                continue;
            }
            if (index + 1 < format.length() && format.charAt(index + 1) == '%') {
                int character = input.peekCharacter();
                if (character < 0) return assigned == 0 ? -1 : assigned;
                if (character != '%') {
                    break;
                }
                input.readCharacter();
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
            if (directive.conversion() != 'c') input.skipWhitespace();
            if (input.peekCharacter() < 0) return assigned == 0 ? -1 : assigned;
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
                    String token = DebugScanfNumbers.readInteger(input, directive.maximumWidth(), directive.conversion() == 'i');
                    if (token.isEmpty()) yield false;
                    long value = DebugScanfNumbers.integer(token, directive.conversion() == 'i');
                    runtime.write(destination, Value.of(directive.integerType(false), value));
                    yield true;
                }
                case 'u' -> {
                    String token = DebugScanfNumbers.readInteger(input, directive.maximumWidth(), false);
                    if (token.isEmpty()) yield false;
                    long value = DebugScanfNumbers.integer(token, false);
                    runtime.write(destination, Value.of(directive.integerType(true), value));
                    yield true;
                }
                case 'f' -> {
                    String token = DebugScanfNumbers.readFloating(input, directive.maximumWidth());
                    if (token.isEmpty()) yield false;
                    double value = DebugScanfNumbers.floating(token, !directive.longFloat());
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

    private static boolean scanWhitespace(int value) { return value == ' ' || value >= '\t' && value <= '\r'; }

    private interface ScanInput extends DebugScanfNumbers.Input {
        void skipWhitespace();
        String readToken(int maximumLength);
        int peekCharacter();
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
        public int peekCharacter() {
            return runtime.peekInputCharacter();
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
            // readCString already returns a one-character-per-byte narrow string.
            this.input = input;
        }

        @Override
        public void skipWhitespace() {
            while (offset < input.length() && scanWhitespace(input.charAt(offset))) {
                offset++;
            }
        }

        @Override
        public String readToken(int maximumLength) {
            skipWhitespace();
            int start = offset;
            while (offset < input.length()
                    && !scanWhitespace(input.charAt(offset))
                    && offset - start < maximumLength) {
                offset++;
            }
            return input.substring(start, offset);
        }

        @Override
        public int peekCharacter() {
            return offset >= input.length() ? -1 : input.charAt(offset);
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
