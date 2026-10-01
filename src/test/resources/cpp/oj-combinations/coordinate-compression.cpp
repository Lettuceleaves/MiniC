#include <vector>
#include <algorithm>
#include <cstdio>
int main(){int cases;std::scanf("%d",&cases);while(cases--){int n;std::scanf("%d",&n);std::vector<long long> a(n);
 for(int i=0;i<n;++i)std::scanf("%lld",&a[i]);std::vector<long long> sorted=a;std::sort(sorted.begin(),sorted.end());sorted.erase(std::unique(sorted.begin(),sorted.end()),sorted.end());
 std::printf("%d",(int)sorted.size());for(long long value:sorted)std::printf(" %lld",value);std::printf("\n");
 for(int i=0;i<n;++i)std::printf(i?" %lld":"%lld",(long long)(std::lower_bound(sorted.begin(),sorted.end(),a[i])-sorted.begin()));std::printf("\n");}}
