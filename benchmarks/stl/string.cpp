#include <string>
#include <cstdio>
unsigned long long state;
int next_value(){state=(state*1664525ULL+1013904223ULL)&0xffffffffULL;return (int)((state>>8)&65535ULL);}
unsigned long long hash_value;unsigned long long hash_length;
void observe(unsigned long long value){hash_value=(hash_value^value)*1099511628211ULL;++hash_length;}
// Same source/input on all four builds. Timed code has no instrumentation.
int main(){int n=0,rounds=0,seed=0;if(std::scanf("%d %d %d",&n,&rounds,&seed)!=3||n<1||n>262144||rounds<1||rounds>10000||seed<0)return 2;
for(int round=0;round<rounds;++round){state=((unsigned long long)seed+(unsigned long long)round*1013904223ULL)&0xffffffffULL;hash_value=14695981039346656037ULL;hash_length=0;
std::string values;for(int i=0;i<n;++i)values.push_back((char)('a'+next_value()%26));
for(int i=0;i<n/16;++i)values[(unsigned long long)(next_value()%n)]=(char)('A'+i%26);
values.append("tail");values.erase((unsigned long long)(n/3),(unsigned long long)(n/5));
for(unsigned long long i=0;i<values.size();++i)observe((unsigned long long)(unsigned char)values[i]);
auto found=values.find("ab");observe(found==std::string::npos?0ULL:(unsigned long long)found+1ULL);
unsigned long long result=(hash_value^hash_length)*1099511628211ULL;std::printf("round=%d hash=%llu\n",round,result);
}return 0;}
