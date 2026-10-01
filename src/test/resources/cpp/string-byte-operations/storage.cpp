#include <string>
#include <utility>
#include <stdio.h>
int valid(const std::string&s,int n,char c){if(s.size()!=(unsigned long long)n||s.data()[n]!=0)return 0;for(int i=0;i<n;++i)if(s[i]!=c)return 0;return 1;}
int main(){
 for(int n=0;n<40;++n){std::string s(n,'a');s.reserve(128);if(!valid(s,n,'a'))return 1;s.shrink_to_fit();if(!valid(s,n,'a'))return 2;
 std::string copy(s),assigned("different-heap-storage-that-will-shrink");assigned=copy;if(!valid(assigned,n,'a'))return 3;
 std::string moved(std::move(s));if(!valid(moved,n,'a'))return 4;s.assign(7,'b');if(!valid(s,7,'b'))return 5;
 s.swap(moved);if(!valid(s,n,'a')||!valid(moved,7,'b'))return 6;
 s.resize(0);s.shrink_to_fit();s.append(15,'c');if(!valid(s,15,'c'))return 7;s.push_back('d');if(s.size()!=16||s[15]!='d'||s.data()[16]!=0)return 8;
 s.resize(15);s.shrink_to_fit();if(!valid(s,15,'c'))return 9;
 }
 puts("ok");return 0;}
