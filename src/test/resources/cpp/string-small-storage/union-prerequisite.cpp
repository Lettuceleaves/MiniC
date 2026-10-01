#include <cstdio>
#include <cstdlib>
void require(bool value){if(!value)std::abort();}
// Named-union prerequisite: all reads name the active member; no byte punning.
// Anonymous aggregate member construction is outside the current compiler profile.
union Storage{unsigned long long capacity;char small[16];};
struct Named{
    char* pointer;unsigned long long size;Storage storage;
    Named():pointer(nullptr),size(0){storage.small[0]=0;}
    void to_number(unsigned long long n){storage.capacity=n;}
    void to_array(int n){for(int i=0;i<n;++i)storage.small[i]=(char)('a'+i);storage.small[n]=0;size=(unsigned long long)n;}
};
static_assert(sizeof(Named)==32,"candidate layout");
int main(){
    Named b;require(b.storage.small[0]==0);
    for(int n=0;n<=15;++n){
        b.to_number(2000ULL+(unsigned long long)n);
        require(b.storage.capacity==2000ULL+(unsigned long long)n);
        b.to_array(n);require(b.storage.small[n]==0);
        for(int i=0;i<n;++i)require(b.storage.small[i]==(char)('a'+i));
    }
    std::printf("union prerequisite ok\n");
}
