// Complete OJ program. The Java registry supplies identical full input to all four builds.
// Output encodes every result in deterministic order, including its observation count.
#include <cstdio>
unsigned long long hash_value;
unsigned long long hash_length;
void begin_answer(){hash_value=14695981039346656037ULL;hash_length=0;}
void observe(long long value){hash_value=(hash_value^(unsigned long long)value)*1099511628211ULL;++hash_length;}
void answer(int round){std::printf("round=%d observations=%llu hash=%llu\n",round,hash_length,(hash_value^hash_length)*1099511628211ULL);}
#include <bitset>
int main(){int rounds;if(std::scanf("%d",&rounds)!=1||rounds<1||rounds>1024)return 2;
 for(int round=0;round<rounds;++round){int n;if(std::scanf("%d",&n)!=1||n<1||n>512)return 2;
  std::bitset<4097> possible;possible.set(0);for(int i=0;i<n;++i){int weight;if(std::scanf("%d",&weight)!=1||weight<0||weight>8192)return 2;possible|=possible<<weight;}
  begin_answer();for(int sum=0;sum<=4096;++sum)observe(possible.test(sum)?1:0);answer(round);
 }return 0;}
