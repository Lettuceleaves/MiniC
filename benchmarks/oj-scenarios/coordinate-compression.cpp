// Complete OJ program. The Java registry supplies identical full input to all four builds.
// Output encodes every result in deterministic order, including its observation count.
#include <cstdio>
unsigned long long hash_value;
unsigned long long hash_length;
void begin_answer(){hash_value=14695981039346656037ULL;hash_length=0;}
void observe(long long value){hash_value=(hash_value^(unsigned long long)value)*1099511628211ULL;++hash_length;}
void answer(int round){std::printf("round=%d observations=%llu hash=%llu\n",round,hash_length,(hash_value^hash_length)*1099511628211ULL);}
#include <vector>
#include <algorithm>
int main(){int rounds;if(std::scanf("%d",&rounds)!=1||rounds<1||rounds>1024)return 2;
 for(int round=0;round<rounds;++round){int n;if(std::scanf("%d",&n)!=1||n<1||n>32768)return 2;
  std::vector<int> values(n);for(int i=0;i<n;++i)if(std::scanf("%d",&values[i])!=1)return 2;
  std::vector<int> dictionary(values);std::sort(dictionary.begin(),dictionary.end());dictionary.erase(std::unique(dictionary.begin(),dictionary.end()),dictionary.end());
  begin_answer();observe(dictionary.size());for(int value:dictionary)observe(value);for(int value:values)observe(std::lower_bound(dictionary.begin(),dictionary.end(),value)-dictionary.begin());answer(round);
 }return 0;}
