#include <queue>
#include <stack>
#include <functional>
#include <cstdio>
int main(){
    std::queue<int> q;std::stack<int> s;std::priority_queue<int,std::vector<int>,std::greater<int>> heap;
    for(int i=9;i>=0;--i){q.push(i);s.push(i);heap.emplace(i);}
    unsigned long long a=0,b=0,c=0;
    while(!q.empty()){a=a*10+q.front();q.pop();b=b*10+s.top();s.pop();c=c*10+heap.top();heap.pop();}
    std::printf("%llu %llu %llu\n",a,b,c);
}
