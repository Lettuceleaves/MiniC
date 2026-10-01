#include <stdio.h>
namespace Shapes {
    typedef int Scalar;
    struct Pair { Scalar left; Scalar right; };
    typedef Pair PairAlias;
}
Shapes::Pair origin = {1, 2};
Shapes::Pair make_pair(Shapes::Scalar value) {
    Shapes::Pair result = {value, value + 1};
    return result;
}
Shapes::Scalar sum(Shapes::Pair pair) {
    return pair.left + pair.right;
}
int main() {
    Shapes::PairAlias local = make_pair((Shapes::Scalar)4);
    origin.left = local.right;
    local.right += origin.right;
    printf("%d %d %d %d\n", sum(local), origin.left,
           (int)sizeof(Shapes::Pair), (int)sizeof(Shapes::Scalar));
    return 0;
}
