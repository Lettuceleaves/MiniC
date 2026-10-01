#include <cmath>
#include <type_traits>
#include <stdio.h>
static_assert(std::is_same<decltype(std::sqrt(9.0f)),float>::value,"float sqrt");
static_assert(std::is_same<decltype(std::pow(2.0f,3.0f)),float>::value,"float pow");
static_assert(std::is_same<decltype(std::atan2(0.0f,1.0f)),float>::value,"float atan2");
static_assert(std::is_same<decltype(std::fabs(-1.0f)),float>::value,"float fabs");
static_assert(std::is_same<decltype(std::abs(-1.0f)),float>::value,"float abs");
int main(){
    float (*root)(float)=std::sqrt;
    float integral=0.0f;
    float fraction=std::modf(3.25f,&integral);
    int exponent=0;
    float mantissa=std::frexp(12.0f,&exponent);
    printf("%.2f %.2f %.2f %.2f %.2f %d %.2f\n",root(9.0f),std::pow(2.0f,3.0f),
           integral,fraction,mantissa,exponent,std::ldexp(mantissa,exponent));
    printf("%.2f %.2f %.2f %.2f %.2f %.2f\n",std::sin(0.0f),std::cos(0.0f),std::exp(0.0f),
           std::log(1.0f),std::ceil(1.25f),std::fmod(5.5f,2.0f));
    return 0;
}
