#include <string>
#include <utility>
#include <cstdio>
#include <cstdlib>
void require(bool okay,int point){if(!okay){std::printf("failed %d\n",point);std::abort();}}
void valid(const std::string& value){require(value.data()!=nullptr,1);require(value.size()<=value.capacity(),2);require(value.data()[value.size()]==0,3);require(value.begin()+value.size()==value.end(),4);}
#if defined(__MINIC__) || defined(__MINIC_SELF_STL__)
static_assert(sizeof(std::string)==32,"SSO keeps the x64 object size");
#endif
int main(){
    const char* text="abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    int lengths[10]={0,1,7,14,15,16,17,30,31,32};
    for(int k=0;k<10;++k){
        unsigned long long n=(unsigned long long)lengths[k];
        std::string filled(n,'x');valid(filled);require(filled.size()==n,10);
        for(unsigned long long i=0;i<n;++i)require(filled[i]=='x',11);
        std::string counted(text,n),copied(counted),range(text,text+n);valid(counted);valid(copied);valid(range);
        require(counted==range&&copied==range,12);
        if(n){copied[0]='!';require(counted[0]=='a',13);}
        std::string slice(counted,n/2,n);valid(slice);require(slice.size()==n-n/2,14);
        for(unsigned long long i=0;i<slice.size();++i)require(slice[i]==text[n/2+i],15);
    }
    char binary[16]={'a',0,'b','c','d','e','f','g','h','i','j','k','l','m','n','o'};
    std::string a(binary,15),b(binary,16),z(binary),listed={'a',0,'b'};
    valid(a);valid(b);valid(z);valid(listed);require(a.size()==15&&b.size()==16&&z.size()==1&&listed.size()==3,16);
    require(a[1]==0&&b[15]=='o'&&listed[1]==0,17);
    const std::string c="";valid(c);require(c.c_str()[0]==0,18);
    std::printf("constructors ok\n");
}
