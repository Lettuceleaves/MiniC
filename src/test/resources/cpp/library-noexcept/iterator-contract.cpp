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
#if !defined(__MINIC__) && !defined(__MINIC_SELF_STL__) && defined(__MINGW32__) && defined(__GLIBCXX__) && __GLIBCXX__ == 20180502
// This exact host header omits array begin/end noexcept required by N4659.
// https://timsong-cpp.github.io/cppwp/n4659/iterator.range
static_assert(!noexcept(std::begin(std::declval<int(&)[3]>())),"libstdc++ 8.1 array begin declaration gap");
static_assert(!noexcept(std::end(std::declval<int(&)[3]>())),"libstdc++ 8.1 array end declaration gap");
#else
static_assert(noexcept(std::begin(std::declval<int(&)[3]>())),"array begin");
static_assert(noexcept(std::end(std::declval<int(&)[3]>())),"array end");
#endif
static_assert(noexcept(std::cbegin(std::declval<int(&)[3]>())),"array cbegin");
static_assert(std::is_same<decltype(std::cbegin(std::declval<int(&)[3]>())),const int*>::value,"array const element");
int main() { int a[3]={1,2,3};return std::cend(a)-std::cbegin(a)==3 ? 0 : 1; }
