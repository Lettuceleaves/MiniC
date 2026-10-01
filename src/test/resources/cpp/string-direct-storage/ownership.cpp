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
int main(){for(int n=0;n<=16;++n){
 std::string a((unsigned long long)n,'a');std::string copy(a);std::string moved(std::move(a));
 text(copy,n,'a');text(moved,n,'a');if(n!=0)require(copy.data()!=moved.data(),10);if(n<=15){small(copy);small(moved);}
 a.assign(4,'z');small(a);moved.assign((unsigned long long)n,'m');text(copy,n,'a');
 std::string heap(80,'h');heap=std::move(moved);text(heap,n,'m');if(n<=15)small(heap);valid(moved);
 moved.assign(3,'r');small(moved);text(heap,n,'m');copy=copy;text(copy,n,'a');
 heap=std::move(heap);valid(heap);heap.assign((unsigned long long)n,'b');heap.reserve(96);require(!inside(heap),11);
 heap.shrink_to_fit();text(heap,n,'b');if(n<=15)small(heap);
 const std::string& c=heap;require(c.data()==heap.data()&&c.c_str()==heap.data(),12);
 }std::puts("ok");}
