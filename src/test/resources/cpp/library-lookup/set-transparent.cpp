#include <set>
#include <stdio.h>
struct Key{int value;explicit Key(int v):value(v){}};
struct Compare{
    typedef void is_transparent;
    bool operator()(const Key& a,const Key& b)const{return a.value<b.value;}
    bool operator()(const Key& a,int b)const{return a.value<b;}
    bool operator()(int a,const Key& b)const{return a<b.value;}
};
int main(){
    std::set<Key,Compare> unique;unique.emplace(2);unique.emplace(4);unique.emplace(6);
    const std::set<Key,Compare>& constant=unique;
    printf("%d %d %d %d %d\n",unique.find(4)->value,(int)unique.count(4),unique.lower_bound(3)->value,
        unique.upper_bound(4)->value,(int)std::distance(unique.equal_range(4).first,unique.equal_range(4).second));
    printf("%d %d %d %d %d\n",constant.find(4)->value,(int)constant.count(5),constant.lower_bound(3)->value,
        constant.upper_bound(4)->value,(int)std::distance(constant.equal_range(4).first,constant.equal_range(4).second));
    std::multiset<Key,Compare> duplicates;duplicates.emplace(2);duplicates.emplace(4);duplicates.emplace(4);duplicates.emplace(6);
    const std::multiset<Key,Compare>& view=duplicates;
    printf("%d %d %d %d %d\n",duplicates.find(4)->value,(int)duplicates.count(4),duplicates.lower_bound(3)->value,
        duplicates.upper_bound(4)->value,(int)std::distance(duplicates.equal_range(4).first,duplicates.equal_range(4).second));
    printf("%d %d %d %d %d\n",view.find(4)->value,(int)view.count(4),view.lower_bound(3)->value,
        view.upper_bound(4)->value,(int)std::distance(view.equal_range(4).first,view.equal_range(4).second));
    return 0;
}
