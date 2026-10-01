#include <stdio.h>
namespace A { struct Item { int value; }; }
namespace B { struct Item { int left; int right; }; }
A::Item first = {2};
B::Item second = {3, 4};
int main() {
    A::Item local_a = first;
    B::Item local_b = second;
    local_a.value += local_b.right;
    local_b.right = local_a.value + local_b.left;
    printf("%d %d %d %d\n", local_a.value, local_b.right,
           (int)sizeof(A::Item), (int)sizeof(B::Item));
    return 0;
}
