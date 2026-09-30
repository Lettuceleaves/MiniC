#include <stdio.h>

int value = 100;
namespace A { int value = 4; }
namespace B {
    using namespace A;
    int value = 7;
    int read() { return value; }
}
namespace Outer {
    int value = 20;
    namespace Imported { int value = 30; }
    namespace Inner {
        int value = 40;
        using namespace Imported;
        int read() { return value; }
    }
}
int main() {
    {
        using namespace A;
        int value = 9;
        printf("%d ", value);
    }
    printf("%d %d %d\n", ::value, B::read(), Outer::Inner::read());
    return 0;
}
