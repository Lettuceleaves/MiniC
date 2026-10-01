// Complete OJ program. The Java registry supplies identical full input to all four builds.
// Output encodes every result in deterministic order, including its observation count.
#include <cstdio>
unsigned long long hash_value;
unsigned long long hash_length;
void begin_answer(){hash_value=14695981039346656037ULL;hash_length=0;}
void observe(long long value){hash_value=(hash_value^(unsigned long long)value)*1099511628211ULL;++hash_length;}
void answer(int round){std::printf("round=%d observations=%llu hash=%llu\n",round,hash_length,(hash_value^hash_length)*1099511628211ULL);}
#include <map>
int main(){int rounds;if(std::scanf("%d",&rounds)!=1||rounds<1||rounds>1024)return 2;
 for(int round=0;round<rounds;++round){int n;if(std::scanf("%d",&n)!=1||n<1||n>16384)return 2;
  std::map<int,long long> values;begin_answer();
  for(int i=0;i<n;++i){int key,delta;if(std::scanf("%d %d",&key,&delta)!=2)return 2;long long& value=values[key];value+=delta;observe(key);observe(value);if(value==0)values.erase(key);}
  observe(values.size());for(const auto& item:values){observe(item.first);observe(item.second);}answer(round);
 }return 0;}
