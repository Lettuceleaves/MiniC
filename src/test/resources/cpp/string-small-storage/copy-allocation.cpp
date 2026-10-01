// The test prepends the exact allocation hooks; source owners live outside each count interval.
#include <string>
void require(bool okay){if(!okay)::abort();}
void value(const std::string& s,unsigned long long n){require(s.size()==n&&s.data()[n]==0);for(unsigned long long i=0;i<n;++i)require(s[i]=='s');}
bool counts(unsigned long long expected,int phase){
    require(allocations==frees&&allocated_bytes==freed_bytes&&live_bytes==0);
#if defined(__MINIC__) || defined(__MINIC_SELF_STL__)
    if(allocations!=expected){::printf("phase=%d allocations=%llu expected=%llu\n",phase,allocations,expected);return false;}
#endif
    return true;
}
int main(){
    int lengths[2]={16,128};
    for(int k=0;k<2;++k){
        unsigned long long n=(unsigned long long)lengths[k];std::string source(n,'s');
        bench_begin();{std::string copied(source);value(copied,n);}bench_active=false;if(!counts(1,10+k))return 44;
        bench_begin();{std::string assigned;assigned=source;value(assigned,n);}bench_active=false;if(!counts(1,20+k))return 44;
        bench_begin();{std::string assigned;assigned.assign(source);value(assigned,n);}bench_active=false;if(!counts(1,30+k))return 44;
        std::string reserved;reserved.reserve(n+32);
        bench_begin();reserved=source;value(reserved,n);reserved.assign(source);value(reserved,n);bench_active=false;if(!counts(0,40+k))return 44;
        bench_begin();source=source;source.assign(source);value(source,n);bench_active=false;if(!counts(0,50+k))return 44;
    }
    ::printf("copy allocation ok\n");
}
