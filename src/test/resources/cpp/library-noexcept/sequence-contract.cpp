#include <vector>
#include <deque>
#include <string>
#include <type_traits>
#include <utility>
typedef std::vector<int> V;
typedef std::deque<int> D;
typedef std::string S;
static_assert(noexcept(V()),"default allocator vector");
static_assert(noexcept(V(std::declval<V&&>())),"vector move construction");
static_assert(noexcept(std::declval<V&>()=std::declval<V&&>()),"vector move assignment");
static_assert(noexcept(std::declval<V&>().swap(std::declval<V&>())),"vector swap");
static_assert(noexcept(std::declval<V&>().begin()) && noexcept(std::declval<const V&>().crend()),"vector iterators");
static_assert(noexcept(std::declval<V&>().data()) && noexcept(std::declval<const V&>().data()),"vector data");
static_assert(noexcept(std::declval<V&>().clear()) && noexcept(std::declval<const V&>().capacity()),"vector clear/capacity");
static_assert(noexcept(std::declval<D&>()=std::declval<D&&>()),"deque move assignment");
static_assert(noexcept(std::swap(std::declval<D&>(),std::declval<D&>())),"deque swap");
static_assert(noexcept(std::declval<D&>().begin()) && noexcept(std::declval<const D&>().cend()),"deque iterators");
static_assert(noexcept(std::declval<D&>().clear()) && noexcept(std::declval<const D&>().max_size()),"deque clear/capacity");
static_assert(noexcept(S()),"string default");
static_assert(noexcept(S(std::declval<S&&>())),"string move");
static_assert(noexcept(std::declval<S&>()=std::declval<S&&>()),"string move assignment");
static_assert(noexcept(std::declval<S&>().assign(std::declval<S&&>())),"string assign move");
static_assert(noexcept(std::swap(std::declval<S&>(),std::declval<S&>())),"string swap");
static_assert(noexcept(std::declval<const S&>().find(std::declval<const S&>())),"string find");
static_assert(noexcept(std::declval<const S&>().rfind(std::declval<const S&>())),"string rfind");
static_assert(noexcept(std::declval<const S&>().find_first_of(std::declval<const S&>())),"string find_first_of");
static_assert(noexcept(std::declval<const S&>().find_last_not_of(std::declval<const S&>())),"string find_last_not_of");
static_assert(noexcept(std::declval<const S&>().compare(std::declval<const S&>())),"string compare");
static_assert(noexcept(std::declval<const S&>()==std::declval<const S&>()),"string equality");
static_assert(noexcept(std::declval<const S&>()<std::declval<const S&>()),"string order");
int main() {
    V a={1,2},b={3};std::swap(a,b);
    D c={4,5},d={6};std::swap(c,d);
    S e="first",f="second";std::swap(e,f);
    return a[0]==3 && b.size()==2 && c.front()==6 && d.size()==2 && e=="second" ? 0 : 1;
}
