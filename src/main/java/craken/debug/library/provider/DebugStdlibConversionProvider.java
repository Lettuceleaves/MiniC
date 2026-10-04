package craken.debug;

import craken.compiler.ir.model.IrType;
import craken.debug.DebugLibraryCallResult.Returned;
import craken.debug.DebugRuntime.Value;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** C17 stdlib 的数字文本转换；扫描和分类均按 C locale 的 ASCII 规则。 */
final class DebugStdlibConversionProvider implements DebugLibraryProvider {
    private static final int MAX_INPUT_BYTES = 1024 * 1024;
    private static final BigInteger PARSE_CAP = BigInteger.ONE.shiftLeft(65);

    private final Map<String, DebugLibraryFunction> functions;

    DebugStdlibConversionProvider() {
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();
        registered.put("atof", this::atof);
        registered.put("atoi", this::atoi);
        registered.put("atol", this::atol);
        registered.put("atoll", this::atoll);
        registered.put("strtod", this::strtod);
        registered.put("craken_ucrt_strtod", this::strtod);
        registered.put("strtof", this::strtof);
        registered.put("craken_ucrt_strtof", this::strtof);
        registered.put("strtol", this::strtol);
        registered.put("strtoll", this::strtoll);
        registered.put("strtoul", this::strtoul);
        registered.put("strtoull", this::strtoull);
        functions = Map.copyOf(registered);
    }

    @Override
    public String name() {
        return "stdlib-conversion";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private DebugLibraryCallResult atof(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("atof", arguments, 1);
        return new Returned(convertFloating(
                runtime,
                arguments.getFirst().integer(),
                0,
                false
        ));
    }

    private DebugLibraryCallResult atoi(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("atoi", arguments, 1);
        return new Returned(convertSigned(runtime, arguments.getFirst().integer(), 0,
                10, 32, IrType.INT));
    }

    private DebugLibraryCallResult atol(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("atol", arguments, 1);
        return new Returned(convertSigned(runtime, arguments.getFirst().integer(), 0,
                10, 32, IrType.LONG));
    }

    private DebugLibraryCallResult atoll(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("atoll", arguments, 1);
        return new Returned(convertSigned(runtime, arguments.getFirst().integer(), 0,
                10, 64, IrType.LONG_LONG));
    }

    private DebugLibraryCallResult strtod(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strtod", arguments, 2);
        return new Returned(convertFloating(
                runtime,
                arguments.get(0).integer(),
                arguments.get(1).integer(),
                false
        ));
    }

    private DebugLibraryCallResult strtof(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strtof", arguments, 2);
        return new Returned(convertFloating(
                runtime,
                arguments.get(0).integer(),
                arguments.get(1).integer(),
                true
        ));
    }

    private DebugLibraryCallResult strtol(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strtol", arguments, 3);
        return new Returned(convertSigned(runtime, arguments.get(0).integer(),
                arguments.get(1).integer(), (int) arguments.get(2).integer(), 32, IrType.LONG));
    }

    private DebugLibraryCallResult strtoll(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strtoll", arguments, 3);
        return new Returned(convertSigned(runtime, arguments.get(0).integer(),
                arguments.get(1).integer(), (int) arguments.get(2).integer(), 64, IrType.LONG_LONG));
    }

    private DebugLibraryCallResult strtoul(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strtoul", arguments, 3);
        return new Returned(convertUnsigned(runtime, arguments.get(0).integer(),
                arguments.get(1).integer(), (int) arguments.get(2).integer(), 32, IrType.UNSIGNED_LONG));
    }

    private DebugLibraryCallResult strtoull(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strtoull", arguments, 3);
        return new Returned(convertUnsigned(runtime, arguments.get(0).integer(),
                arguments.get(1).integer(), (int) arguments.get(2).integer(), 64, IrType.UNSIGNED_LONG_LONG));
    }

    private Value convertFloating(
            DebugRuntime runtime,
            long inputAddress,
            long endPointerAddress,
            boolean singlePrecision
    ) {
        String input = input(runtime, inputAddress);
        FloatingToken token = scanFloating(input, singlePrecision);
        writeEndPointer(runtime, endPointerAddress, inputAddress, token.converted() ? token.end() : 0);
        if (!token.converted()) {
            return Value.of(singlePrecision ? IrType.FLOAT : IrType.DOUBLE, 0);
        }

        double parsed;
        boolean numeric = token.kind() == FloatingKind.NUMBER;
        if (token.kind() == FloatingKind.INFINITY) {
            parsed = token.negative() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        } else if (token.kind() == FloatingKind.NAN) {
            parsed = token.negative()
                    ? Math.copySign(Double.NaN, -1.0)
                    : Double.NaN;
        } else {
            String text = token.text();
            if (singlePrecision && (text.contains("x") || text.contains("X"))
                    && !text.contains("p") && !text.contains("P")) text += "p0";
            // Parsing first as double would round twice at exact float midpoints.
            parsed = singlePrecision ? Float.parseFloat(text) : Double.parseDouble(text);
        }

        if (singlePrecision) {
            float result = (float) parsed;
            if (numeric && floatingRangeError(result, token.text(), Float.MIN_NORMAL)) {
                runtime.setErrno(DebugLibrarySupport.ERANGE);
            }
            return Value.of(IrType.FLOAT, result);
        }
        if (numeric && floatingRangeError(parsed, token.text(), Double.MIN_NORMAL)) {
            runtime.setErrno(DebugLibrarySupport.ERANGE);
        }
        return Value.of(IrType.DOUBLE, parsed);
    }

    private Value convertSigned(
            DebugRuntime runtime,
            long inputAddress,
            long endPointerAddress,
            int base,
            int bits,
            IrType type
    ) {
        IntegerToken token = scanInteger(input(runtime, inputAddress), base);
        writeEndPointer(runtime, endPointerAddress, inputAddress, token.converted() ? token.end() : 0);
        if (token.invalidBase()) {
            runtime.setErrno(DebugLibrarySupport.EINVAL);
            return Value.of(type, 0);
        }
        if (!token.converted()) {
            return Value.of(type, 0);
        }

        BigInteger positiveLimit = BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE);
        BigInteger negativeLimit = BigInteger.ONE.shiftLeft(bits - 1);
        BigInteger limit = token.negative() ? negativeLimit : positiveLimit;
        if (token.magnitude().compareTo(limit) > 0) {
            runtime.setErrno(DebugLibrarySupport.ERANGE);
            return Value.of(type, token.negative()
                    ? negativeLimit.negate().longValue()
                    : positiveLimit.longValue());
        }
        BigInteger result = token.negative() ? token.magnitude().negate() : token.magnitude();
        return Value.of(type, result.longValue());
    }

