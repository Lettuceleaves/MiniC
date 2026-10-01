#include <algorithm>
#include <utility>
#include <map>
#include <stdio.h>
struct SameParity { bool operator()(int a, int b) const { return a % 2 == b % 2; } };
struct Payload {
    int value;
    explicit Payload(int v):value(v) {}
    Payload(const Payload& other):value(other.value) {}
    Payload(Payload&& other):value(other.value) { other.value = -1; }
    Payload& operator=(Payload&& other) { value = other.value; other.value = -1; return *this; }
};
int main() {
    int a[3] = {1, 2, 3}, b[4] = {1, 2, 3, 4}, parity[3] = {5, 6, 7};
    if (std::equal(a, a + 3, b, b + 4)) return 1;
    if (!std::equal(a, a + 3, b, b + 3)) return 2;
    if (!std::equal(a, a + 3, parity, parity + 3, SameParity())) return 3;
    int x = 1, y = 2, u = 8, v = 9;
    std::pair<int&, int&> left(x, y), right(u, v);
    left = right;
    if (x != 8 || y != 9 || &left.first != &x || &left.second != &y) return 4;
    left = std::pair<short, short>(3, 4);
    std::pair<Payload, Payload> moves(Payload(10), Payload(20));
    std::pair<Payload, Payload> target(Payload(0), Payload(0));
    target = std::move(moves);
    if (target.first.value != 10 || moves.first.value != -1) return 5;
    std::map<int, Payload> values;
    values.try_emplace(values.end(), 2, 20);
    Payload held(30);
    values.try_emplace(values.begin(), 2, std::move(held));
    if (held.value != 30) return 6;
    values.insert_or_assign(values.end(), 3, Payload(31));
    values.insert_or_assign(values.begin(), 2, Payload(21));
    values.insert_or_assign(values.end(), 1, Payload(11)); // Deliberately wrong hint.
    printf("%d %d %d %d %d\n", x, y, values.begin()->second.value, values.at(2).value, values.at(3).value);
}
