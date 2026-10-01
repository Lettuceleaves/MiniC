#include <cmath>
#include <type_traits>
#include <stdio.h>
static_assert(std::is_same<decltype(std::sqrt(9)),double>::value,"integer sqrt");
static_assert(std::is_same<decltype(std::sqrt(9LL)),double>::value,"wide integer sqrt");
static_assert(std::is_same<decltype(std::pow(2.0f,3)),double>::value,"integer exponent promotes");
static_assert(std::is_same<decltype(std::pow(2,3.0f)),double>::value,"integer base promotes");
static_assert(std::is_same<decltype(std::pow(2.0,3.0f)),double>::value,"mixed precision");
static_assert(std::is_same<decltype(std::atan2(0,1.0f)),double>::value,"mixed atan2");
static_assert(std::is_same<decltype(std::fabs(-3)),double>::value,"fabs integer");
static_assert(std::is_same<decltype(std::ldexp(3,2)),double>::value,"ldexp integer");
int main(){
    printf("%.2f %.2f %.2f %.2f %.2f\n",std::sqrt(9),std::pow(2.0f,3),std::pow(2,3.0f),
           std::atan2(0,1.0f),std::ldexp(3,2));
    return 0;
}
