#include <bitset>
// Externally visible, runtime-receiver methods prevent constant input folding.
unsigned long long probe_count(const std::bitset<129>& value) { return value.count(); }
bool probe_all(const std::bitset<129>& value) { return value.all(); }
int main() { std::bitset<129> value(1ULL); return (int)probe_count(value)-1+(probe_all(value)?1:0); }
