#include <utility>
#include <type_traits>
#include <stdio.h>
int copies=0,moves=0;
struct CopyPreferred {
    CopyPreferred() {}
    CopyPreferred(const CopyPreferred&) { ++copies; }
    CopyPreferred(CopyPreferred&&) noexcept(false) { ++moves; }
};
struct MovePreferred {
    MovePreferred() {}
    MovePreferred(const MovePreferred&) { ++copies; }
    MovePreferred(MovePreferred&&) noexcept { ++moves; }
};
struct MoveOnly {
    MoveOnly() {}
    MoveOnly(const MoveOnly&) = delete;
    MoveOnly(MoveOnly&&) noexcept(false) { ++moves; }
};
static_assert(std::is_same<decltype(std::move_if_noexcept(std::declval<CopyPreferred&>())),const CopyPreferred&>::value,"copy fallback");
static_assert(std::is_same<decltype(std::move_if_noexcept(std::declval<MovePreferred&>())),MovePreferred&&>::value,"nonthrowing move");
static_assert(std::is_same<decltype(std::move_if_noexcept(std::declval<MoveOnly&>())),MoveOnly&&>::value,"move-only fallback");
static_assert(noexcept(std::move_if_noexcept(std::declval<CopyPreferred&>())),"selection itself does not throw");
int main() {
    CopyPreferred a;
    MovePreferred b;
    MoveOnly c;
    CopyPreferred aa(std::move_if_noexcept(a));
    MovePreferred bb(std::move_if_noexcept(b));
    MoveOnly cc(std::move_if_noexcept(c));
    printf("%d %d\n",copies,moves);
    return 0;
}
