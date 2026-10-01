#include <vector>
#include <algorithm>
#include <queue>
#include <map>
#include <deque>
#include <string>
#include <bitset>
unsigned long long state;
int next_value(){state=(state*1664525ULL+1013904223ULL)&0xffffffffULL;return (int)((state>>8)&65535ULL);}
unsigned long long hash_value,hash_length;
void observe(unsigned long long value){hash_value=(hash_value^value)*1099511628211ULL;++hash_length;}
int main(){int workload=0,n=0,rounds=0,seed=0;if(::scanf("%d %d %d %d",&workload,&n,&rounds,&seed)!=4||workload<0||workload>6||n<1||n>4096||rounds<1||rounds>10000||seed<0)return 2;
for(int round=0;round<rounds;++round){state=((unsigned long long)seed+(unsigned long long)round*1013904223ULL)&0xffffffffULL;hash_value=14695981039346656037ULL;hash_length=0;bench_begin();
if(workload==0){
std::vector<Tracked> values;for(int i=0;i<n;++i)values.push_back(Tracked(next_value()));
std::sort(values.begin(),values.end());for(int i=0;i<n;++i)observe((unsigned long long)values[i].value);
}
else if(workload==1){
std::vector<Tracked> values;for(int i=0;i<n;++i)values.push_back(Tracked(next_value()));std::sort(values.begin(),values.end());
for(int i=0;i<n;++i){Tracked key(next_value());auto at=std::lower_bound(values.begin(),values.end(),key);observe((unsigned long long)(at-values.begin()));observe(at!=values.end()&&at->value==key.value?1ULL:0ULL);}
}
else if(workload==2){
std::priority_queue<Tracked> values;for(int i=0;i<n;++i)values.push(Tracked(next_value()));while(!values.empty()){observe((unsigned long long)values.top().value);values.pop();}
}
else if(workload==3){
std::map<int,Tracked,CountLess> values;for(int i=0;i<n;++i){int key=next_value()%((n+1)/2+1);values[key]=Tracked(i+1);}
for(int i=0;i<n/4;++i)values.erase(next_value()%((n+1)/2+1));
for(auto at=values.begin();at!=values.end();++at){observe((unsigned long long)at->first);observe((unsigned long long)at->second.value);}
}
else if(workload==4){
std::deque<Tracked> values;for(int i=0;i<n;++i){int value=next_value();if(i%2)values.push_front(Tracked(value));else values.push_back(Tracked(value));}
for(int i=0;i<n;++i)observe((unsigned long long)values[i].value);
for(int i=0;i<n;++i){if(i%2){observe((unsigned long long)values.back().value);values.pop_back();}else{observe((unsigned long long)values.front().value);values.pop_front();}}
}
else if(workload==5){
std::string values;for(int i=0;i<n;++i)values.push_back((char)('a'+next_value()%26));
for(int i=0;i<n/16;++i)values[(unsigned long long)(next_value()%n)]=(char)('A'+i%26);
values.append("tail");values.erase((unsigned long long)(n/3),(unsigned long long)(n/5));
for(unsigned long long i=0;i<values.size();++i)observe((unsigned long long)(unsigned char)values[i]);
auto found=values.find("ab");observe(found==std::string::npos?0ULL:(unsigned long long)found+1ULL);
}
else if(workload==6){
std::bitset<1024> values;for(int i=0;i<n;++i){int index=next_value()%1024;values.flip((unsigned long long)index);if(i%8==0)values.set((unsigned long long)((index+13)%1024));}
values=(values<<3)^(values>>5);observe((unsigned long long)values.count());
for(int i=0;i<1024;++i)observe(values[(unsigned long long)i]?1ULL:0ULL);
}
bench_active=false;unsigned long long result=(hash_value^hash_length)*1099511628211ULL;
::printf("round=%d hash=%llu comparisons=%llu value_ctor=%llu copy_ctor=%llu move_ctor=%llu copy_assign=%llu move_assign=%llu destroyed=%llu live=%llu peak_live=%llu allocations=%llu frees=%llu allocated_bytes=%llu freed_bytes=%llu live_bytes=%llu peak_bytes=%llu\n",round,result,comparisons,value_ctor,copy_ctor,move_ctor,copy_assign,move_assign,destroyed,live,peak_live,allocations,frees,allocated_bytes,freed_bytes,live_bytes,peak_bytes);
}return 0;}
