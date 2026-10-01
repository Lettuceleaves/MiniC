#include <type_traits>
#include <stdio.h>
constexpr bool inherited_call() { return std::is_same<int,int>{}(); }
constexpr bool inherited_conversion() { return std::is_nothrow_assignable<int&,int>{}; }
int use_true(const std::true_type& value) { return value() ? 7 : 0; }
int use_false(const std::false_type* value) { return (*value)() ? 0 : 9; }
static_assert(inherited_call(),"inherited constexpr call");
static_assert(inherited_conversion(),"inherited constexpr conversion");
static_assert(std::is_same<std::is_same<int,int>::type,std::true_type>::value,"type belongs to base");
static_assert(std::is_same<std::is_same<int,int>::value_type,bool>::value,"inherited typedef");
static_assert(std::is_convertible<std::is_same<int,int>*,std::true_type*>::value,"public base conversion");
static_assert(std::is_convertible<std::is_same<int,long>&,const std::false_type&>::value,"base reference");
static_assert(!std::is_same<std::is_same<int,int>,std::true_type>::value,"trait is distinct derived type");
int main() {
    std::is_same<int,int> yes;
    std::is_same<int,long> no;
    const bool* inherited=&std::is_same<int,int>::value;
    const bool* base=&std::true_type::value;
    printf("%d %d %d\n",use_true(yes),use_false(&no),inherited==base);
    return 0;
}
