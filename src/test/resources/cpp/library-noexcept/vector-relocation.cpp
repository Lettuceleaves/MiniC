#include <vector>
#include <stdio.h>
int copies=0,moves=0;
struct Item {
    int value;
    Item(int n):value(n) {}
    Item(const Item& other):value(other.value) { ++copies; }
    Item(Item&& other) noexcept(false):value(other.value) { other.value=-1;++moves; }
    Item& operator=(const Item& other) { value=other.value;return *this; }
    Item& operator=(Item&& other) noexcept(false) { value=other.value;other.value=-1;return *this; }
};
int main() {
    std::vector<Item> values;
    values.reserve(1);
    values.emplace_back(7);
    copies=0;moves=0;
    values.reserve(4);
    printf("%d %d %d\n",values[0].value,copies,moves);
    return 0;
}
