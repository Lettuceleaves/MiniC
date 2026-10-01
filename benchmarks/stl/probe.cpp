#include <vector>
#include <algorithm>
#include <queue>
#include <map>
#include <deque>
#include <string>
#include <utility>
#include <bitset>
unsigned long long state;
int next_value(){state=(state*1664525ULL+1013904223ULL)&0xffffffffULL;return (int)((state>>8)&65535ULL);}
unsigned long long hash_value,hash_length;
void observe(unsigned long long value){hash_value=(hash_value^value)*1099511628211ULL;++hash_length;}
void observe_text(const std::string& value){
    observe((unsigned long long)value.size());
    for(unsigned long long i=0;i<value.size();++i)observe((unsigned long long)(unsigned char)value[i]);
}
unsigned long long count_calls=0,bit_mutations=0,count_total=0;
template<size_t N> unsigned long long counted_count(const std::bitset<N>& value){++count_calls;unsigned long long result=(unsigned long long)value.count();count_total+=result;return result;}
template<size_t N> void counted_flip(std::bitset<N>& value,size_t index){value.flip(index);++bit_mutations;}
int main(){int workload=0,n=0,rounds=0,seed=0;if(::scanf("%d %d %d %d",&workload,&n,&rounds,&seed)!=4||workload<0||workload>10||n<1||n>4096||rounds<1||rounds>10000||seed<0)return 2;
for(int round=0;round<rounds;++round){state=((unsigned long long)seed+(unsigned long long)round*1013904223ULL)&0xffffffffULL;hash_value=14695981039346656037ULL;hash_length=0;bench_begin();count_calls=0;bit_mutations=0;count_total=0;
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
else if(workload==7){
// Each iteration starts at length 0..15 and appends one character, including 15 -> 16.
// A moved-from string is deliberately never observed: its content is unspecified.
for(int i=0;i<n;++i){
    std::string original((unsigned long long)(i%16),(char)('a'+next_value()%26));
    std::string copied(original);
    std::string moved(std::move(copied));
    observe_text(original);observe_text(moved);
    moved.append(1,(char)('A'+next_value()%26));
    observe_text(moved);
    original=moved;
    std::string assigned;
    assigned=std::move(original);
    observe_text(assigned);
}
}
else if(workload==8){
// Dense spans all 1024 bits; sparse changes only the first 64. Both are mutated
// before every count, and every result contributes to the ordered checksum.
std::bitset<1024> dense;dense.set();std::bitset<1024> sparse;
for(int i=0;i<n;++i){
    int dense_index=next_value()%1024,sparse_index=next_value()%64;
    dense.flip((unsigned long long)dense_index);sparse.flip((unsigned long long)sparse_index);
    observe((unsigned long long)dense_index);observe((unsigned long long)sparse_index);
    observe((unsigned long long)dense.count());observe((unsigned long long)sparse.count());
}
for(int i=0;i<1024;++i){observe(dense[(unsigned long long)i]?1ULL:0ULL);observe(sparse[(unsigned long long)i]?1ULL:0ULL);}
}
else if(workload==9){
std::bitset<1024> values;int anchors[16];
for(int word=0;word<16;++word){anchors[word]=next_value()%64;values.set((unsigned long long)(word*64+anchors[word]));}
// Restoring the same bit after the first count keeps EVERY word 0..2 bits set,
// including arbitrarily long runs. Each count sees a fresh preceding mutation.
for(int i=0;i<n;++i){
    int word=next_value()%16,bit=next_value()%64;
    int index=word*64+(i%4==0?anchors[word]:bit);
    observe((unsigned long long)index);
    counted_flip(values,(unsigned long long)index);
    observe(counted_count(values));
    counted_flip(values,(unsigned long long)index);
    observe(counted_count(values));
}
for(int bit=0;bit<1024;++bit)observe(values[(unsigned long long)bit]?1ULL:0ULL);
}
else if(workload==10){
std::bitset<1024> values;values.set();int anchors[16];
for(int word=0;word<16;++word){anchors[word]=next_value()%64;values.reset((unsigned long long)(word*64+anchors[word]));}
// Restoring the same bit after the first count keeps EVERY word 62..64 bits set,
// including arbitrarily long runs. Each count sees a fresh preceding mutation.
for(int i=0;i<n;++i){
    int word=next_value()%16,bit=next_value()%64;
    int index=word*64+(i%4==0?anchors[word]:bit);
    observe((unsigned long long)index);
    counted_flip(values,(unsigned long long)index);
    observe(counted_count(values));
    counted_flip(values,(unsigned long long)index);
    observe(counted_count(values));
}
for(int bit=0;bit<1024;++bit)observe(values[(unsigned long long)bit]?1ULL:0ULL);
}
bench_active=false;unsigned long long result=(hash_value^hash_length)*1099511628211ULL;
::printf("round=%d hash=%llu comparisons=%llu value_ctor=%llu copy_ctor=%llu move_ctor=%llu copy_assign=%llu move_assign=%llu destroyed=%llu live=%llu peak_live=%llu allocations=%llu frees=%llu allocated_bytes=%llu freed_bytes=%llu live_bytes=%llu peak_bytes=%llu",round,result,comparisons,value_ctor,copy_ctor,move_ctor,copy_assign,move_assign,destroyed,live,peak_live,allocations,frees,allocated_bytes,freed_bytes,live_bytes,peak_bytes);
if(workload>=9)::printf(" count_calls=%llu bit_mutations=%llu count_total=%llu",count_calls,bit_mutations,count_total);
::printf("\n");
}return 0;}
