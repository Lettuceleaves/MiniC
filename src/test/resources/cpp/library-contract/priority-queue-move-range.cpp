#include <queue>
#include <vector>
#include <functional>
#include <type_traits>
#include <cstdio>
struct Sequence {
    typedef int value_type;
    typedef std::vector<int>::size_type size_type;
    typedef int& reference;
    typedef const int& const_reference;
    typedef std::vector<int>::iterator iterator;
    static int copies;
    static int moves;
    std::vector<int> values;
    Sequence()=default;
    Sequence(const Sequence& other):values(other.values){++copies;}
    Sequence(Sequence&& other):values(std::move(other.values)){++moves;}
    iterator begin(){return values.begin();}
    iterator end(){return values.end();}
    template<class I> iterator insert(iterator where,I first,I last){return values.insert(where,first,last);}
    void push_back(const int& value){values.push_back(value);}
    void push_back(int&& value){values.push_back(std::move(value));}
    void pop_back(){values.pop_back();}
    const_reference front() const{return values.front();}
    bool empty() const{return values.empty();}
    size_type size() const{return values.size();}
};
int Sequence::copies=0;
int Sequence::moves=0;
static_assert(std::is_same<std::priority_queue<int,Sequence>::reference,int&>::value,"priority queue reference typedef");
int main(){
    Sequence values;
    values.push_back(2);
    int added[]={4,1,3};
    std::priority_queue<int,Sequence> heap(added,added+3,std::less<int>(),std::move(values));
    std::printf("%d %d",Sequence::copies,Sequence::moves);
    while(!heap.empty()){std::printf(" %d",heap.top());heap.pop();}
    std::printf("\n");
    return 0;
}
