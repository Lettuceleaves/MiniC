#include <stdio.h>

int choose() { return 10; }
namespace A { int choose() { return 20; } int first = 1; }
namespace B {
    int before() { return choose(); }
    using A::choose;
    int after() { return choose(); }
}
using namespace A;
namespace A { int second = 2; }

int main() {
    printf("%d %d %d\n", B::before(), B::after(), first + second);
    return 0;
}
