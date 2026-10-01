#include <map>
struct Key{int value;explicit Key(int v):value(v){}};
struct Compare{
    bool operator()(const Key& a,const Key& b)const{return a.value<b.value;}
    bool operator()(const Key& a,int b)const{return a.value<b;}
    bool operator()(int a,const Key& b)const{return a<b.value;}
};
int main(){std::map<Key,int,Compare> values;return values.find(1)==values.end();}
