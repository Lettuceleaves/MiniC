#include <algorithm>
#include <cstdio>
// Six fixed distributions; no library-internal pivot/fallback inspection.
int input_value(int mode,int i,int n){
    if(mode==0)return i;
    if(mode==1)return n-1-i;
    if(mode==2)return 7;
    if(mode==3)return (i*37)%5;
    if(mode==4)return i<n/2?i:n-i;
    // Alternating low/high odd ranks followed by even ranks: a classic
    // deterministic median-of-three stress input, still a strict ordering.
    if(i<n/2)return i%2==0?i+1:n/2+i;
    return 2*(i-n/2)+2;
}
int main(){int n=0;if(::scanf("%d",&n)!=1||n<8||n>4096||n%4)return 2;
    int values[4096];
    for(int mode=0;mode<6;++mode){
        for(int i=0;i<n;++i)values[i]=input_value(mode,i,n);
        comparisons=0;
#ifdef MINIC_COMPLEXITY_QUADRATIC_CONTROL
        // Correct result, deliberately quadratic comparisons: the acceptance
        // checker must reject this control rather than merely checking output.
        for(int i=0;i<n;++i)for(int j=i+1;j<n;++j)if(CountLess()(values[j],values[i]))std::swap(values[i],values[j]);
#else
        std::sort(values,values+n,CountLess());
#endif
        unsigned long long checksum=0;
        for(int i=0;i<n;++i){if(i&&values[i]<values[i-1])return 3;checksum=(checksum*131+(unsigned long long)values[i]+10000)%1000000007ULL;}
        ::printf("sort case=%d n=%d comparisons=%llu checksum=%llu\n",mode,n,comparisons,checksum);
    }
    return 0;
}
