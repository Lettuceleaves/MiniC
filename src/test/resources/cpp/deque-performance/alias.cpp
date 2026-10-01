#include <deque>
#include <stdio.h>
int main(){
    std::deque<int> values;values.push_back(17);
    for(int i=0;i<260;++i){values.push_front(values.back());values.push_back(values.front());}
    for(unsigned long long i=0;i<values.size();++i)if(values[i]!=17)return 1;
    values.front()=23;values.back()=41;
    int& from=values.emplace_front(values.back());int& to=values.emplace_back(values[1]);
    if(&from!=&values.front()||&to!=&values.back()||from!=41||to!=23)return 2;
    const std::deque<int>& view=values;if(view.front()!=41||view.back()!=23)return 3;
    while(values.size()>1){values.pop_front();if(values.size()>1)values.pop_back();}
    values.clear();values.push_back(91);values.emplace_front(values.front());if(values[0]!=91||values[1]!=91)return 4;
    puts("ok");return 0;
}
