#include <map>
#include <set>
#include <stdio.h>
int main() {
    std::map<int, int> original;
    for (int i = 0; i < 32; ++i) original.emplace((i * 13) % 32, i);
    std::map<int, int> copied(original);
    if (copied != original || &*copied.begin() == &*original.begin()) return 1;
    std::map<int, int>::iterator retained = copied.find(18);
    int* address = &retained->second;
    copied.erase(16); copied.erase(copied.begin());
    if (&copied.find(18)->second != address || retained != copied.find(18)) return 2;
    std::map<int, int> exchanged;
    exchanged.swap(copied);
    if (retained != exchanged.find(18) || !copied.empty()) return 3;
    copied = exchanged;
    while (!copied.empty()) {
        std::map<int, int>::iterator last = copied.end(); --last;
        copied.erase(last);
    }
    std::multiset<int> repeated;
    for (int i = 0; i < 36; ++i) repeated.insert(i % 7);
    std::multiset<int> copy(repeated);
    long long removed = copy.erase(3);
    std::multiset<int>::iterator first = copy.begin(); ++first;
    copy.erase(first, copy.end());
    printf("%llu %llu %lld %llu %d\n", original.size(), exchanged.size(), removed, copy.size(), *copy.rbegin());
}
