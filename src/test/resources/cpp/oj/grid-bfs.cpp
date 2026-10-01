#include <iostream>
#include <vector>
#include <string>
#include <queue>
#include <utility>
int main(){
    int n,m;std::cin>>n>>m;
    std::vector<std::string> grid(n);for(auto& row:grid)std::cin>>row;
    std::vector<std::vector<int>> distance(n,std::vector<int>(m,-1));
    std::queue<std::pair<int,int>> pending;pending.emplace(0,0);distance[0][0]=0;
    int dx[4]={1,0,-1,0},dy[4]={0,1,0,-1};
    while(!pending.empty()){
        auto [x,y]=pending.front();pending.pop();
        for(int d=0;d<4;++d){
            int nx=x+dx[d],ny=y+dy[d];
            if(nx>=0&&nx<n&&ny>=0&&ny<m&&grid[nx][ny]!='#'&&distance[nx][ny]==-1){
                distance[nx][ny]=distance[x][y]+1;pending.emplace(nx,ny);
            }
        }
    }
    std::cout<<distance[n-1][m-1]<<'\n';return 0;
}
