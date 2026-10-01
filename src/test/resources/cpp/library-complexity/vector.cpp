#include <vector>
#include <cstdio>
struct CopyPreferred {
    int value;
    CopyPreferred(int n):value(n){++value_ctor;born();}
    CopyPreferred(const CopyPreferred& other):value(other.value){++copy_ctor;born();}
    CopyPreferred(CopyPreferred&& other) noexcept(false):value(other.value){++move_ctor;born();}
    CopyPreferred& operator=(const CopyPreferred& other){value=other.value;++copy_assign;return *this;}
    CopyPreferred& operator=(CopyPreferred&& other) noexcept(false){value=other.value;++move_assign;return *this;}
    ~CopyPreferred(){++destroyed;--live;}
};
int main(){int n=0;if(::scanf("%d",&n)!=1||n<8||n>4096)return 2;
    bench_begin();unsigned long long checksum=0;
    {
        std::vector<Tracked> values;
        for(int i=0;i<n;++i)values.emplace_back(i);
        for(int i=0;i<n;++i)checksum=(checksum*131+(unsigned long long)values[i].value+10000)%1000000007ULL;
    }
    ::printf("vector case=0 n=%d value=%llu copies=%llu moves=%llu destroyed=%llu live=%llu allocations=%llu frees=%llu bytes=%llu freed_bytes=%llu checksum=%llu\n",n,value_ctor,copy_ctor,move_ctor,destroyed,live,allocations,frees,allocated_bytes,freed_bytes,checksum);
    bench_begin();unsigned long long extra_alloc=0,extra_copy=0,extra_move=0;bool stable=true;checksum=0;
    {
        std::vector<Tracked> values;values.reserve((unsigned long long)n);
        for(int i=0;i<n/2;++i)values.emplace_back(i);
        auto before_alloc=allocations,before_copy=copy_ctor,before_move=move_ctor;
        Tracked* address=values.data();auto capacity=values.capacity();
        values.reserve(capacity);values.reserve(capacity-1);values.reserve(0);
        stable=values.data()==address&&values.capacity()==capacity;
        for(int i=n/2;i<n;++i)values.emplace_back(i);
        stable=stable&&values.data()==address&&values.capacity()==capacity;
        extra_alloc=allocations-before_alloc;extra_copy=copy_ctor-before_copy;extra_move=move_ctor-before_move;
        for(int i=0;i<n;++i)checksum=(checksum*131+(unsigned long long)values[i].value+10000)%1000000007ULL;
    }
    ::printf("vector case=1 n=%d value=%llu copies=%llu moves=%llu destroyed=%llu live=%llu allocations=%llu frees=%llu bytes=%llu freed_bytes=%llu checksum=%llu extra_alloc=%llu extra_copy=%llu extra_move=%llu stable=%d\n",n,value_ctor,copy_ctor,move_ctor,destroyed,live,allocations,frees,allocated_bytes,freed_bytes,checksum,extra_alloc,extra_copy,extra_move,stable?1:0);
    bench_begin();checksum=0;
    {
        std::vector<CopyPreferred> values;
        for(int i=0;i<n;++i)values.emplace_back(i);
        for(int i=0;i<n;++i)checksum=(checksum*131+(unsigned long long)values[i].value+10000)%1000000007ULL;
    }
    ::printf("vector case=2 n=%d value=%llu copies=%llu moves=%llu destroyed=%llu live=%llu allocations=%llu frees=%llu bytes=%llu freed_bytes=%llu checksum=%llu\n",n,value_ctor,copy_ctor,move_ctor,destroyed,live,allocations,frees,allocated_bytes,freed_bytes,checksum);
    bench_active=false;return 0;
}
