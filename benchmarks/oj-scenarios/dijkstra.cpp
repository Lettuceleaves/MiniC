// Complete OJ program. The Java registry supplies identical full input to all four builds.
// Output encodes every result in deterministic order, including its observation count.
#include <cstdio>
unsigned long long hash_value;
unsigned long long hash_length;
void begin_answer(){hash_value=14695981039346656037ULL;hash_length=0;}
void observe(long long value){hash_value=(hash_value^(unsigned long long)value)*1099511628211ULL;++hash_length;}
void answer(int round){std::printf("round=%d observations=%llu hash=%llu\n",round,hash_length,(hash_value^hash_length)*1099511628211ULL);}
#include <vector>
#include <queue>
#include <utility>
#include <functional>
int main(){int rounds;if(std::scanf("%d",&rounds)!=1||rounds<1||rounds>1024)return 2;
 for(int round=0;round<rounds;++round){int n,m;if(std::scanf("%d %d",&n,&m)!=2||n<1||n>1024||m<0||m>8*n)return 2;
  std::vector<std::vector<std::pair<int,int>>> graph(n);
  for(int i=0;i<m;++i){int u,v,w;if(std::scanf("%d %d %d",&u,&v,&w)!=3||u<0||u>=n||v<0||v>=n||w<0||w>1000000000)return 2;graph[u].push_back(std::make_pair(v,w));}
  const long long inf=0x3fffffffffffffffLL;std::vector<long long> distance(n,inf);distance[0]=0;
  std::priority_queue<std::pair<long long,int>,std::vector<std::pair<long long,int>>,std::greater<std::pair<long long,int>>> pending;
  pending.push(std::make_pair(0LL,0));
  while(!pending.empty()){auto item=pending.top();pending.pop();long long cost=item.first;int u=item.second;if(cost!=distance[u])continue;
   for(const auto& edge:graph[u]){long long next=cost+edge.second;if(next<distance[edge.first]){distance[edge.first]=next;pending.push(std::make_pair(next,edge.first));}}
  }
  begin_answer();for(int i=0;i<n;++i)observe(distance[i]==inf?-1:distance[i]);answer(round);
 }return 0;}
