#include <deque>
#include <utility>
#include <stdio.h>
#include <stdlib.h>
int live=0;
struct Item {
    int value;
    Item():value(0){++live;}
    explicit Item(int v):value(v){++live;}
    Item(const Item& other):value(other.value){++live;}
    Item(Item&& other):value(other.value){other.value=-1;++live;}
    Item& operator=(const Item& other){value=other.value;return *this;}
    Item& operator=(Item&& other){value=other.value;other.value=-1;return *this;}
    ~Item(){--live;}
};
void require(bool okay,int line) { if(!okay){printf("failed %d\n",line);abort();} }
int main() {
    {
        std::deque<Item> d;
        for(int i=0;i<270;++i)d.emplace_back(i);
        require(live==270,21);
        d.insert(d.begin()+2,130,d[5]);
        require(d.size()==400 && d[1].value==1 && d[2].value==5 && d[131].value==5 && d[132].value==2,23);
        d.erase(d.begin()+2,d.begin()+132);
        require(d.size()==270 && d[2].value==2,25);
        d.insert(d.end()-2,130,d[7]);
        require(d.size()==400 && d[268].value==7 && d[397].value==7 && d[398].value==268,27);
        d.erase(d.begin()+268,d.begin()+398);
        for(int i=0;i<270;++i)require(d[i].value==i,29);
        d.emplace(d.begin()+1,500);d.emplace(d.end()-1,600);
        require(d[1].value==500 && d[d.size()-2].value==600,31);
        d.erase(d.begin()+1);d.erase(d.end()-2);
        d.resize(300,d[5]);require(d.back().value==5,33);
        d.resize(270);d.resize(275);require(d.back().value==0,34);
        d.assign(3,d[4]);require(d.size()==3 && d.front().value==4,35);
        std::deque<Item> copy=d;
        copy=d;copy=copy;
        d.clear();require(live==3,38);
    }
    require(live==0,40);
    std::deque<int> a={1,2,3};
    std::deque<int> b={4,5};
    a.insert(a.begin()+1,b.begin(),b.end());
    require(a.size()==5 && a[0]==1 && a[1]==4 && a[2]==5 && a[3]==2 && a[4]==3,44);
    a.assign(b.begin(),b.end());require(a==b && !(a<b),45);
    a.insert(a.end(),{7,8});require(a.back()==8,46);
    printf("deque modifiers ok\n");
}
