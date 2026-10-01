#include <type_traits>

struct Quiet {
    Quiet() noexcept {}
    Quiet(const Quiet&) noexcept {}
    Quiet(Quiet&&) noexcept {}
    Quiet& operator=(const Quiet&) noexcept { return *this; }
    Quiet& operator=(Quiet&&) noexcept { return *this; }
};
struct Loud {
    Loud() noexcept(false) {}
    Loud(const Loud&) noexcept(false) {}
    Loud(Loud&&) noexcept(false) {}
    Loud& operator=(Loud&&) noexcept(false) { return *this; }
};
struct Deleted { Deleted() = delete; Deleted& operator=(const Deleted&) = delete; };
class Private { Private() noexcept {} Private& operator=(const Private&) noexcept { return *this; } };
namespace custom {
struct Adl { Adl(Adl&&) = delete; Adl& operator=(Adl&&) = delete; };
void swap(Adl&,Adl&) noexcept;
struct Throwing { Throwing(Throwing&&) = delete; Throwing& operator=(Throwing&&) = delete; };
void swap(Throwing&,Throwing&) noexcept(false);
struct Forbidden {};
void swap(Forbidden&,Forbidden&) = delete;
struct Left {};
struct Right {};
void swap(Left&,Right&) noexcept;
}
static_assert(std::is_nothrow_constructible<Quiet>::value,"quiet default");
static_assert(std::is_nothrow_default_constructible<Quiet[2][3]>::value,"bounded arrays recurse");
static_assert(std::is_nothrow_copy_constructible<Quiet>::value,"quiet copy");
static_assert(std::is_nothrow_move_constructible<Quiet>::value,"quiet move");
static_assert(std::is_nothrow_copy_assignable<Quiet>::value,"quiet copy assignment");
static_assert(std::is_nothrow_move_assignable<Quiet>::value,"quiet move assignment");
static_assert(std::is_nothrow_constructible<int&,int&>::value,"direct reference binding");
static_assert(!std::is_nothrow_default_constructible<int&>::value,"unbound reference");
static_assert(!std::is_nothrow_default_constructible<int[]>::value,"unknown array");
static_assert(!std::is_nothrow_default_constructible<const int[]>::value,"const unknown array");
static_assert(!std::is_nothrow_default_constructible<volatile int[]>::value,"volatile unknown array");
static_assert(!std::is_nothrow_constructible<const volatile int[],int>::value,"unknown array with argument");
#if defined(__MINIC__) || defined(__MINIC_SELF_STL__)
// MinGW 8's system is_constructible also misreports unknown bounds. Check the
// required contract on both MiniC and G++ compiling our source library.
static_assert(!std::is_constructible<int[]>::value,"unknown bound is not constructible");
static_assert(!std::is_constructible<const int[]>::value,"const unknown bound");
static_assert(!std::is_constructible<volatile int[]>::value,"volatile unknown bound");
static_assert(!std::is_constructible<const volatile int[]>::value,"const volatile unknown bound");
static_assert(!std::is_constructible<int[],int>::value,"unknown bound with scalar argument");
static_assert(!std::is_constructible<const int[],const int(&)[2]>::value,"unknown bound with array argument");
static_assert(!std::is_constructible<volatile int[],int,int>::value,"unknown bound with multiple arguments");
#endif
static_assert(!std::is_nothrow_default_constructible<void>::value,"void");
static_assert(!std::is_nothrow_constructible<Deleted>::value,"deleted constructor");
static_assert(!std::is_nothrow_constructible<Private>::value,"private constructor");
static_assert(!std::is_nothrow_assignable<Deleted&,const Deleted&>::value,"deleted assignment");
static_assert(!std::is_nothrow_assignable<Private&,const Private&>::value,"private assignment");
static_assert(!std::is_nothrow_copy_assignable<const int>::value,"const assignment");
static_assert(!std::is_nothrow_constructible<Loud>::value,"potentially throwing construction");
static_assert(!std::is_nothrow_move_assignable<Loud>::value,"potentially throwing assignment");
static_assert(std::is_nothrow_swappable<int>::value,"standard swap visible from type_traits");
static_assert(std::is_nothrow_swappable<custom::Adl>::value,"ADL overrides lack of move");
static_assert(std::is_nothrow_swappable<custom::Adl[2]>::value,"array element ADL");
static_assert(std::is_swappable<custom::Throwing>::value,"throwing swap is viable");
static_assert(!std::is_nothrow_swappable<custom::Throwing>::value,"ADL exception specification");
static_assert(!std::is_swappable<custom::Forbidden>::value,"deleted ADL winner");
static_assert(!std::is_swappable_with<custom::Left&,custom::Right&>::value,"both directions required");
static_assert(!std::is_swappable<void>::value,"void is not referenceable");
static_assert(!std::is_nothrow_swappable<const int>::value,"unavailable swap is false without hard error");
int main() { return 0; }
