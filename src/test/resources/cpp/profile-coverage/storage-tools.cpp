#include <vector>
#include <memory>
#include <new>
#include <utility>
#include <type_traits>
#include <cstdio>
#include <cstdlib>

void require(bool value,int line){if(!value){std::printf("storage failure %d\n",line);std::abort();}}
static_assert(std::is_same<std::enable_if<true,long>::type,long>::value,"enable_if selected type");
static_assert(std::is_same<std::enable_if<true>::type,void>::value,"enable_if default type");
template<class T,typename std::enable_if<std::is_integral<T>::value,int>::type=0>int enabled(T){return 1;}
int enabled(...){return 2;}
static_assert(std::is_same<decltype(std::forward<int&>(std::declval<int&>())),int&>::value,"forward lvalue");
static_assert(std::is_same<decltype(std::forward<const int&>(std::declval<const int&>())),const int&>::value,"forward const lvalue");
static_assert(std::is_same<decltype(std::forward<int>(std::declval<int>())),int&&>::value,"forward rvalue");
int choice(int&){return 1;}int choice(const int&){return 2;}int choice(int&&){return 3;}
template<class T>int relay(T&& value){return choice(std::forward<T>(value));}
int evaluations=0;int fresh(){++evaluations;return 7;}
int live=0,trace=0;
struct Item{
    int value;explicit Item(int n):value(n){++live;}
    Item(const Item& x):value(x.value){++live;}
    Item& operator=(const Item& x){value=x.value;return *this;}
    ~Item(){--live;trace=trace*10+value;}
};
int main(){
    int value=4;const int constant=5;
    require(enabled(3)==1&&enabled(3.5)==2,29);
    require(relay(value)==1&&relay(constant)==2&&relay(fresh())==3&&evaluations==1,30);
    require(&std::forward<int&>(value)==&value && &std::forward<const int&>(constant)==&constant,31);
    std::vector<int> values;require(values.capacity()>=values.size(),32);
    values.reserve(12);auto capacity=values.capacity();require(capacity>=12&&values.empty(),33);
    values.assign(3,7);int* address=values.data();values.reserve(capacity-1);
    require(values.size()==3&&values[2]==7&&values.data()==address&&values.capacity()==capacity,35);
    int input[]={2,4,6,8};values.assign(input+1,input+4);require(values.size()==3&&values[0]==4&&values[2]==8,36);
    values.assign({9,1});require(values.size()==2&&values[0]==9&&values[1]==1,37);
    values.assign(input,input);require(values.empty()&&values.capacity()>=values.size(),38);
    {
        Item held(4);std::vector<Item> items;items.assign(3,held);require(live==4&&items[2].value==4,40);
        items.assign(1,held);require(live==2&&items[0].value==4,41);items.clear();require(live==1,42);
    }
    require(live==0,44);trace=0;
    // memory.mh is an internal typed-storage layer. The standard build performs the
    // equivalent raw allocation/placement construction/destruction explicitly.
#if defined(__MINIC__) || defined(__MINIC_SELF_STL__)
    Item* storage=std::__minic_allocate<Item>(3);
    require(std::__minic_allocate<Item>(0)==nullptr,49);
    std::__minic_construct(storage,1);std::__minic_construct(storage+1,2);std::__minic_construct(storage+2,3);
#else
    Item* storage=(Item*)std::malloc(3*sizeof(Item));require(storage!=nullptr,52);
    ::new((void*)storage)Item(1);::new((void*)(storage+1))Item(2);::new((void*)(storage+2))Item(3);
#endif
    require(live==3&&storage[0].value==1&&storage[2].value==3,55);
#if defined(__MINIC__) || defined(__MINIC_SELF_STL__)
    std::__minic_destroy_range(storage,3);std::__minic_deallocate(storage);
#else
    for(int i=3;i>0;--i)storage[i-1].~Item();std::free(storage);
#endif
    require(live==0&&trace==321,61);std::printf("storage tools ok\n");return 0;
}
