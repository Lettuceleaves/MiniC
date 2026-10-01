#include <string>
#include <utility>
#include <cstdio>
#include <cstdlib>
void require(bool okay,int point){if(!okay){std::printf("failed %d\n",point);std::abort();}}
void valid(const std::string& value){require(value.data()!=nullptr,1);require(value.size()<=value.capacity(),2);require(value.data()[value.size()]==0,3);require(value.begin()+value.size()==value.end(),4);}
#include <vector>
std::string global_short="abcdefghijklmno";
std::string global_long="abcdefghijklmnop";
struct Holder{std::string text;};
std::string make_value(int n){std::string value((unsigned long long)n,'x');return value;}
std::string& persistent(){static std::string value="abc";return value;}
int main(){
    valid(global_short);valid(global_long);require(global_short.size()==15&&global_long.size()==16,10);
    std::string values[3]={"", "abcdefghijklmno", "abcdefghijklmnop"};
    Holder first={"short"};Holder second(std::move(first));valid(first.text);require(second.text=="short",11);
    first.text="again";require(first.text=="again"&&second.text=="short",12);
    std::vector<std::string> stored;
    for(int i=0;i<18;++i)stored.push_back(make_value(i));
    for(int i=0;i<18;++i){valid(stored[(unsigned long long)i]);require(stored[(unsigned long long)i].size()==(unsigned long long)i,13);}
    std::swap(stored[1],stored[16]);require(stored[1].size()==16&&stored[16].size()==1,14);
    stored[1]=std::move(stored[16]);require(stored[1].size()==1,15);valid(stored[16]);
    stored[16]="reuse";require(stored[16]=="reuse",16);
    stored[0]=std::move(stored[0]);valid(stored[0]);stored[0]="self";
    for(int i=0;i<3;++i)valid(values[i]);
    persistent().append("def");require(persistent()=="abcdef",17);valid(persistent());
    std::printf("lifetime ok\n");
}
