#include <cmath>
#include <float.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#if !defined(__MINIC__) && !defined(__MINIC_SELF_STL__) && defined(__MINGW32__) && defined(__GLIBCXX__) && __GLIBCXX__ == 20180502
// This MinGW libstdc++ omits these std f-suffix names required by N4659.
// Its corresponding float overloads provide the same arithmetic oracle.
// https://timsong-cpp.github.io/cppwp/n4659/c.math
#define TEST_FREXPF std::frexp
#define TEST_LDEXPF std::ldexp
#define TEST_FABSF std::fabs
#define TEST_HOST_LDEXPF_ERRNO_GAP 1
#else
#define TEST_FREXPF std::frexpf
#define TEST_LDEXPF std::ldexpf
#define TEST_FABSF std::fabsf
#endif
int main(){
    int exponent=0;
    float tiny=std::ldexp(1.0f,-149);
    float fraction=TEST_FREXPF(tiny,&exponent);
    int preserved=TEST_LDEXPF(fraction,exponent)==tiny;
    float zero=TEST_FABSF(-0.0f);
    unsigned int zeroBits=1;
    memcpy(&zeroBits,&zero,sizeof(zeroBits));
    int positiveZero=zeroBits==0;
    errno=0;
    float overflow=TEST_LDEXPF(1.0f,128);
#if defined(TEST_HOST_LDEXPF_ERRNO_GAP)
    // This CRT implements ldexpf as float(ldexp(double,...)): the float-only
    // overflow leaves errno zero, including a volatile function-pointer call.
    // Verify that known reference behavior; own/MiniC still require ERANGE.
    int errorContract=errno==0;
#else
    int errorContract=errno==ERANGE;
#endif
    printf("%.2f %d %d %d %d %d\n",fraction,exponent,preserved,positiveZero,overflow>FLT_MAX,errorContract);
    return 0;
}
