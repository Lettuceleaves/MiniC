#include <vector>
#include <set>
#include <algorithm>
#include <utility>
#include <cstdio>
int main(){int cases;std::scanf("%d",&cases);while(cases--){int n,k;std::scanf("%d%d",&n,&k);std::vector<std::pair<long long,long long>> jobs;
 for(int i=0;i<n;++i){long long start,end;std::scanf("%lld%lld",&start,&end);jobs.emplace_back(end,start);}std::sort(jobs.begin(),jobs.end());
 std::multiset<long long> ends;for(int i=0;i<k;++i)ends.insert(-4000000000000000LL);int accepted=0;
 for(const auto& job:jobs){auto at=ends.upper_bound(job.second);if(at==ends.begin())continue;--at;ends.erase(at);ends.insert(job.first);++accepted;}
 std::printf("%d\n",accepted);}}
