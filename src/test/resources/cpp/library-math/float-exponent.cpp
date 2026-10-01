#include <cmath>
#include <float.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
int main(){
    int exponent=0;
    float tiny=std::ldexp(1.0f,-149);
    float fraction=std::frexpf(tiny,&exponent);
    int preserved=std::ldexpf(fraction,exponent)==tiny;
    float zero=std::fabsf(-0.0f);
    unsigned int zeroBits=1;
    memcpy(&zeroBits,&zero,sizeof(zeroBits));
    int positiveZero=zeroBits==0;
    errno=0;
    float overflow=std::ldexpf(1.0f,128);
    int range=errno==ERANGE;
    printf("%.2f %d %d %d %d %d\n",fraction,exponent,preserved,positiveZero,overflow>FLT_MAX,range);
    return 0;
}
