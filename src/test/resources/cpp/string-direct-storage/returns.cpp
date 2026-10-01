#include <string>
#include <utility>
#include <cstdio>
#include <cstdlib>
void require(bool value,int code){if(!value){std::printf("failed %d\n",code);std::abort();}}
void valid(const std::string& s){require(s.data()!=nullptr&&s.size()<=s.capacity()&&s.data()[s.size()]=='\0',1);require(s.begin()+s.size()==s.end(),2);}
bool inside(const std::string& s){const char* bytes=(const char*)&s;for(unsigned long long i=0;i<sizeof(s);++i)if(s.data()==bytes+i)return true;return false;}
void small(const std::string& s){valid(s);
#if defined(__MINIC__) || defined(__MINIC_SELF_STL__)
 require(sizeof(s)==32&&inside(s),3);
#endif
}
void text(const std::string& s,unsigned long long n,char c){valid(s);require(s.size()==n,4);for(unsigned long long i=0;i<n;++i)require(s[i]==c,5);}
std::string immediate(int n){return std::string((unsigned long long)n,'p');}
std::string named(int n){std::string value((unsigned long long)n,'n');return value;}
std::string relay(std::string value){return value;}
std::string choose(bool b){return b?std::string("left"):std::string("right");}
struct Nest{std::string value;std::string second;};
Nest make_nest(){Nest value={"small",std::string(64,'h')};return value;}
std::string global="global";
std::string& persistent(){static std::string value="static";return value;}
int main(){small(global);small(persistent());for(int n=0;n<=16;++n){
 std::string a=immediate(n),b=named(n),c=relay(immediate(n));text(a,n,'p');text(b,n,'n');text(c,n,'p');if(n<=15){small(a);small(b);small(c);}
 }Nest first=make_nest();Nest second(first);Nest third(std::move(first));small(second.value);small(third.value);require(second.value=="small"&&third.value=="small",20);
 first.value="reused";third.value[0]='S';require(second.value=="small",21);
 Nest array[2]={make_nest(),make_nest()};small(array[0].value);small(array[1].value);array[0].value[0]='X';require(array[1].value=="small",22);
 std::string left=choose(true),right=choose(false);small(left);small(right);require(left=="left"&&right=="right",23);
 std::puts("ok");}
