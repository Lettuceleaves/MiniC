#include <stdio.h>

namespace A {
    int value = 12;
    int twice(int value) { return value * 2; }
}
namespace B { using namespace A; using A::twice; }
namespace C { using namespace A; using B::twice; }
namespace A { using namespace C; }
using namespace B;
using namespace C;

int main() {
    using A::value;
    value += 3;
    int (*operation)(int) = &twice;
    printf("%d %d\n", value, operation(value));
    return 0;
}
