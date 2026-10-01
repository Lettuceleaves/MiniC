#include <cstdlib>
#include <type_traits>
#include <stdio.h>
static_assert(std::is_same<decltype(std::abs(-1.25f)),float>::value,"cstdlib float abs");
static_assert(std::is_same<decltype(std::abs(-1.25)),double>::value,"cstdlib double abs");
static_assert(std::is_same<decltype(std::abs(-2L)),long>::value,"cstdlib long abs");
static_assert(std::is_same<decltype(std::abs(-3LL)),long long>::value,"cstdlib long long abs");
int main(){printf("%.2f %.2f %ld %lld\n",std::abs(-1.25f),std::abs(-1.25),std::abs(-2L),std::abs(-3LL));return 0;}
