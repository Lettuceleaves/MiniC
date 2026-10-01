#include <algorithm>
#include <stdio.h>
template<class T> void check(int id) {
    const T source[4]={T(1),T(0),T(3),T(2)};
    T target[6]={T(7),T(7),T(7),T(7),T(7),T(7)};
    T* result=std::copy(source,source+4,target+1);
    printf("%d %lld",id,(long long)(result-target));
    for(int i=0;i<6;++i)printf(" %lld",(long long)target[i]);
    printf("\n");
}
int main() {
    check<bool>(0);check<char>(1);check<signed char>(2);check<unsigned char>(3);
    check<short>(4);check<unsigned short>(5);check<int>(6);check<unsigned int>(7);
    check<long>(8);check<unsigned long>(9);check<long long>(10);check<unsigned long long>(11);
    return 0;
}
