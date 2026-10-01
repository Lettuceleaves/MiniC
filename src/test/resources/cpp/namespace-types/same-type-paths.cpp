#include <stdio.h>
namespace A {
    struct Value { int number; };
    typedef Value Alias;
}
namespace B { using A::Value; using A::Alias; }
namespace C { using namespace A; using namespace B; }
using namespace C;
int main() {
    Value first = {6};
    Alias second = first;
    B::Alias third = {8};
    second = third;
    printf("%d %d\n", first.number, second.number);
    return 0;
}
