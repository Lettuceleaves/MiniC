#include <bitset>
#include <iostream>
#include <iomanip>
// stdin: "x 101q 11"
// stdout: "0011 1 __0101 q 1 00000011 1 0\n"
int main() {
    std::bitset<4> custom("aabb", std::string::npos, 'a', 'b');
    char prefix = 0;
    std::cin.get(prefix);
    std::bitset<4> bits;
    std::cin >> bits;
    long long preserved = std::cin.gcount();
    char separator = 0;
    std::cin >> separator;
    std::bitset<0> empty;
    std::cin >> empty;
    bool zero_ok = !std::cin.fail();
    std::bitset<8> tail;
    std::cin >> tail;
    std::cout << custom << ' ' << preserved << ' ' << std::setfill('_') << std::setw(6) << bits
              << ' ' << separator << ' ' << zero_ok << ' ' << tail << ' ' << std::cin.eof() << ' ' << std::cin.fail() << '\n';
}
