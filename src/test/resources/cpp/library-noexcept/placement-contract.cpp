#include <new>
#include <utility>
struct Quiet { Quiet() noexcept {} };
struct Loud { Loud() noexcept(false) {} };
static_assert(noexcept(::operator new(sizeof(int),std::declval<void*>())),"standard placement allocation");
static_assert(noexcept(::operator delete(std::declval<void*>(),std::declval<void*>())),"matching placement delete");
static_assert(noexcept(new (std::declval<void*>()) Quiet()),"placement construction combines allocator and constructor");
static_assert(!noexcept(new (std::declval<void*>()) Loud()),"placement construction preserves throwing constructor");
int main() { return 0; }
