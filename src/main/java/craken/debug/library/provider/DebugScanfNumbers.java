package craken.debug;

import java.math.BigInteger;
import java.util.Locale;

/** scanf input items, bounded by field width, leave the first conflicting byte unread. */
final class DebugScanfNumbers {
    private DebugScanfNumbers() { }

    interface Input {
        int peekCharacter();
        int readCharacter();
    }

    static String readInteger(Input input, int width, boolean automaticBase) {
        Item item = new Item(input, width);
        item.sign();
        int radix = 10;
        if (automaticBase && item.peek() == '0') {
            item.take();radix = 8;
            if (item.peek() == 'x' || item.peek() == 'X') { item.take();radix = 16; }
        }
        item.digits(radix);
        return item.toString();
    }

    static long integer(String token, boolean automaticBase) {
        int at = token.startsWith("+") || token.startsWith("-") ? 1 : 0;
        boolean negative = token.startsWith("-");
        int radix = 10;
        if (automaticBase && token.startsWith("0", at)) {
            radix = 8;
            if (token.startsWith("0x", at) || token.startsWith("0X", at)) { radix = 16;at += 2; }
        }
        BigInteger value = new BigInteger(token.substring(at), radix);
        // Destination-width wrapping is performed by DebugRuntime.Value, including %u -1.
        return (negative ? value.negate() : value).longValue();
    }

    static String readFloating(Input input, int width) {
        Item item = new Item(input, width);
        item.sign();
        if (lower(item.peek()) == 'i') {
            boolean complete = item.word("inf");
            if (complete && lower(item.peek()) == 'i') item.word("inity");
            return item.toString();
        }
        if (lower(item.peek()) == 'n') {
            boolean complete = item.word("nan");
            if (complete && item.peek() == '(') {
                item.take();
                while (item.peek() == '_' || asciiLetter(item.peek()) || digit(item.peek(), 10)) item.take();
                if (item.peek() == ')') item.take();
            }
            return item.toString();
        }
        boolean hex = false;
        boolean mantissa = false;
        if (item.peek() == '0') {
            item.take();mantissa = true;
            if (lower(item.peek()) == 'x') { item.take();hex = true;mantissa = false; }
        }
        mantissa |= item.digits(hex ? 16 : 10);
        if (item.peek() == '.') { item.take();mantissa |= item.digits(hex ? 16 : 10); }
        if (mantissa && lower(item.peek()) == (hex ? 'p' : 'e')) { item.take();item.sign();item.digits(10); }
        return item.toString();
    }

    static double floating(String token, boolean singlePrecision) {
        boolean negative = token.startsWith("-");
        String unsigned = token.startsWith("+") || negative ? token.substring(1) : token;
        String lowered = unsigned.toLowerCase(Locale.ROOT);
        if (lowered.equals("inf") || lowered.equals("infinity"))
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        if (lowered.equals("nan") || lowered.startsWith("nan(") && lowered.endsWith(")"))
            return Math.copySign(Double.NaN, negative ? -1.0 : 1.0);
        if (lowered.startsWith("0x") && !lowered.contains("p")) token += "p0";
        return singlePrecision ? Float.parseFloat(token) : Double.parseDouble(token);
    }

    private static boolean asciiLetter(int character) {
        return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z';
    }
    private static int lower(int character) {
        return character >= 'A' && character <= 'Z' ? character + ('a' - 'A') : character;
    }
    private static boolean digit(int character, int radix) {
        int value = character >= '0' && character <= '9' ? character - '0'
                : character >= 'a' && character <= 'f' ? character - 'a' + 10
                : character >= 'A' && character <= 'F' ? character - 'A' + 10 : -1;
        return value >= 0 && value < radix;
    }

    private static final class Item {
        private final Input input;
        private int remaining;
        private final StringBuilder value = new StringBuilder();
        Item(Input input, int width) { this.input=input;this.remaining=width; }
        int peek() { return remaining == 0 ? -1 : input.peekCharacter(); }
        void take() { value.append((char)input.readCharacter());--remaining; }
        void sign() { if (peek() == '+' || peek() == '-') take(); }
        boolean digits(int radix) { int before=value.length();while (digit(peek(), radix)) take();return value.length()>before; }
        boolean word(String word) { for(int i=0;i<word.length();++i){if(lower(peek())!=word.charAt(i))return false;take();}return true; }
        @Override public String toString() { return value.toString(); }
    }
}