    private Value convertUnsigned(
            DebugRuntime runtime,
            long inputAddress,
            long endPointerAddress,
            int base,
            int bits,
            IrType type
    ) {
        IntegerToken token = scanInteger(input(runtime, inputAddress), base);
        writeEndPointer(runtime, endPointerAddress, inputAddress, token.converted() ? token.end() : 0);
        if (token.invalidBase()) {
            runtime.setErrno(DebugLibrarySupport.EINVAL);
            return Value.of(type, 0);
        }
        if (!token.converted()) {
            return Value.of(type, 0);
        }

        BigInteger modulus = BigInteger.ONE.shiftLeft(bits);
        BigInteger maximum = modulus.subtract(BigInteger.ONE);
        if (token.magnitude().compareTo(maximum) > 0) {
            runtime.setErrno(DebugLibrarySupport.ERANGE);
            return Value.of(type, maximum.longValue());
        }
        BigInteger result = token.negative()
                ? token.magnitude().negate().mod(modulus)
                : token.magnitude();
        return Value.of(type, result.longValue());
    }

    private FloatingToken scanFloating(String input, boolean hexWithoutExponent) {
        int cursor = skipSpace(input, 0);
        int tokenStart = cursor;
        boolean negative = false;
        if (cursor < input.length() && (input.charAt(cursor) == '+' || input.charAt(cursor) == '-')) {
            negative = input.charAt(cursor) == '-';
            cursor++;
        }
        int bodyStart = cursor;

        if (startsIgnoreCase(input, bodyStart, "infinity")) {
            int end = bodyStart + "infinity".length();
            return new FloatingToken(input.substring(tokenStart, end), end, true,
                    FloatingKind.INFINITY, negative);
        }
        if (startsIgnoreCase(input, bodyStart, "inf")) {
            int end = bodyStart + 3;
            return new FloatingToken(input.substring(tokenStart, end), end, true,
                    FloatingKind.INFINITY, negative);
        }
        if (startsIgnoreCase(input, bodyStart, "nan")) {
            int end = bodyStart + 3;
            if (end < input.length() && input.charAt(end) == '(') {
                int payloadEnd = end + 1;
                while (payloadEnd < input.length() && isNanPayload(input.charAt(payloadEnd))) {
                    payloadEnd++;
                }
                if (payloadEnd < input.length() && input.charAt(payloadEnd) == ')') {
                    end = payloadEnd + 1;
                }
            }
            return new FloatingToken(input.substring(tokenStart, end), end, true,
                    FloatingKind.NAN, negative);
        }

        if (bodyStart + 2 <= input.length()
                && bodyStart + 1 < input.length()
                && input.charAt(bodyStart) == '0'
                && (input.charAt(bodyStart + 1) == 'x' || input.charAt(bodyStart + 1) == 'X')) {
            int hexEnd = scanHexFloating(input, bodyStart + 2, hexWithoutExponent);
            if (hexEnd >= 0) {
                return new FloatingToken(input.substring(tokenStart, hexEnd), hexEnd, true,
                        FloatingKind.NUMBER, negative);
            }
        }

        int end = cursor;
        boolean digits = false;
        while (end < input.length() && isDecimalDigit(input.charAt(end))) {
            digits = true;
            end++;
        }
        if (end < input.length() && input.charAt(end) == '.') {
            end++;
            while (end < input.length() && isDecimalDigit(input.charAt(end))) {
                digits = true;
                end++;
            }
        }
        if (!digits) {
            return FloatingToken.none();
        }
        int exponent = scanExponent(input, end, 'e', 'E');
        if (exponent >= 0) {
            end = exponent;
        }
        return new FloatingToken(input.substring(tokenStart, end), end, true,
                FloatingKind.NUMBER, negative);
    }

