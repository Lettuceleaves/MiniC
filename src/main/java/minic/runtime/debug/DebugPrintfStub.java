package minic.runtime.debug;

import minic.compiler.ir.value.IrStringLiteral;
import minic.compiler.ir.value.IrValue;

import java.util.List;

/**
 * printf 的教学型最小 debug stub。
 */
final class DebugPrintfStub implements DebugExternalFunctionStub {
    @Override
    public DebugExternalCallResult invoke(
            String functionName,
            List<IrValue> rawArguments,
            List<DebugValue> arguments,
            DebugExternalCallContext context
    ) {
        if (rawArguments.isEmpty() || !(rawArguments.getFirst() instanceof IrStringLiteral formatLiteral)) {
            throw new UnsupportedOperationException("debug printf requires a string literal format argument");
        }
        String format = context.stringLiteral(formatLiteral.label()).orElseThrow(() ->
                new IllegalStateException("string literal is not available: " + formatLiteral.label()));
        String output = format(format, arguments.subList(1, arguments.size()));
        return new DebugExternalCallResult(
                DebugValue.intValue(output.length()),
                output,
                "printf 输出 " + output.length() + " 个字符"
        );
    }

    private String format(String format, List<DebugValue> arguments) {
        StringBuilder builder = new StringBuilder();
        int argumentIndex = 0;
        for (int i = 0; i < format.length(); i++) {
            char ch = format.charAt(i);
            if (ch != '%') {
                builder.append(ch);
                continue;
            }
            if (i + 1 >= format.length()) {
                builder.append(ch);
                continue;
            }
            char specifier = format.charAt(++i);
            if (specifier == '%') {
                builder.append('%');
                continue;
            }
            if (specifier == 'l' && i + 1 < format.length()) {
                char longSpecifier = format.charAt(++i);
                if (longSpecifier == 'd' || longSpecifier == 'i') {
                    builder.append(numericArgument(arguments, argumentIndex++));
                    continue;
                }
                builder.append("%l").append(longSpecifier);
                continue;
            }
            switch (specifier) {
                case 'd', 'i' -> builder.append(numericArgument(arguments, argumentIndex++));
                case 'c' -> builder.append(charArgument(arguments, argumentIndex++));
                case 's' -> builder.append(stringArgument(arguments, argumentIndex++));
                default -> builder.append('%').append(specifier);
            }
        }
        return builder.toString();
    }

    private long numericArgument(List<DebugValue> arguments, int index) {
        DebugValue value = argument(arguments, index);
        return switch (value.kind()) {
            case BOOL -> Boolean.parseBoolean(value.summary()) ? 1 : 0;
            case CHAR -> value.summary().length() >= 3 ? value.summary().charAt(1) : 0;
            case INT, LONG -> Long.parseLong(value.summary());
            case NULL -> 0;
            case POINTER -> value.pointerTargetOptional().map(DebugVirtualAddress::offset).orElse(0L);
            case FLOAT, DOUBLE -> (long) Double.parseDouble(value.summary());
            case ARRAY, STRUCT, UNINITIALIZED -> throw new IllegalStateException(
                    "printf numeric argument is not numeric: " + value.summary());
        };
    }

    private char charArgument(List<DebugValue> arguments, int index) {
        DebugValue value = argument(arguments, index);
        if (value.kind() == DebugValueKind.CHAR && value.summary().length() >= 3) {
            return value.summary().charAt(1);
        }
        return (char) numericArgument(arguments, index);
    }

    private String stringArgument(List<DebugValue> arguments, int index) {
        DebugValue value = argument(arguments, index);
        return value.kind() == DebugValueKind.NULL ? "(null)" : value.summary();
    }

    private DebugValue argument(List<DebugValue> arguments, int index) {
        if (index >= arguments.size()) {
            throw new IllegalArgumentException("printf format requires more arguments");
        }
        return arguments.get(index);
    }
}
