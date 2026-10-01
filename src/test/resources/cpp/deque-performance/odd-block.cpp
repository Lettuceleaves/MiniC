#include <deque>
#include <stdio.h>
struct Three{char bytes[3];explicit Three(int n){bytes[0]=(char)(n%100);bytes[1]=7;bytes[2]=9;}};
int main(){if(sizeof(Three)!=3)return 1;std::deque<Three> values;
    for(int i=0;i<190;++i){values.emplace_front(i);values.emplace_back(i+10);}
    for(int i=0;i<190;++i){if(values.front().bytes[0]!=(189-i)%100||values.back().bytes[0]!=(199-i)%100)return 2;values.pop_front();values.pop_back();}
    if(!values.empty())return 3;
    for(int i=0;i<5;++i){values.emplace_front(19);values.pop_back();values.emplace_back(31);values.pop_front();}
    values.emplace_front(47);if(values.front().bytes[0]!=47||values.back().bytes[1]!=7)return 4;
    puts("ok");return 0;}