    private int scanHexFloating(String input, int cursor, boolean hexWithoutExponent) {
        int end = cursor;
        boolean digits = false;
        while (end < input.length() && digit(input.charAt(end)) >= 0
                && digit(input.charAt(end)) < 16) {
            digits = true;
            end++;
        }
        if (end < input.length() && input.charAt(end) == '.') {
            end++;
            while (end < input.length() && digit(input.charAt(end)) >= 0
                    && digit(input.charAt(end)) < 16) {
                digits = true;
                end++;
            }
        }
        if (!digits) {
            return -1;
        }
        int exponent = scanExponent(input, end, 'p', 'P');
        return exponent < 0 && hexWithoutExponent ? end : exponent;
    }

    private int scanExponent(String input, int cursor, char lower, char upper) {
        if (cursor >= input.length()
                || input.charAt(cursor) != lower && input.charAt(cursor) != upper) {
            return -1;
        }
        int exponent = cursor + 1;
        if (exponent < input.length()
                && (input.charAt(exponent) == '+' || input.charAt(exponent) == '-')) {
            exponent++;
        }
        int digits = exponent;
        while (exponent < input.length() && isDecimalDigit(input.charAt(exponent))) {
            exponent++;
        }
        return exponent == digits ? -1 : exponent;
    }

    private IntegerToken scanInteger(String input, int requestedBase) {
        if (requestedBase != 0 && (requestedBase < 2 || requestedBase > 36)) {
            return IntegerToken.invalidBaseToken();
        }
        int cursor = skipSpace(input, 0);
        boolean negative = false;
        if (cursor < input.length() && (input.charAt(cursor) == '+' || input.charAt(cursor) == '-')) {
            negative = input.charAt(cursor) == '-';
            cursor++;
        }

        int base = requestedBase;
        if (base == 0) {
            if (hasHexPrefixWithDigit(input, cursor)) {
                base = 16;
                cursor += 2;
            } else if (cursor < input.length() && input.charAt(cursor) == '0') {
                base = 8;
            } else {
                base = 10;
            }
        } else if (base == 16 && hasHexPrefixWithDigit(input, cursor)) {
            cursor += 2;
        }

        int digitsStart = cursor;
        BigInteger magnitude = BigInteger.ZERO;
        while (cursor < input.length()) {
            int value = digit(input.charAt(cursor));
            if (value < 0 || value >= base) {
                break;
            }
            if (magnitude.compareTo(PARSE_CAP) <= 0) {
                magnitude = magnitude.multiply(BigInteger.valueOf(base)).add(BigInteger.valueOf(value));
            }
            cursor++;
        }
        if (cursor == digitsStart) {
            return IntegerToken.none();
        }
        return new IntegerToken(magnitude, negative, cursor, true, false);
    }

