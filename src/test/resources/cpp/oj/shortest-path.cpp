#include <iostream>
#include <vector>
#include <queue>
#include <functional>
#include <utility>
int main(){
    int n,m;std::cin>>n>>m;
    std::vector<std::vector<std::pair<int,int>>> graph(n);
    for(int i=0;i<m;++i){int u,v,w;std::cin>>u>>v>>w;graph[u].emplace_back(v,w);}
    const long long infinity=1000000000000LL;
    std::vector<long long> distance(n,infinity);
    typedef std::pair<long long,int> State;
    std::priority_queue<State,std::vector<State>,std::greater<State>> pending;
    distance[0]=0;pending.emplace(0,0);
    while(!pending.empty()){
        auto [cost,u]=pending.top();pending.pop();
        if(cost!=distance[u])continue;
        for(const auto& edge:graph[u]){
            auto [v,w]=edge;
            if(cost+w<distance[v]){distance[v]=cost+w;pending.emplace(distance[v],v);}
        }
    }
    for(int i=0;i<n;++i)std::cout<<(i?" ":"")<<distance[i];
    std::cout<<'\n';return 0;
}
