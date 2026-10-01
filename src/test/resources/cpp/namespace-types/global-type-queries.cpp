#include <stdio.h>
namespace A { struct Item { int values[3]; }; }
int item_size = sizeof(A::Item);
int item_alignment = alignof(A::Item);
int array_size = sizeof(A::Item[2]);
int main() {
    printf("%d %d %d\n", item_size, item_alignment, array_size);
    return 0;
}
