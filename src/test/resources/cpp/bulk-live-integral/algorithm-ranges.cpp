#include <algorithm>
#include <stdio.h>
int main() {
    int sizes[12]={0,1,2,3,7,8,15,16,17,31,32,33};
    for(int op=0;op<4;++op) for(int c=0;c<12;++c) {
        int a[42]; for(int i=0;i<42;++i)a[i]=i*13-201;
        int n=sizes[c]; int source=(op==0||op==2)?4:1; int target=(op==0||op==2)?1:4;
        int* result;
        if(op==0)result=std::copy(a+source,a+source+n,a+target);
        else if(op==1)result=std::copy_backward(a+source,a+source+n,a+target+n);
        else if(op==2)result=std::move(a+source,a+source+n,a+target);
        else result=std::move_backward(a+source,a+source+n,a+target+n);
        printf("%d %d %lld",op,n,(long long)(result-a));
        for(int i=0;i<42;++i)printf(" %d",a[i]);
        printf("\n");
    }
    int* empty=nullptr;
    if(std::copy(empty,empty,empty)!=empty)return 1;
    if(std::copy_backward(empty,empty,empty)!=empty)return 2;
    if(std::move(empty,empty,empty)!=empty)return 3;
    if(std::move_backward(empty,empty,empty)!=empty)return 4;
    printf("empty-ok\n");
    return 0;
}
