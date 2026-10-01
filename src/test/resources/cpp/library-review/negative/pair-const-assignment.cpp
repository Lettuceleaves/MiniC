#include <utility>
// Expected rejection: a const key must not become assignable through std::pair.
int main() {
    std::pair<const int, int> first(1, 2), second(3, 4);
    first = second;
    return 0;
}
