#include <utility>
#include <type_traits>
#include <stdio.h>
namespace custom {
int swaps=0;
struct Item {
    int value;
    Item(int n):value(n) {}
    Item(Item&&) = delete;
    Item& operator=(Item&&) = delete;
};
void swap(Item& a,Item& b) noexcept { int n=a.value;a.value=b.value;b.value=n;++swaps; }
struct Throwing {
    Throwing() {}
    Throwing(Throwing&&) noexcept(false) {}
    Throwing& operator=(Throwing&&) noexcept(false) { return *this; }
};
}
static_assert(noexcept(std::declval<std::pair<int,int>&>()=std::declval<std::pair<int,int>&&>()),"pair move assignment");
static_assert(!noexcept(std::declval<std::pair<custom::Throwing,int>&>()=std::declval<std::pair<custom::Throwing,int>&&>()),"pair condition");
static_assert(std::is_nothrow_swappable<std::pair<int&,int&>>::value,"reference elements swap referents");
static_assert(!std::is_swappable<std::pair<const int,int>>::value,"const key cannot swap");
static_assert(noexcept(std::swap(std::declval<custom::Item(&)[2]>(),std::declval<custom::Item(&)[2]>())),"array swap uses ADL");
int main() {
    int a=1,b=2,c=3,d=4;
    std::pair<int&,int&> first(a,b),second(c,d);
    std::swap(first,second);
    custom::Item left[2]={custom::Item(5),custom::Item(6)};
    custom::Item right[2]={custom::Item(7),custom::Item(8)};
    std::swap(left,right);
    printf("%d %d %d %d %d %d %d\n",a,b,c,d,left[0].value,right[1].value,custom::swaps);
    return 0;
}
