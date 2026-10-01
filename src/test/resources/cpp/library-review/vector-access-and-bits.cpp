#include <vector>
#include <algorithm>
#include <stdio.h>
int main() {
    std::vector<int> values = {2, 4, 6};
    values.at(1) = 9;
    const std::vector<int>& constant = values;
    if (constant.at(1) != 9 || *values.crbegin() != 6 || values.crend() - values.crbegin() != 3) return 1;
    std::vector<bool> bits(130, true);
    bits.resize(63);
    bits.resize(129, false);
    bits[64] = bits[62];
    std::swap(bits[0], bits[128]);
    bool external = false;
    std::swap(bits[1], external);
    if (!external || bits[0] || bits[1] || !bits[64] || !bits[128]) return 2;
    bits.reserve(400);
    bits.insert(bits.begin() + 64, 3, true);
    bits.erase(bits.begin() + 62, bits.begin() + 66);
    bits.flip();
    std::sort(bits.begin(), bits.end());
    const std::vector<bool>& fixed = bits;
    if (fixed.end() - bits.begin() != (long long)bits.size()) return 3;
    long long ones = 0;
    for (std::vector<bool>::const_iterator i = fixed.begin(); i != fixed.end(); ++i) if (*i) ++ones;
    bits.clear();
    bits.resize(131);
    for (std::vector<bool>::iterator i = bits.begin(); i != bits.end(); ++i) if (*i) return 4;
    printf("%d %llu %lld\n", constant.at(1), bits.size(), ones);
}
