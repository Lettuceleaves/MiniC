// Complete OJ program. The Java registry supplies identical full input to all four builds.
// Output encodes every result in deterministic order, including its observation count.
#include <cstdio>
unsigned long long hash_value;
unsigned long long hash_length;
void begin_answer(){hash_value=14695981039346656037ULL;hash_length=0;}
void observe(long long value){hash_value=(hash_value^(unsigned long long)value)*1099511628211ULL;++hash_length;}
void answer(int round){std::printf("round=%d observations=%llu hash=%llu\n",round,hash_length,(hash_value^hash_length)*1099511628211ULL);}
#include <map>
#include <string>
int main(){int rounds;if(std::scanf("%d",&rounds)!=1||rounds<1||rounds>1024)return 2;
 for(int round=0;round<rounds;++round){int n;if(std::scanf("%d",&n)!=1||n<1||n>16384)return 2;
  std::map<std::string,int> counts;char word[128];for(int i=0;i<n;++i){if(std::scanf("%127s",word)!=1)return 2;++counts[std::string(word)];}
  begin_answer();observe(counts.size());for(const auto& item:counts){observe(item.first.size());for(char c:item.first)observe((unsigned char)c);observe(item.second);}answer(round);
 }return 0;}
