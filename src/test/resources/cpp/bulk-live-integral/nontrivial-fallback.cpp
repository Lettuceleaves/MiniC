#include <algorithm>
#include <vector>
#include <stdio.h>
int copies;int moves;int destroyed;int selectedTemplate;
struct Item {
    int value;
    Item(int n=0):value(n){}
    Item(const Item& other):value(other.value){}
    Item(Item&& other) noexcept:value(other.value){other.value=-900;}
    ~Item(){++destroyed;}
    Item& operator=(const Item& other) noexcept{++copies;value=other.value;return *this;}
    Item& operator=(Item&& other) noexcept{++moves;value=other.value;other.value=-900;return *this;}
};
struct Hybrid {
    int value;
    Hybrid& operator=(const Hybrid& other)=default;
    template<class U> Hybrid& operator=(U& other){++selectedTemplate;value=other.value;return *this;}
};
int main(){
    {
        Item from[3]={Item(2),Item(5),Item(8)};Item to[3];copies=0;moves=0;
        std::copy(from,from+3,to);
        printf("copy %d %d %d %d %d\n",copies,moves,to[0].value,to[1].value,to[2].value);
        copies=0;moves=0;std::move(from,from+3,to);
        printf("move %d %d %d %d %d %d\n",copies,moves,to[0].value,to[1].value,to[2].value,from[0].value);
    }
    {
        std::vector<Item> v;v.reserve(8);for(int i=0;i<8;++i)v.emplace_back(i);
        copies=0;moves=0;destroyed=0;auto result=v.erase(v.begin()+2,v.begin()+5);
        printf("erase %d %d %d %lld",copies,moves,destroyed,(long long)(result-v.begin()));
        for(unsigned long long i=0;i<v.size();++i)printf(" %d",v[i].value);printf("\n");
    }
    Hybrid from[2]={{11},{13}};Hybrid to[2]={{0},{0}};
    std::copy(from,from+2,to);
    printf("selected %d %d %d\n",selectedTemplate,to[0].value,to[1].value);
    return 0;
}
