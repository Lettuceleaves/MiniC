#include <deque>
#include <stdio.h>
int main(){
    std::deque<int> values;int expected[2048]={};int first=800,last=800;
    values.push_back(777);expected[last++]=777;int* stable=&values.front();
    for(int i=0;i<300;++i){if(i%2){values.emplace_front(i);expected[--first]=i;}else{values.emplace_back(i);expected[last++]=i;}}
    if(*stable!=777||stable!=&values[150])return 1;
    for(int i=0;i<last-first;++i)if(values[i]!=expected[first+i])return 2;
    for(int i=0;i<70;++i){if(values.front()!=expected[first++]||values.back()!=expected[--last])return 3;values.pop_front();values.pop_back();}
    for(int i=0;i<160;++i){values.emplace_front(i+1000);expected[--first]=i+1000;values.emplace_back(i+2000);expected[last++]=i+2000;}
    while(!values.empty()){if(values.front()!=expected[first++])return 4;values.pop_front();}
    if(first!=last)return 5;
    for(int cycle=0;cycle<8;++cycle){values.emplace_front(cycle);if(&values.front()!=&values.back()||values.front()!=cycle)return 6;values.pop_back();values.emplace_back(cycle+20);if(values.back()!=cycle+20)return 7;values.pop_front();}
    values.resize(145,9);values.clear();values.shrink_to_fit();values.push_front(8);values.push_back(11);if(values[0]!=8||values[1]!=11)return 8;
    puts("ok");return 0;
}
