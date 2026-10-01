#include <iterator>
#include <type_traits>
#include <utility>
struct QuietRange {
    const int* begin() const noexcept;
    const int* end() const noexcept;
};
struct LoudRange {
    const int* begin() const noexcept(false);
    const int* end() const noexcept(false);
};
static_assert(noexcept(std::cbegin(std::declval<const QuietRange&>()))==noexcept(std::begin(std::declval<const QuietRange&>())),"cbegin reflects std::begin's guarantee");
static_assert(noexcept(std::cend(std::declval<const QuietRange&>()))==noexcept(std::end(std::declval<const QuietRange&>())),"cend reflects std::end's guarantee");
static_assert(!noexcept(std::cbegin(std::declval<const LoudRange&>())),"cbegin preserves potential throw");
static_assert(!noexcept(std::cend(std::declval<const LoudRange&>())),"cend preserves potential throw");
static_assert(noexcept(std::begin(std::declval<int(&)[3]>())),"array begin");
static_assert(noexcept(std::end(std::declval<int(&)[3]>())),"array end");
static_assert(noexcept(std::cbegin(std::declval<int(&)[3]>())),"array cbegin");
static_assert(std::is_same<decltype(std::cbegin(std::declval<int(&)[3]>())),const int*>::value,"array const element");
int main() { int a[3]={1,2,3};return std::cend(a)-std::cbegin(a)==3 ? 0 : 1; }
