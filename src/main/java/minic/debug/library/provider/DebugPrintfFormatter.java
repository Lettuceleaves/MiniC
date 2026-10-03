package minic.debug;

import minic.debug.DebugRuntime.Value;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/** Narrow MSVCRT formatting. Each Java character in the result carries one byte. */
final class DebugPrintfFormatter {
    private static final int MAX_RENDERED_BYTES = 1024 * 1024;

    private DebugPrintfFormatter() {}

    static String render(DebugRuntime runtime, List<Value> arguments, int formatIndex) {
        String format = runtime.readCString(arguments.get(formatIndex).integer());
        Cursor cursor = new Cursor(format, arguments, formatIndex + 1);
        StringBuilder output = new StringBuilder();
        while (cursor.offset < format.length()) {
            char c = format.charAt(cursor.offset++);
            if (c != '%') {
                output.append(c);
            } else if (cursor.at('%')) {
                cursor.offset++;
                output.append('%');
            } else {
                Directive directive = cursor.directive();
                String rendered = value(runtime, directive, cursor.argument());
                if (rendered.length() > MAX_RENDERED_BYTES - output.length()) limit();
                output.append(rendered);
            }
            if (output.length() > MAX_RENDERED_BYTES) limit();
        }
        return output.toString();
    }

    private static String value(DebugRuntime runtime, Directive d, Value value) {
        if ("diuoxXfFeEgGcsp".indexOf(d.conversion) < 0)
            throw new IllegalStateException("Unsupported printf conversion: %" + d.conversion);
        char conversion = Character.toLowerCase(d.conversion);
        if ("diuox".indexOf(conversion) >= 0) return integer(d, value.integer());
        if ("feg".indexOf(conversion) >= 0) {
            if (!d.length.isEmpty() && !d.length.equals("l") && !d.length.equals("L")) unsupportedLength(d);
            return floating(d, value.real());
        }
        if (!d.length.isEmpty()) unsupportedLength(d);
        return switch (conversion) {
            case 'c' -> pad(d, "", Character.toString((char) (value.integer() & 0xff)), false);
            case 's' -> pad(d, "", string(runtime, value.integer(), d.precision), false);
            // The native Windows x64 CRT prints a fixed-width, uppercase address without 0x.
            case 'p' -> pad(d, "", zeroes(16 - Long.toUnsignedString(value.integer(), 16).length())
                    + Long.toUnsignedString(value.integer(), 16).toUpperCase(Locale.ROOT), false);
            default -> throw new IllegalStateException("Unsupported printf conversion: %" + d.conversion);
        };
    }

    private static String string(DebugRuntime runtime, long pointer, int precision) {
        if (pointer == 0) return precision < 0 ? "(null)" : "(null)".substring(0, Math.min(precision, 6));
        StringBuilder result = new StringBuilder();
        int limit = precision < 0 ? MAX_RENDERED_BYTES : precision;
        for (int i = 0; i < limit; i++) {
            int c = runtime.readUnsignedByte(pointer + i);
            if (c == 0) return result.toString();
            result.append((char) c);
        }
        if (precision < 0) throw new IllegalStateException("String is not null terminated");
        return result.toString();
    }

    private static String integer(Directive d, long raw) {
        int bits = switch (d.length) {
            case "hh" -> 8;
            case "h" -> 16;
            case "", "l", "I32" -> 32; // Windows LLP64: long is not long long.
            case "ll", "I64" -> 64;
            default -> { unsupportedLength(d); yield 0; }
        };
        char conversion = Character.toLowerCase(d.conversion);
        boolean signed = conversion == 'd' || conversion == 'i';
        if (bits < 64) raw = signed ? raw << (64 - bits) >> (64 - bits) : raw & ((1L << bits) - 1);
        int radix = conversion == 'o' ? 8 : conversion == 'x' ? 16 : 10;
        boolean negative = signed && raw < 0;
        String digits = Long.toUnsignedString(negative ? -raw : raw, radix);
        if (d.precision == 0 && raw == 0) digits = "";
        digits = zeroes(Math.max(0, d.precision - digits.length())) + digits;
        String prefix = signed ? sign(d, negative) : "";
        if (d.has('#')) {
            if (conversion == 'o' && (digits.isEmpty() || digits.charAt(0) != '0')) digits = '0' + digits;
            if (conversion == 'x' && raw != 0) prefix = "0x";
        }
        if (d.conversion == 'X') {
            prefix = prefix.toUpperCase(Locale.ROOT);
            digits = digits.toUpperCase(Locale.ROOT);
        }
        return pad(d, prefix, digits, d.precision < 0);
    }

    private static String floating(Directive d, double value) {
        boolean negative = Double.doubleToRawLongBits(value) < 0;
        String prefix = sign(d, negative);
        char conversion = Character.toLowerCase(d.conversion);
        int precision = d.precision < 0 ? 6 : d.precision;
        if (conversion == 'g' && precision == 0) precision = 1;
        if (!Double.isFinite(value)) return pad(d, prefix, legacyNonfinite(d, value, precision, negative), true);

        // Exact binary value, not Double.toString's shortest decimal. The legacy MSVCRT
        // default rounds ties away from zero; UCRT's newer standard-rounding mode differs.
        BigDecimal exact = new BigDecimal(Math.abs(value));
        String body;
        if (conversion == 'f') {
            body = exact.setScale(precision, RoundingMode.HALF_UP).toPlainString();
        } else if (conversion == 'e') {
            body = scientific(exact, precision, d.has('#'), false);
        } else {
            BigDecimal rounded = exact.round(new MathContext(precision, RoundingMode.HALF_UP));
            int exponent = exponent(rounded);
            if (exponent < -4 || exponent >= precision) {
                body = scientific(rounded, precision - 1, d.has('#'), !d.has('#'));
            } else {
                body = rounded.setScale(Math.max(0, precision - exponent - 1), RoundingMode.HALF_UP).toPlainString();
                if (!d.has('#')) body = stripFractionZeroes(body);
            }
        }
        if (d.has('#') && body.indexOf('.') < 0 && body.indexOf('e') < 0) body += '.';
        if (Character.isUpperCase(d.conversion)) body = body.toUpperCase(Locale.ROOT);
        return pad(d, prefix, body, true);
    }