    private String input(DebugRuntime runtime, long address) {
        String value = runtime.readCString(address);
        if (value.length() > MAX_INPUT_BYTES) {
            throw new IllegalStateException("numeric input is too long");
        }
        return value;
    }

    private void writeEndPointer(
            DebugRuntime runtime,
            long endPointerAddress,
            long inputAddress,
            int offset
    ) {
        if (endPointerAddress != 0) {
            runtime.write(endPointerAddress, Value.of(IrType.POINTER, inputAddress + offset));
        }
    }

    private boolean floatingRangeError(double value, String token, double minimumNormal) {
        if (Double.isInfinite(value)) {
            return true;
        }
        if (!hasNonZeroSignificand(token)) {
            return false;
        }
        return value == 0.0 || Math.abs(value) < minimumNormal;
    }

    private boolean hasNonZeroSignificand(String token) {
        int start = token.startsWith("+") || token.startsWith("-") ? 1 : 0;
        boolean hexadecimal = start + 1 < token.length()
                && token.charAt(start) == '0'
                && (token.charAt(start + 1) == 'x' || token.charAt(start + 1) == 'X');
        if (hexadecimal) {
            start += 2;
        }
        int end = token.length();
        for (int index = start; index < token.length(); index++) {
            char value = token.charAt(index);
            if (hexadecimal ? value == 'p' || value == 'P' : value == 'e' || value == 'E') {
                end = index;
                break;
            }
        }
        for (int index = start; index < end; index++) {
            int value = digit(token.charAt(index));
            if (value > 0 && value < (hexadecimal ? 16 : 10)) {
                return true;
            }
        }
        return false;
    }

    private int skipSpace(String value, int cursor) {
        while (cursor < value.length() && isSpace(value.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private boolean hasHexPrefixWithDigit(String input, int cursor) {
        return cursor + 2 < input.length()
                && input.charAt(cursor) == '0'
                && (input.charAt(cursor + 1) == 'x' || input.charAt(cursor + 1) == 'X')
                && digit(input.charAt(cursor + 2)) >= 0
                && digit(input.charAt(cursor + 2)) < 16;
    }

    private boolean startsIgnoreCase(String input, int offset, String expected) {
        if (offset + expected.length() > input.length()) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            char actual = input.charAt(offset + index);
            if (actual >= 'A' && actual <= 'Z') {
                actual = (char) (actual + ('a' - 'A'));
            }
            if (actual != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNanPayload(char value) {
        return value == '_'
                || value >= '0' && value <= '9'
                || value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z';
    }

    private boolean isSpace(char value) {
        return value == ' ' || value >= '\t' && value <= '\r';
    }

    private boolean isDecimalDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private int digit(char value) {
        if (value >= '0' && value <= '9') return value - '0';
        if (value >= 'A' && value <= 'Z') return value - 'A' + 10;
        if (value >= 'a' && value <= 'z') return value - 'a' + 10;
        return -1;
    }

    private enum FloatingKind { NUMBER, INFINITY, NAN, NONE }

    private record FloatingToken(
            String text,
            int end,
            boolean converted,
            FloatingKind kind,
            boolean negative
    ) {
        static FloatingToken none() {
            return new FloatingToken("", 0, false, FloatingKind.NONE, false);
        }
    }

    private record IntegerToken(
            BigInteger magnitude,
            boolean negative,
            int end,
            boolean converted,
            boolean invalidBase
    ) {
        static IntegerToken none() {
            return new IntegerToken(BigInteger.ZERO, false, 0, false, false);
        }

        static IntegerToken invalidBaseToken() {
            return new IntegerToken(BigInteger.ZERO, false, 0, false, true);
        }
    }
}
