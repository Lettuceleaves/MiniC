#include <functional>
#include <type_traits>
#include <cstdio>
struct Verdict { int value; explicit operator bool() const{return value!=0;} };
struct Item { int value; };
Verdict operator<(const Item& a,const Item& b){return {a.value<b.value};}
Verdict operator==(const Item& a,const Item& b){return {a.value==b.value};}
static_assert(std::is_same<std::less<int>::first_argument_type,int>::value,"typed first argument");
static_assert(std::is_same<std::greater<int>::second_argument_type,int>::value,"typed second argument");
static_assert(std::is_same<std::less<int>::result_type,bool>::value,"typed result");
static_assert(std::is_same<decltype(std::less<void>()(std::declval<Item>(),std::declval<Item>())),Verdict>::value,"transparent less preserves result");
static_assert(std::is_same<decltype(std::equal_to<void>()(std::declval<Item>(),std::declval<Item>())),Verdict>::value,"transparent equal preserves result");
int main(){Item a={1},b={2};Verdict result=std::less<void>()(a,b);std::printf("%d\n",result.value);return 0;}
