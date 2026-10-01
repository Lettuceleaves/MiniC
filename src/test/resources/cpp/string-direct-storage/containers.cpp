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
#include <vector>
#include <deque>
std::string made(int i){return std::string((unsigned long long)(i%18),(char)('a'+i%20));}
int main(){std::vector<std::string> v;std::deque<std::string> q;
 for(int i=0;i<90;++i){std::string source=made(i);v.push_back(source);q.push_back(std::move(source));}
 for(int i=0;i<90;++i){text(v[i],i%18,(char)('a'+i%20));text(q[i],i%18,(char)('a'+i%20));if(i%18<=15){small(v[i]);small(q[i]);}if(i%18!=0)require(v[i].data()!=q[i].data(),30);}
 v.reserve(240);std::vector<std::string> copied(v);std::vector<std::string> moved(std::move(v));
 for(int i=0;i<90;++i){text(copied[i],i%18,(char)('a'+i%20));text(moved[i],i%18,(char)('a'+i%20));if(i%18<=15){small(copied[i]);small(moved[i]);}}
 copied.erase(copied.begin()+2,copied.begin()+22);for(int i=2;i<70;++i){int k=i+20;text(copied[i],k%18,(char)('a'+k%20));if(k%18<=15)small(copied[i]);}
 for(int i=0;i<30;++i){q.emplace_front("front");}for(int i=0;i<30;++i){small(q.front());require(q.front()=="front",31);q.pop_front();}
 for(int i=0;i<90;++i){text(q.front(),i%18,(char)('a'+i%20));if(i%18<=15)small(q.front());q.pop_front();}
 std::puts("ok");}
