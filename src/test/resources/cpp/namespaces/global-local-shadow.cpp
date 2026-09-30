#include <stdio.h>

int value = 100;
namespace A {
    int value = 4;
    int read() {
        int value = 7;
        return value + A::value + ::value;
    }
}
int main() {
    int value = 9;
    ::value = 11;
    A::value = 5;
    printf("%d %d %d %d\n", value, ::value, A::value, A::read());
    return 0;
}
