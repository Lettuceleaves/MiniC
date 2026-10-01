#include <map>
#include <cstdio>
unsigned long long state;
int next_value(){state=(state*1664525ULL+1013904223ULL)&0xffffffffULL;return (int)((state>>8)&65535ULL);}
unsigned long long hash_value;unsigned long long hash_length;
void observe(unsigned long long value){hash_value=(hash_value^value)*1099511628211ULL;++hash_length;}
// Same source/input on all four builds. Timed code has no instrumentation.
int main(){int n=0,rounds=0,seed=0;if(std::scanf("%d %d %d",&n,&rounds,&seed)!=3||n<1||n>262144||rounds<1||rounds>10000||seed<0)return 2;
for(int round=0;round<rounds;++round){state=((unsigned long long)seed+(unsigned long long)round*1013904223ULL)&0xffffffffULL;hash_value=14695981039346656037ULL;hash_length=0;
std::map<int,int> values;for(int i=0;i<n;++i){int key=next_value()%((n+1)/2+1);values[key]=i+1;}
for(int i=0;i<n/4;++i)values.erase(next_value()%((n+1)/2+1));
for(auto at=values.begin();at!=values.end();++at){observe((unsigned long long)at->first);observe((unsigned long long)at->second);}
unsigned long long result=(hash_value^hash_length)*1099511628211ULL;std::printf("round=%d hash=%llu\n",round,result);
}return 0;}
