#include <bitset>
#include <cstdio>
int main(){int cases;std::scanf("%d",&cases);while(cases--){int n;std::scanf("%d",&n);std::bitset<129> sums;sums.set(0);
 for(int i=0;i<n;++i){int weight;std::scanf("%d",&weight);sums|=sums<<weight;}
 std::printf("%llu ",(unsigned long long)sums.count());for(int i=0;i<129;++i)std::printf(sums[i]?"1":"0");std::printf("\n");}}
