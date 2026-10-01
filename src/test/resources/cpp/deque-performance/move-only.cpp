#include <deque>
#include <utility>
#include <stdio.h>
int live=0,created=0,destroyed=0,bad=0;
struct Large{
    int value;Large* self;char padding[600];
    explicit Large(int v):value(v),self(this){++live;++created;}
    Large(const Large&)=delete;
    Large&operator=(const Large&)=delete;
    Large(Large&& other):value(other.value),self(this){other.value=-1;++live;++created;}
    Large&operator=(Large&& other){value=other.value;other.value=-1;return *this;}
    ~Large(){if(self!=this)++bad;--live;++destroyed;}
};
int main(){
    {std::deque<Large> values;Large& anchor=values.emplace_back(123);Large* address=&anchor;
     for(int i=0;i<20;++i){values.emplace_front(i);values.emplace_back(i+50);}
     if(address!=&values[20]||address->value!=123||live!=41)return 1;
     for(int i=0;i<10;++i){if(values.front().value!=19-i||values.back().value!=69-i)return 2;values.pop_front();values.pop_back();}
     values.emplace_front(std::move(values.back()));if(values.front().value!=59||values.back().value!=-1)return 3;
     values.emplace_back(std::move(values.front()));if(values.back().value!=59||values.front().value!=-1)return 4;
     std::deque<Large> moved(std::move(values));if(address!=&moved[11]||address->value!=123)return 5;
     moved.clear();if(live!=0)return 6;moved.emplace_front(88);moved.pop_back();moved.emplace_back(99);moved.pop_front();
    }
    if(live!=0||bad!=0||created!=destroyed)return 7;puts("ok");return 0;
}
