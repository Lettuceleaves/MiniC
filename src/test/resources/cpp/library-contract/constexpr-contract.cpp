#include <type_traits>
#include <utility>
#include <iterator>
#include <functional>
#include <algorithm>
#include <initializer_list>
#include <bitset>
static_assert(std::is_integral<int>{}, "trait implicit bool");
static_assert(std::is_integral<const int>()(), "trait callable value");
static_assert(!std::is_integral<double>{}, "false trait value");
static_assert(std::is_same<std::is_integral<int>::value_type,bool>::value, "trait value_type");
static_assert(std::is_same<std::is_integral<int>::type,std::true_type>::value, "trait base type");
constexpr std::pair<int,int> pair_value(2,3);
constexpr std::pair<int,int> pair_zero;
constexpr auto pair_made=std::make_pair(4,5);
static_assert(pair_value.first==2 && pair_value.second==3, "constexpr pair direct construction");
static_assert(pair_zero.first==0 && pair_zero.second==0, "constexpr pair value initialization");
static_assert(pair_made.first==4 && pair_made.second==5, "constexpr make_pair");
static_assert(pair_value<pair_made && pair_value!=pair_made, "constexpr comparison");
constexpr int scalar=6;
constexpr const int* scalar_address=std::addressof(scalar);
static_assert(scalar_address==&scalar && *scalar_address==6, "constexpr true address");
constexpr const int* trait_address=&std::integral_constant<int,9>::value;
static_assert(*trait_address==9, "ODR-used constexpr trait value");
constexpr int values[]={2,4,6};
static_assert(std::distance(std::begin(values),std::end(values))==3, "constexpr range navigation");
static_assert(*std::next(std::begin(values),2)==6, "constexpr next");
static_assert(*std::prev(std::end(values),2)==4, "constexpr prev");
static_assert(*std::rbegin(values)==6 && std::rend(values)-std::rbegin(values)==3, "constexpr reverse range");
static_assert(std::less<int>()(2,3), "typed predicate");
static_assert(std::greater<void>()(3,2), "transparent predicate");
static_assert(std::min(2,3)==2 && std::max(2,3)==3, "constexpr two-value minmax");
static_assert(std::min({3,1,2})==1 && std::max({3,1,2},std::less<int>())==3, "constexpr list minmax");
constexpr std::bitset<70> bit_value(3ULL);
constexpr std::bitset<0> bit_empty;
static_assert(bit_value.size()==70 && bit_value[0] && bit_value[1] && !bit_value[64], "constexpr bitset construction");
static_assert(bit_empty.size()==0, "empty constexpr bitset");
int main(){return 0;}
