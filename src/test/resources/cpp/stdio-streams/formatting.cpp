#include <stdio.h>
// Raw C formatting: retained native/debug parity case; MSVCRT uses 3-digit exponents.
// The self-hosted C++ iostream layer has its own two-digit exponent normalization.
int main() {
    char text[512];
    int written = sprintf(text, "%+08d|%#o|%#X|%*.*f|%.3e|%.3g|%#.*g", 42, 493, 255, -10, 2, 12.5, 12.5, 1234567.0, 4, 3.0);
    printf("%d %s\n", written, text);
    char short_text[6];
    int needed = snprintf(short_text, 6, "%06d", 123);
    printf("%d %s\n", needed, short_text);
    printf("%.3s\n", "你xy");
    return 0;
}
