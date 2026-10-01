#include <bitset>
#include <cstdio>
unsigned long long state;
int next_value(){state=(state*1664525ULL+1013904223ULL)&0xffffffffULL;return (int)((state>>8)&65535ULL);}
unsigned long long hash_value;unsigned long long hash_length;
void observe(unsigned long long value){hash_value=(hash_value^value)*1099511628211ULL;++hash_length;}
// Same source/input on all four builds. Timed code has no instrumentation.
int main(){int n=0,rounds=0,seed=0;if(std::scanf("%d %d %d",&n,&rounds,&seed)!=3||n<1||n>262144||rounds<1||rounds>10000||seed<0)return 2;
for(int round=0;round<rounds;++round){state=((unsigned long long)seed+(unsigned long long)round*1013904223ULL)&0xffffffffULL;hash_value=14695981039346656037ULL;hash_length=0;
std::bitset<1024> values;for(int i=0;i<n;++i){int index=next_value()%1024;values.flip((unsigned long long)index);if(i%8==0)values.set((unsigned long long)((index+13)%1024));}
values=(values<<3)^(values>>5);observe((unsigned long long)values.count());
for(int i=0;i<1024;++i)observe(values[(unsigned long long)i]?1ULL:0ULL);
unsigned long long result=(hash_value^hash_length)*1099511628211ULL;std::printf("round=%d hash=%llu\n",round,result);
}return 0;}
