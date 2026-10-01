#include <map>
#include <set>
#include <cstdio>
int main(){int n=0;if(::scanf("%d",&n)!=1||n<8||n>4096||n%2)return 2;
    for(int mode=0;mode<3;++mode){
        bench_begin();unsigned long long hint_count=0,copy_count=0,copy_compare=0,copy_alloc=0,checksum=0;
        {
            std::map<int,Tracked,CountLess> original;
            if(mode==0)for(int i=0;i<n;++i)original.emplace_hint(original.end(),i,Tracked(i));
            if(mode==1)for(int i=n;i>0;--i)original.emplace_hint(original.begin(),i-1,Tracked(i-1));
            if(mode==2){
                for(int i=0;i<n;i+=2)original.emplace(i,Tracked(i));
                comparisons=0;
                auto hint=original.begin();++hint;
                for(int i=1;i<n;i+=2){original.emplace_hint(hint,i,Tracked(i));if(hint!=original.end())++hint;}
            }
            hint_count=comparisons;
            auto before_copy=copy_ctor,before_compare=comparisons,before_alloc=allocations;
            std::map<int,Tracked,CountLess> copied(original);
            copy_count=copy_ctor-before_copy;copy_compare=comparisons-before_compare;copy_alloc=allocations-before_alloc;
            if(copied.size()!=(unsigned long long)n||&*copied.begin()==&*original.begin())return 3;
            int expected=0;for(auto it=copied.begin();it!=copied.end();++it){if(it->first!=expected||it->second.value!=expected)return 4;checksum=(checksum*131+(unsigned long long)expected+10000)%1000000007ULL;++expected;}
            if(expected!=n)return 5;
        }
        ::printf("tree case=%d n=%d comparisons=%llu copy_comparisons=%llu copies=%llu copy_allocations=%llu allocations=%llu frees=%llu bytes=%llu freed_bytes=%llu value=%llu all_copies=%llu moves=%llu destroyed=%llu live=%llu checksum=%llu\n",mode,n,hint_count,copy_compare,copy_count,copy_alloc,allocations,frees,allocated_bytes,freed_bytes,value_ctor,copy_ctor,move_ctor,destroyed,live,checksum);
    }
    for(int mode=3;mode<5;++mode){
        bench_begin();unsigned long long hint_count=0,copy_count=0,copy_compare=0,copy_alloc=0,checksum=0;
        if(mode==3){
            std::set<Tracked> original;for(int i=0;i<n;++i)original.emplace_hint(original.end(),i);
            hint_count=comparisons;auto c=copy_ctor,q=comparisons,a=allocations;std::set<Tracked> copied(original);copy_count=copy_ctor-c;copy_compare=comparisons-q;copy_alloc=allocations-a;
            int expected=0;for(auto it=copied.begin();it!=copied.end();++it){if(it->value!=expected)return 6;checksum=(checksum*131+(unsigned long long)expected+10000)%1000000007ULL;++expected;}if(expected!=n)return 7;
        }else{
            std::multiset<Tracked> original;for(int i=0;i<n;++i)original.emplace_hint(original.end(),i/2);
            hint_count=comparisons;auto c=copy_ctor,q=comparisons,a=allocations;std::multiset<Tracked> copied(original);copy_count=copy_ctor-c;copy_compare=comparisons-q;copy_alloc=allocations-a;
            int expected=0;for(auto it=copied.begin();it!=copied.end();++it){if(it->value!=expected/2)return 8;checksum=(checksum*131+(unsigned long long)(expected/2)+10000)%1000000007ULL;++expected;}if(expected!=n)return 9;
        }
        ::printf("tree case=%d n=%d comparisons=%llu copy_comparisons=%llu copies=%llu copy_allocations=%llu allocations=%llu frees=%llu bytes=%llu freed_bytes=%llu value=%llu all_copies=%llu moves=%llu destroyed=%llu live=%llu checksum=%llu\n",mode,n,hint_count,copy_compare,copy_count,copy_alloc,allocations,frees,allocated_bytes,freed_bytes,value_ctor,copy_ctor,move_ctor,destroyed,live,checksum);
    }
    bench_active=false;return 0;
}
