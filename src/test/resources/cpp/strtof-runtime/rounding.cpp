#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
int main() {
    const char* text="1.00000005960464477539062500000000000000000001rest";
    char* end=0;errno=17;
    float value=strtof(text,&end);unsigned int bits=0;
    memcpy(&bits,&value,sizeof(value));
    printf("%x %d %c\n",bits,errno,*end);
    value=strtof("1e100",&end);printf("%d %d\n",errno,*end==0);
    return 0;
}
