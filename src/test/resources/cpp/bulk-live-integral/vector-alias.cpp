#include <vector>
#include <stdio.h>
struct Item {
    int value;
    Item(int n):value(n){}
    Item(const Item& x):value(x.value){}
    Item(Item&& x) noexcept:value(x.value){x.value=-900;}
    Item& operator=(const Item& x){value=x.value;return *this;}
    Item& operator=(Item&& x) noexcept{value=x.value;x.value=-900;return *this;}
};
void print(std::vector<Item>& v){for(unsigned long long i=0;i<v.size();++i)printf("%d ",v[i].value);printf("\n");}
int main(){
    std::vector<Item> v;v.reserve(2);v.emplace_back(7);v.emplace_back(9);
    v.push_back(v[0]);print(v);
    v.resize(6,v[0]);print(v);
    v.insert(v.begin()+1,v[2]);print(v);
    return 0;
}
