#include <stdio.h>

int value = 3;
namespace A {
    int before() { return value; }
    int value = 8;
    int after() { return value; }
    int factorial(int number);
    int call(int number) { return factorial(number); }
    int factorial(int number) {
        if (number < 2) return 1;
        return number * factorial(number - 1);
    }
}
namespace Parity {
    int odd(int number);
    int even(int number) {
        if (number == 0) return 1;
        return odd(number - 1);
    }
    int odd(int number) {
        if (number == 0) return 0;
        return even(number - 1);
    }
}
int main() {
    printf("%d %d %d %d %d\n", A::before(), A::after(), A::call(5), Parity::even(8), Parity::odd(8));
    return 0;
}
