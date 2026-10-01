// Complete OJ program. The Java registry supplies identical full input to all four builds.
// Output encodes every result in deterministic order, including its observation count.
#include <cstdio>
unsigned long long hash_value;
unsigned long long hash_length;
void begin_answer(){hash_value=14695981039346656037ULL;hash_length=0;}
void observe(long long value){hash_value=(hash_value^(unsigned long long)value)*1099511628211ULL;++hash_length;}
void answer(int round){std::printf("round=%d observations=%llu hash=%llu\n",round,hash_length,(hash_value^hash_length)*1099511628211ULL);}
#include <vector>
#include <string>
#include <queue>
#include <cstring>
int main(){int rounds;if(std::scanf("%d",&rounds)!=1||rounds<1||rounds>1024)return 2;
 for(int round=0;round<rounds;++round){int n,sy,sx;if(std::scanf("%d %d %d",&n,&sy,&sx)!=3||n<1||n>128||sy<0||sy>=n||sx<0||sx>=n)return 2;
  std::vector<std::string> grid;char row[129];for(int y=0;y<n;++y){if(std::scanf("%128s",row)!=1||std::strlen(row)!=(unsigned long long)n)return 2;grid.push_back(std::string(row));}
  if(grid[sy][sx]!='.')return 2;std::vector<int> distance(n*n,-1);std::queue<int> pending;pending.push(sy*n+sx);distance[sy*n+sx]=0;
  int dy[4]={-1,0,1,0};int dx[4]={0,1,0,-1};
  while(!pending.empty()){int u=pending.front();pending.pop();int y=u/n,x=u%n;for(int d=0;d<4;++d){int ny=y+dy[d],nx=x+dx[d];if(ny>=0&&ny<n&&nx>=0&&nx<n&&grid[ny][nx]=='.'&&distance[ny*n+nx]<0){distance[ny*n+nx]=distance[u]+1;pending.push(ny*n+nx);}}}
  begin_answer();for(int d:distance)observe(d);answer(round);
 }return 0;}
