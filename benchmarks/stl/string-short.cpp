#include <string>
#include <utility>
#include <cstdio>
unsigned long long state;
int next_value(){state=(state*1664525ULL+1013904223ULL)&0xffffffffULL;return (int)((state>>8)&65535ULL);}
unsigned long long hash_value,hash_length;
void observe(unsigned long long value){hash_value=(hash_value^value)*1099511628211ULL;++hash_length;}
void observe_text(const std::string& value){
    observe((unsigned long long)value.size());
    for(unsigned long long i=0;i<value.size();++i)observe((unsigned long long)(unsigned char)value[i]);
}
// Uninstrumented source and stdin are identical across all four builds.
int main(){int n=0,rounds=0,seed=0;if(std::scanf("%d %d %d",&n,&rounds,&seed)!=3||n<1||n>262144||rounds<1||rounds>10000||seed<0)return 2;
for(int round=0;round<rounds;++round){state=((unsigned long long)seed+(unsigned long long)round*1013904223ULL)&0xffffffffULL;hash_value=14695981039346656037ULL;hash_length=0;
// Each iteration starts at length 0..15 and appends one character, including 15 -> 16.
// A moved-from string is deliberately never observed: its content is unspecified.
for(int i=0;i<n;++i){
    std::string original((unsigned long long)(i%16),(char)('a'+next_value()%26));
    std::string copied(original);
    std::string moved(std::move(copied));
    observe_text(original);observe_text(moved);
    moved.append(1,(char)('A'+next_value()%26));
    observe_text(moved);
    original=moved;
    std::string assigned;
    assigned=std::move(original);
    observe_text(assigned);
}
unsigned long long result=(hash_value^hash_length)*1099511628211ULL;std::printf("round=%d hash=%llu\n",round,result);
}return 0;}