    private static String scientific(BigDecimal value, int precision, boolean alternate, boolean trim) {
        int exponent = exponent(value);
        BigDecimal mantissa = value.movePointLeft(exponent).setScale(precision, RoundingMode.HALF_UP);
        if (mantissa.compareTo(BigDecimal.TEN) >= 0) {
            exponent++;
            mantissa = mantissa.movePointLeft(1).setScale(precision, RoundingMode.HALF_UP);
        }
        String digits = mantissa.toPlainString();
        if (trim) digits = stripFractionZeroes(digits);
        if (alternate && digits.indexOf('.') < 0) digits += '.';
        String magnitude = Integer.toString(Math.abs(exponent));
        return digits + 'e' + (exponent < 0 ? '-' : '+') + zeroes(3 - magnitude.length()) + magnitude;
    }

    private static int exponent(BigDecimal value) {
        return value.signum() == 0 ? 0 : value.precision() - value.scale() - 1;
    }

    private static String stripFractionZeroes(String value) {
        if (value.indexOf('.') < 0) return value;
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '0') end--;
        if (end > 0 && value.charAt(end - 1) == '.') end--;
        return value.substring(0, end);
    }

    private static String legacyNonfinite(Directive d, double value, int precision, boolean negative) {
        char conversion = Character.toLowerCase(d.conversion);
        String fraction = Double.isInfinite(value) ? "#INF" : negative ? "#IND" : "#QNAN";
        int places = conversion == 'g' ? precision - 1 : precision;
        String body;
        if (places == 0) body = "1";
        else if (places < fraction.length()) {
            String prefix = fraction.substring(0, places);
            body = "1." + prefix.substring(0, places - 1) + (char) (prefix.charAt(places - 1) + 1);
        } else {
            body = "1." + fraction;
            if (conversion != 'g' || d.has('#')) body += zeroes(places - fraction.length());
        }
        if (conversion == 'e') body += Character.isUpperCase(d.conversion) ? "E+000" : "e+000";
        return body;
    }

    private static String sign(Directive d, boolean negative) {
        return negative ? "-" : d.has('+') ? "+" : d.has(' ') ? " " : "";
    }

    private static String pad(Directive d, String prefix, String value, boolean numeric) {
        int padding = Math.max(0, d.width - prefix.length() - value.length());
        if (d.has('-')) return prefix + value + " ".repeat(padding);
        if (numeric && d.has('0')) return prefix + zeroes(padding) + value;
        return " ".repeat(padding) + prefix + value;
    }

    private static String zeroes(int count) { return "0".repeat(Math.max(0, count)); }
    private static void unsupportedLength(Directive d) {
        throw new IllegalStateException("Unsupported printf length: %" + d.length + d.conversion);
    }
    private static void limit() {
        throw new IllegalStateException("Formatted output exceeds the debugger's one-megabyte rendering limit");
    }

    private record Directive(char conversion, String flags, int width, int precision, String length) {
        boolean has(char flag) { return flags.indexOf(flag) >= 0; }
    }

    private static final class Cursor {
        private final String format;
        private final List<Value> arguments;
        private int offset;
        private int argumentIndex;

        Cursor(String format, List<Value> arguments, int argumentIndex) {
            this.format = format;
            this.arguments = arguments;
            this.argumentIndex = argumentIndex;
        }
        boolean at(char c) { return offset < format.length() && format.charAt(offset) == c; }
        Value argument() {
            if (argumentIndex >= arguments.size()) throw new IllegalStateException("printf argument count does not match format");
            return arguments.get(argumentIndex++);
        }
        int decimal() {
            int result = 0;
            while (offset < format.length() && format.charAt(offset) >= '0' && format.charAt(offset) <= '9') {
                int digit = format.charAt(offset++) - '0';
                if (result > (MAX_RENDERED_BYTES - digit) / 10) limit();
                result = result * 10 + digit;
            }
            return result;
        }
        Directive directive() {
            int flagsStart = offset;
            while (offset < format.length() && "-+ #0".indexOf(format.charAt(offset)) >= 0) offset++;
            String flags = format.substring(flagsStart, offset);
            int width;
            if (at('*')) {
                offset++;
                long supplied = (int) argument().integer();
                if (supplied < 0) { flags += '-'; supplied = -supplied; }
                if (supplied > MAX_RENDERED_BYTES) limit();
                width = (int) supplied;
            } else width = decimal();
            int precision = -1;
            if (at('.')) {
                offset++;
                if (at('*')) {
                    offset++;
                    precision = (int) argument().integer();
                    if (precision > MAX_RENDERED_BYTES) limit();
                    if (precision < 0) precision = -1;
                } else precision = decimal();
            }
            String length = "";
            if (format.startsWith("I64", offset) || format.startsWith("I32", offset)) {
                length = format.substring(offset, offset + 3); offset += 3;
            } else if (at('h') || at('l')) {
                char marker = format.charAt(offset++); length = Character.toString(marker);
                if (at(marker)) { length += marker; offset++; }
            } else if (at('L') || at('z') || at('j') || at('t')) {
                length = Character.toString(format.charAt(offset++));
            }
            if (offset == format.length()) throw new IllegalStateException("Incomplete format directive");
            return new Directive(format.charAt(offset++), flags, width, precision, length);
        }
    }
}
