#include <vector>
#include <deque>
#include <cstdio>
int main(){int cases;std::scanf("%d",&cases);while(cases--){int n,k;std::scanf("%d%d",&n,&k);std::vector<long long> a(n);
 for(int i=0;i<n;++i)std::scanf("%lld",&a[i]);std::deque<int> q;bool first=true;
 for(int i=0;i<n;++i){while(!q.empty()&&q.front()<=i-k)q.pop_front();while(!q.empty()&&a[q.back()]<=a[i])q.pop_back();q.push_back(i);
  if(i+1>=k){std::printf(first?"%lld":" %lld",a[q.front()]);first=false;}}
 std::printf("\n");}}
