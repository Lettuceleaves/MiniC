#include <stdio.h>

namespace Math { int increment(int value) { return value + 1; } }
namespace Other { int increment(int value) { return value + 10; } }
int increment(int value) { return value + 100; }

int main() {
    int (*first)(int) = &Math::increment;
    int (*second)(int) = Other::increment;
    int (*third)(int) = &::increment;
    using Math::increment;
    int (*fourth)(int) = &increment;
    printf("%d %d %d %d\n", first(1), second(1), third(1), fourth(1));
    return 0;
}
