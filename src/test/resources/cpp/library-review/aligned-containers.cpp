#include <vector>
#include <deque>
#include <map>
#include <set>
#include <stdio.h>
struct Aligned {
    alignas(64) int value;
    explicit Aligned(int v):value(v) {}
    Aligned(const Aligned& other):value(other.value) {}
    Aligned(Aligned&& other):value(other.value) {}
    Aligned& operator=(const Aligned& other) { value = other.value; return *this; }
    bool operator<(const Aligned& other) const { return value < other.value; }
};
int main() {
    std::vector<Aligned> values;
    for (int i = 0; i < 20; ++i) values.emplace_back(i);
    for (int i = 0; i < 20; ++i) if ((unsigned long long)&values[i] % alignof(Aligned)) return 1;
    std::deque<Aligned> segmented;
    for (int i = 0; i < 20; ++i) segmented.emplace_front(i);
    for (int i = 0; i < 20; ++i) if ((unsigned long long)&segmented[i] % alignof(Aligned)) return 2;
    std::map<int, Aligned> indexed;
    for (int i = 0; i < 20; ++i) indexed.try_emplace(i, i);
    for (std::map<int, Aligned>::iterator p = indexed.begin(); p != indexed.end(); ++p)
        if ((unsigned long long)&p->second % alignof(Aligned)) return 3;
    std::set<Aligned> ordered;
    for (int i = 0; i < 20; ++i) ordered.emplace(i);
    for (std::set<Aligned>::iterator p = ordered.begin(); p != ordered.end(); ++p)
        if ((unsigned long long)&*p % alignof(Aligned)) return 4;
    printf("%d %d %d %d\n", values.back().value, segmented.front().value, indexed.rbegin()->second.value, ordered.rbegin()->value);
}
