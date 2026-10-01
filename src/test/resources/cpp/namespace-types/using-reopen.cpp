#include <stdio.h>
namespace Data {
    typedef int Count;
    struct Cell { Count value; };
    typedef Cell Item;
}
namespace Data {
    typedef Item Clone;
    Count next(Count value) { return value + 1; }
    Clone copy(Item input) { return input; }
}
using Data::Count;
using Data::Item;
int main() {
    Count count = Data::next(4);
    Item first = {count};
    Data::Clone second = Data::copy(first);
    {
        using namespace Data;
        Clone third = {8};
        second = third;
    }
    first.value += second.value;
    printf("%d %d %d\n", count, first.value, second.value);
    return 0;
}
