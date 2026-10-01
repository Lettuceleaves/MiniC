#include <bitset>
#include <cstdio>
unsigned long long state;
int next_value(){state=(state*1664525ULL+1013904223ULL)&0xffffffffULL;return (int)((state>>8)&65535ULL);}
unsigned long long hash_value,hash_length;
void observe(unsigned long long value){hash_value=(hash_value^value)*1099511628211ULL;++hash_length;}
// Exact same uninstrumented source and stdin across all four native builds.
// End-to-end work includes setup, PRNG, two flips/two counts per input, and hashing.
int main(){int n=0,rounds=0,seed=0;if(std::scanf("%d %d %d",&n,&rounds,&seed)!=3||n<1||n>262144||rounds<1||rounds>10000||seed<0)return 2;
for(int round=0;round<rounds;++round){state=((unsigned long long)seed+(unsigned long long)round*1013904223ULL)&0xffffffffULL;hash_value=14695981039346656037ULL;hash_length=0;
std::bitset<1024> values;int anchors[16];
for(int word=0;word<16;++word){anchors[word]=next_value()%64;values.set((unsigned long long)(word*64+anchors[word]));}
// Restoring the same bit after the first count keeps EVERY word 0..2 bits set,
// including arbitrarily long runs. Each count sees a fresh preceding mutation.
for(int i=0;i<n;++i){
    int word=next_value()%16,bit=next_value()%64;
    int index=word*64+(i%4==0?anchors[word]:bit);
    observe((unsigned long long)index);
    values.flip((unsigned long long)index);
    observe((unsigned long long)values.count());
    values.flip((unsigned long long)index);
    observe((unsigned long long)values.count());
}
for(int bit=0;bit<1024;++bit)observe(values[(unsigned long long)bit]?1ULL:0ULL);
unsigned long long result=(hash_value^hash_length)*1099511628211ULL;std::printf("round=%d hash=%llu\n",round,result);
}return 0;}
