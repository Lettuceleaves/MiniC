// The test prepends the existing exact-allocation probe hooks to this source.
#include <string>
#include <utility>
void require(bool okay){if(!okay)::abort();}
void valid(const std::string& value){require(value.data()!=nullptr&&value.size()<=value.capacity()&&value.data()[value.size()]==0);}
void balanced(){require(allocations==frees&&allocated_bytes==freed_bytes&&live_bytes==0);}
bool zero_allocations(){
    balanced();
#if defined(__MINIC__) || defined(__MINIC_SELF_STL__)
    if(allocations!=0){::printf("unexpected allocations=%llu bytes=%llu\n",allocations,allocated_bytes);return false;}
#endif
    return true;
}
int main(){
    bench_begin();
    {
        const char* letters="abcdefghijklmno";
        for(int n=0;n<=15;++n){
            std::string a(letters,(unsigned long long)n),b(a),c(std::move(b));
            valid(a);valid(b);valid(c);require(a==c);b=a;c=std::move(b);valid(b);valid(c);
            std::string d(letters,letters+n);require(d==a);a.swap(d);a.swap(a);a=std::move(a);valid(a);
            a.assign((unsigned long long)n,'x');if(n<15)a.append(1,'y');valid(a);
        }
    }
    bench_active=false;if(!zero_allocations())return 42;
    bench_begin();
    {
        std::string a="abcdef";a.insert(2,a.data()+1,4);require(a=="abbcdecdef");
        a="abcdef";a.replace(1,4,a.data()+2,4);require(a=="acdeff");
        a="abc";a.append(a.begin(),a.end());require(a=="abcabc");
        a="abc";a.append(a.data()+a.size(),1);require(a.size()==4&&a[3]==0);valid(a);
        a.assign({'a','b','c'});a.insert(a.end(),{'d','e'});a.replace(a.begin()+1,a.begin()+3,a.begin()+2,a.end());valid(a);
        std::string b=a.substr(1,3);valid(b);a.clear();a.shrink_to_fit();a.reserve(15);valid(a);
    }
    bench_active=false;if(!zero_allocations())return 42;
    // Positive hook control: avoid a false zero caused by missing instrumentation.
    bench_begin();
    {std::string long_value(128,'z');valid(long_value);require(long_value.size()==128);}
    bench_active=false;balanced();
#if defined(__MINIC__) || defined(__MINIC_SELF_STL__)
    if(allocations==0||allocated_bytes==0)return 43;
#endif
    ::printf("zero allocation ok\n");
}
