#include <vector>
#include <queue>
#include <string>
#include <utility>
#include <iostream>
int main(){int cases;std::cin>>cases;while(cases--){int h,w;std::cin>>h>>w;std::vector<std::string> grid(h);for(int i=0;i<h;++i)std::cin>>grid[i];
 std::vector<std::vector<int>> distance(h,std::vector<int>(w,-1));std::queue<std::pair<int,int>> queue;
 if(grid[0][0]!='#'){distance[0][0]=0;queue.emplace(0,0);}int dr[4]={1,-1,0,0},dc[4]={0,0,1,-1};
 while(!queue.empty()){auto current=queue.front();queue.pop();for(int direction=0;direction<4;++direction){int r=current.first+dr[direction],c=current.second+dc[direction];
  if(r>=0&&r<h&&c>=0&&c<w&&grid[r][c]!='#'&&distance[r][c]<0){distance[r][c]=distance[current.first][current.second]+1;queue.emplace(r,c);}}}
 for(int r=0;r<h;++r)for(int c=0;c<w;++c)std::cout<<(r||c?" ":"")<<distance[r][c];std::cout<<'\n';}}
