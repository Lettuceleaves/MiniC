#include <stdio.h>

namespace outer {
    int counter = 1;
    int twice(int value) { return value * 2; }
    namespace inner {
        int bias = 3;
        int apply(int value) {
            counter += value;
            return twice(value) + bias;
        }
    }
}
namespace outer {
    int take() { return counter; }
}
namespace compact::nested {
    int value = 6;
}
namespace compact::nested {
    int read() { return value; }
}
int main() {
    outer::counter = 4;
    int value = outer::inner::apply(5);
    printf("%d %d %d %d\n", value, outer::take(), outer::counter, compact::nested::read());
    return 0;
}
