#include <set>
#include <map>
#include <cstdio>
int main(){
    std::set<int> unique;std::multiset<int> duplicates;std::map<int,int> counts;
    for(int i=0;i<400;++i){int k=(i*73)%127;unique.insert(k);duplicates.insert(k);++counts[k];}
    // Unsigned checksums avoid relying on signed overflow.
    unsigned long long hash=0;for(auto i=counts.begin();i!=counts.end();++i)hash=hash*131+i->first*17+i->second;
    auto saved=unique.find(80);const int* address=&*saved;
    for(int i=0;i<127;i+=3){unique.erase(i);duplicates.erase(i);counts.erase(i);}
    std::set<int> other;other.insert(-1);unique.swap(other);
    unsigned long long tail=0;for(auto i=other.rbegin();i!=other.rend();++i)tail=tail*17+*i;
    std::printf("%llu %llu %llu %d %d %llu\n",hash,tail,(unsigned long long)duplicates.size(),*saved,address==&*other.find(80),(unsigned long long)counts.size());
    while(!other.empty())other.erase(other.begin());
    std::printf("%d %d %d\n",other.empty(),unique.count(-1),counts.lower_bound(50)->first);
}
