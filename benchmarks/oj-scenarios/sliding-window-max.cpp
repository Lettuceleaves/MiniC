// Complete OJ program. The Java registry supplies identical full input to all four builds.
// Output encodes every result in deterministic order, including its observation count.
#include <cstdio>
unsigned long long hash_value;
unsigned long long hash_length;
void begin_answer(){hash_value=14695981039346656037ULL;hash_length=0;}
void observe(long long value){hash_value=(hash_value^(unsigned long long)value)*1099511628211ULL;++hash_length;}
void answer(int round){std::printf("round=%d observations=%llu hash=%llu\n",round,hash_length,(hash_value^hash_length)*1099511628211ULL);}
#include <vector>
#include <deque>
int main(){int rounds;if(std::scanf("%d",&rounds)!=1||rounds<1||rounds>1024)return 2;
 for(int round=0;round<rounds;++round){int n,width;if(std::scanf("%d %d",&n,&width)!=2||n<1||n>32768||width<1||width>n)return 2;
  std::vector<int> values(n);for(int i=0;i<n;++i)if(std::scanf("%d",&values[i])!=1)return 2;
  std::deque<int> indices;begin_answer();
  for(int i=0;i<n;++i){while(!indices.empty()&&indices.front()<=i-width)indices.pop_front();while(!indices.empty()&&values[indices.back()]<=values[i])indices.pop_back();indices.push_back(i);if(i+1>=width)observe(values[indices.front()]);}
  answer(round);
 }return 0;}
