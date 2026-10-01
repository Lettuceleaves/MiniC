#include <iostream>
#include <iomanip>
#include <stdlib.h>
// Retained source; no runtime or header compilation was run during implementation.
int main() {
    std::cout << std::scientific << std::setprecision(2) << 12.5 << ' ' << 0.00125 << '\n';
    std::cout << std::uppercase << std::showpos << 1000.0 << '\n';
    std::cout << std::nouppercase << std::noshowpos << std::defaultfloat << std::setprecision(4) << 120000.0 << ' ' << 0.00012 << '\n';
    std::cout << std::showpoint << std::fixed << std::setprecision(0) << 12.25 << '\n';
    double infinity = strtod("inf", nullptr), nan = strtod("nan", nullptr);
    std::cout << infinity << ' ' << -infinity << ' ' << nan << '\n';
    std::cout << std::uppercase << std::showpos << infinity << ' ' << nan << '\n';
    std::cout << std::nouppercase << std::noshowpos << std::setfill('_') << std::internal << std::setw(7) << -infinity << '\n';
}
