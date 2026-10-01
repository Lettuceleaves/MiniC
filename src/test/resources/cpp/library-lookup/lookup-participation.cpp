#include <map>
#include <set>
#include <type_traits>
#include <stdio.h>
struct Key{int value;explicit Key(int v):value(v){}};
struct Opaque{
    bool operator()(const Key& a,const Key& b)const{return a.value<b.value;}
    bool operator()(const Key& a,int b)const{return a.value<b;}
    bool operator()(int a,const Key& b)const{return a<b.value;}
};
struct Transparent{
    typedef int is_transparent;
    bool operator()(const Key& a,const Key& b)const{return a.value<b.value;}
    bool operator()(const Key& a,int b)const{return a.value<b;}
    bool operator()(int a,const Key& b)const{return a<b.value;}
};
template<class C,class K,class=decltype(std::declval<C&>().find(std::declval<const K&>()))> std::true_type can_find(int);
template<class C,class K> std::false_type can_find(...);
template<class C,class K,class=decltype(std::declval<const C&>().count(std::declval<const K&>()))> std::true_type can_count(int);
template<class C,class K> std::false_type can_count(...);
static_assert(!decltype(can_find<std::map<Key,int,Opaque>,int>(0))::value,"opaque map removes hetero lookup");
static_assert(!decltype(can_find<std::set<Key,Opaque>,int>(0))::value,"opaque set removes hetero lookup");
static_assert(!decltype(can_count<std::multiset<Key,Opaque>,int>(0))::value,"opaque multiset removes hetero lookup");
static_assert(decltype(can_find<std::map<Key,int,Transparent>,int>(0))::value,"any nested type is transparent");
static_assert(decltype(can_find<std::set<Key,Transparent>,int>(0))::value,"transparent set");
static_assert(decltype(can_count<std::multiset<Key,Transparent>,int>(0))::value,"transparent multiset");
int main(){std::set<int,std::less<>> values;values.insert(4);printf("%d\n",*values.find(4L));return 0;}
