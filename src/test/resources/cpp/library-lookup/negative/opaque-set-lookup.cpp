#include <set>
struct Key{int value;explicit Key(int v):value(v){}};
struct Compare{
    bool operator()(const Key& a,const Key& b)const{return a.value<b.value;}
    bool operator()(const Key& a,int b)const{return a.value<b;}
    bool operator()(int a,const Key& b)const{return a<b.value;}
};
int main(){const std::multiset<Key,Compare> values;return (int)values.count(1);}
