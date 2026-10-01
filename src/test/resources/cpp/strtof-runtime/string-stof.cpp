#include <string>
#include <stdio.h>
#include <string.h>
int main() {
    std::string::size_type used=0;
    float first=std::stof("1.00000005960464477539062500000000000000000001rest",&used);
    unsigned int bits=0;memcpy(&bits,&first,sizeof(first));
    printf("%x %llu %.2f\n",bits,(unsigned long long)used,(double)std::stof("1.25"));
}
