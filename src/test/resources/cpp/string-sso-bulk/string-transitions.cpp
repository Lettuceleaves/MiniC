#include <string>
#include <utility>
#include <stdio.h>
int valid(const std::string&s,const char*text,int n){if(s.size()!=(unsigned long long)n||s.data()[n]!=0)return 0;for(int i=0;i<n;++i)if(s[i]!=text[i])return 0;return 1;}
int main(){for(int n=0;n<16;++n){char bytes[16];for(int i=0;i<n;++i)bytes[i]=(char)(i%4==0?0:i%4==1?255:'a'+i);bytes[n]=0;
 std::string a(bytes,n);std::string b(std::move(a));if(!valid(b,bytes,n))return 1;
 a.assign(7,'q');std::string heap(80,'H');heap=std::move(b);if(!valid(heap,bytes,n))return 2;
 heap.reserve(80);heap.shrink_to_fit();if(!valid(heap,bytes,n))return 3;
 heap.swap(a);if(!valid(a,bytes,n)||heap!=std::string(7,'q'))return 4;
 a=std::move(a);if(a.data()==nullptr||a.size()>a.capacity()||a.data()[a.size()]!=0)return 5;
#if defined(__MINIC__) || defined(__MINIC_SELF_STL__)
 if(!valid(a,bytes,n))return 5;
#endif
 a.assign(bytes,n);
 std::string other(std::move(a));a.assign(bytes,n);if(!valid(a,bytes,n)||!valid(other,bytes,n))return 6;
 }puts("ok");return 0;}
