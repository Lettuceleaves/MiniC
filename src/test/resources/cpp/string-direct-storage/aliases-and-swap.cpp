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
int main(){const char* samples[4]={"","a","abcdefghijklmno","abcdefghijklmnop"};
 for(int i=0;i<4;++i)for(int j=0;j<4;++j){std::string a(samples[i]),b(samples[j]);a.swap(b);require(a==samples[j]&&b==samples[i],40);if(j<3)small(a);if(i<3)small(b);std::swap(a,b);require(a==samples[i]&&b==samples[j],41);if(i<3)small(a);if(j<3)small(b);}
 for(int n=0;n<=16;++n){std::string a((unsigned long long)n,'x');a.append(a.data(),a.size());text(a,2*n,'x');
 a.assign("abcdef");a.replace(1,4,a.data()+2,4);require(a=="acdeff",42);a.shrink_to_fit();small(a);
 a.append(a.data()+a.size(),1);require(a.size()==7&&a[6]==0,43);a.insert(0,a.data()+1,4);require(a.size()==11&&a[10]==0,44);valid(a);
 a.reserve(128);a.assign(a.data()+4,7);require(a.size()==7&&a[6]==0,45);a.shrink_to_fit();small(a);
 a.clear();a.shrink_to_fit();small(a);a.push_back((char)255);require((unsigned char)a[0]==255,46);small(a);
 }std::puts("ok");}
