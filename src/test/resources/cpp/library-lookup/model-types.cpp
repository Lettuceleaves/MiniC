#include <cstddef>
#include <cstdint>
#include <stdio.h>
std::nullptr_t null_value=nullptr;
static_assert(sizeof(std::int_least8_t)>=1 && sizeof(std::uint_least8_t)>=1,"least8");
static_assert(sizeof(std::int_least16_t)>=2 && sizeof(std::uint_least16_t)>=2,"least16");
static_assert(sizeof(std::int_least32_t)>=4 && sizeof(std::uint_least32_t)>=4,"least32");
static_assert(sizeof(std::int_least64_t)>=8 && sizeof(std::uint_least64_t)>=8,"least64");
static_assert(sizeof(std::int_fast8_t)>=1 && sizeof(std::uint_fast8_t)>=1,"fast8");
static_assert(sizeof(std::int_fast16_t)>=2 && sizeof(std::uint_fast16_t)>=2,"fast16");
static_assert(sizeof(std::int_fast32_t)>=4 && sizeof(std::uint_fast32_t)>=4,"fast32");
static_assert(sizeof(std::int_fast64_t)>=8 && sizeof(std::uint_fast64_t)>=8,"fast64");
static_assert(alignof(std::max_align_t)>=alignof(double),"fundamental alignment");
int main(){std::intmax_t signed_value=-9;std::uintmax_t unsigned_value=12;
    int* pointer=null_value;std::size_t size=3;std::ptrdiff_t distance=-2;
    printf("%d %d %d %d\n",pointer==nullptr,(int)(signed_value+unsigned_value),(int)size,(int)distance);return 0;}
