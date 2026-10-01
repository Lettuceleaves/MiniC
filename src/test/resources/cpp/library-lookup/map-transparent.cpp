#include <map>
#include <type_traits>
#include <stdio.h>
struct Key{int value;explicit Key(int v):value(v){}};
struct Compare{
    typedef int is_transparent;
    bool operator()(const Key& a,const Key& b)const{return a.value<b.value;}
    bool operator()(const Key& a,int b)const{return a.value<b;}
    bool operator()(int a,const Key& b)const{return a<b.value;}
};
int main(){
    std::map<Key,int,Compare> values;values.emplace(Key(2),20);values.emplace(Key(4),40);values.emplace(Key(6),60);
    const std::map<Key,int,Compare>& view=values;
    printf("%d %d %d %d %d\n",values.find(4)->second,(int)values.count(5),values.lower_bound(3)->second,
        values.upper_bound(4)->second,(int)std::distance(values.equal_range(4).first,values.equal_range(4).second));
    printf("%d %d %d %d %d\n",view.find(2)->second,(int)view.count(6),view.lower_bound(3)->second,
        view.upper_bound(4)->second,(int)std::distance(view.equal_range(4).first,view.equal_range(4).second));
    return 0;
}
