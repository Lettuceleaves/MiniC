#include <vector>
#include <queue>
#include <utility>
#include <functional>
#include <cstdio>
int main(){
 int cases;std::scanf("%d",&cases);
 while(cases--){int n,m,start;std::scanf("%d%d%d",&n,&m,&start);
  typedef std::pair<long long,int> Entry;
  std::vector<std::vector<Entry>> graph(n);
  for(int i=0;i<m;++i){int from,to;long long weight;std::scanf("%d%d%lld",&from,&to,&weight);graph[from].emplace_back(weight,to);}
  const long long inf=4000000000000000LL;std::vector<long long> distance(n,inf);
  std::priority_queue<Entry,std::vector<Entry>,std::greater<Entry>> queue;
  distance[start]=0;queue.emplace(0,start);
  while(!queue.empty()){Entry top=queue.top();queue.pop();if(top.first!=distance[top.second])continue;
   for(const Entry& edge:graph[top.second])if(top.first+edge.first<distance[edge.second]){distance[edge.second]=top.first+edge.first;queue.emplace(distance[edge.second],edge.second);}
  }
  for(int i=0;i<n;++i)std::printf(i?" %lld":"%lld",distance[i]==inf?-1:distance[i]);std::printf("\n");
 }
}
