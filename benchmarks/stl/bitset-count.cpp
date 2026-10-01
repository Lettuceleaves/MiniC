#include <bitset>
#include <cstdio>
unsigned long long state;
int next_value(){state=(state*1664525ULL+1013904223ULL)&0xffffffffULL;return (int)((state>>8)&65535ULL);}
unsigned long long hash_value,hash_length;
void observe(unsigned long long value){hash_value=(hash_value^value)*1099511628211ULL;++hash_length;}
// Uninstrumented source and stdin are identical across all four builds.
int main(){int n=0,rounds=0,seed=0;if(std::scanf("%d %d %d",&n,&rounds,&seed)!=3||n<1||n>262144||rounds<1||rounds>10000||seed<0)return 2;
for(int round=0;round<rounds;++round){state=((unsigned long long)seed+(unsigned long long)round*1013904223ULL)&0xffffffffULL;hash_value=14695981039346656037ULL;hash_length=0;
// Dense spans all 1024 bits; sparse changes only the first 64. Both are mutated
// before every count, and every result contributes to the ordered checksum.
std::bitset<1024> dense;dense.set();std::bitset<1024> sparse;
for(int i=0;i<n;++i){
    int dense_index=next_value()%1024,sparse_index=next_value()%64;
    dense.flip((unsigned long long)dense_index);sparse.flip((unsigned long long)sparse_index);
    observe((unsigned long long)dense_index);observe((unsigned long long)sparse_index);
    observe((unsigned long long)dense.count());observe((unsigned long long)sparse.count());
}
for(int i=0;i<1024;++i){observe(dense[(unsigned long long)i]?1ULL:0ULL);observe(sparse[(unsigned long long)i]?1ULL:0ULL);}
unsigned long long result=(hash_value^hash_length)*1099511628211ULL;std::printf("round=%d hash=%llu\n",round,result);
}return 0;}
