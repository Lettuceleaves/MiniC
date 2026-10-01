#include <iostream>
// stdin: "a \n42 true   "
// stdout: "a 1 42 1 1 0 0 1 0 1\n+1\n"
int main() {
    char first = 0;
    std::cin.get(first);
    std::cin >> std::ws;
    long long before = std::cin.gcount();
    int number = 0;
    bool value = false;
    std::cin >> number >> std::boolalpha >> value;
    std::cin.setstate(std::ios::failbit);
    bool unchanged = true;
    std::cin >> unchanged;
    std::cin.unget();
    bool unget_bad = std::cin.bad();
    std::cin.putback('x');
    bool putback_bad = std::cin.bad();
    std::cin.clear();
    std::cin.get(first); // Consume one of the trailing spaces; gcount becomes one.
    std::cin >> std::ws;
    std::cout << 'a' << ' ' << before << ' ' << number << ' ' << value
              << ' ' << unchanged << ' ' << unget_bad << ' ' << putback_bad
              << ' ' << std::cin.eof() << ' ' << std::cin.fail() << ' ' << std::cin.gcount() << '\n';
    std::cout << std::showpos << true << '\n';
}
