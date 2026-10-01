#include <iterator>
#include <type_traits>
#include <algorithm>
#include <vector>
#include <deque>
#include <set>
#include <cstdio>
struct BareRange {
    int values[3];
    int* begin(){return values;}
    int* end(){return values+3;}
    const int* begin() const{return values;}
    const int* end() const{return values+3;}
};
struct IncompleteIterator { typedef int value_type; };
template<class T,class=void> struct HasValue { static const bool value=false; };
template<class T> struct HasValue<T,decltype((void)sizeof(typename std::iterator_traits<T>::value_type))> { static const bool value=true; };
static_assert(!HasValue<IncompleteIterator>::value,"iterator_traits invalid primary is empty");
static_assert(HasValue<int*>::value,"raw pointer specialization");
static_assert(std::is_same<std::back_insert_iterator<std::vector<int>>::container_type,std::vector<int>>::value,"back inserter container_type");
static_assert(std::is_constructible<std::reverse_iterator<const int*>,std::reverse_iterator<int*>>::value,"reverse cv conversion");
static_assert(!std::is_constructible<std::reverse_iterator<int*>,std::reverse_iterator<const int*>>::value,"no reverse cv removal");
static_assert(!std::is_assignable<std::reverse_iterator<int*>&,std::reverse_iterator<const int*>>::value,"no reverse assignment cv removal");
int main(){
    int values[]={1,2,3};
    BareRange range={{4,5,6}};
    std::vector<int> back;
    std::copy(std::begin(values),std::end(values),std::back_inserter(back));
    std::deque<int> front;
    std::copy(std::cbegin(range),std::cend(range),std::front_inserter(front));
    std::set<int> set;
    std::copy(std::begin(values),std::end(values),std::inserter(set,set.end()));
    std::reverse_iterator<int*> r=std::make_reverse_iterator(values+3);
    std::reverse_iterator<const int*> cr;
    cr=r;
    auto list=std::initializer_list<int>{7,8};
    std::printf("%d %d %d %d %d %d %d\n",back[2],front.front(),front.back(),(int)set.size(),*cr,*std::rbegin(list),(int)(std::rend(values)-std::rbegin(values)));
    return 0;
}
