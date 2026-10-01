#include <map>
#include <cstdio>
int main(){int cases;std::scanf("%d",&cases);while(cases--){int n,q;std::scanf("%d%d",&n,&q);std::map<long long,long long> values;
 for(int i=0;i<n;++i){long long key,delta;std::scanf("%lld%lld",&key,&delta);values[key]+=delta;if(values[key]==0)values.erase(key);}
 std::printf("%d",(int)values.size());for(const auto& entry:values)std::printf(" %lld:%lld",entry.first,entry.second);std::printf("\n");
 for(int i=0;i<q;++i){long long lo,hi,total=0;std::scanf("%lld%lld",&lo,&hi);for(auto at=values.lower_bound(lo);at!=values.end()&&at->first<=hi;++at)total+=at->second;std::printf(i?" %lld":"%lld",total);}std::printf("\n");}}
