#include <map>
#include <set>
#include <queue>
#include <stack>
#include <vector>
#include <deque>
#include <type_traits>
#include <utility>
namespace custom {
struct Quiet {
    Quiet() = default;
    Quiet(const Quiet&) = default;
    Quiet(Quiet&&) = default;
    bool operator()(int a,int b) const { return a<b; }
    Quiet& operator=(Quiet&&) noexcept { return *this; }
};
void swap(Quiet&,Quiet&) noexcept {}
struct Loud {
    Loud() = default;
    Loud(const Loud&) = default;
    Loud(Loud&&) = default;
    bool operator()(int a,int b) const { return a<b; }
    Loud& operator=(Loud&&) noexcept(false) { return *this; }
};
void swap(Loud&,Loud&) noexcept(false) {}
struct Storage {
    typedef int value_type;
    typedef int& reference;
    typedef const int& const_reference;
    typedef unsigned long long size_type;
    std::deque<int> values;
    bool empty() const { return values.empty(); }
    size_type size() const { return values.size(); }
    reference front() { return values.front(); }
    const_reference front() const { return values.front(); }
    reference back() { return values.back(); }
    const_reference back() const { return values.back(); }
    void push_back(const int& value) { values.push_back(value); }
    void push_back(int&& value) { values.push_back(std::move(value)); }
    template<class... A> reference emplace_back(A&&... a) { return values.emplace_back(std::forward<A>(a)...); }
    void pop_back() { values.pop_back(); }
    void pop_front() { values.pop_front(); }
};
void swap(Storage& a,Storage& b) noexcept(false) { a.values.swap(b.values); }
}
typedef std::set<int,custom::Quiet> SQ;
typedef std::set<int,custom::Loud> SL;
typedef std::multiset<int,custom::Quiet> MS;
typedef std::map<int,int,custom::Quiet> MQ;
typedef std::map<int,int,custom::Loud> ML;
typedef std::priority_queue<int,std::vector<int>,custom::Quiet> PQ;
typedef std::priority_queue<int,std::vector<int>,custom::Loud> PL;
static_assert(noexcept(std::declval<SQ&>()=std::declval<SQ&&>()),"set move comparator");
static_assert(!noexcept(std::declval<SL&>()=std::declval<SL&&>()),"throwing set comparator");
static_assert(noexcept(std::swap(std::declval<SQ&>(),std::declval<SQ&>())),"set ADL comparator swap");
static_assert(!noexcept(std::swap(std::declval<SL&>(),std::declval<SL&>())),"throwing comparator swap");
static_assert(noexcept(std::swap(std::declval<MS&>(),std::declval<MS&>())),"multiset comparator swap");
static_assert(noexcept(std::declval<MQ&>()=std::declval<MQ&&>()),"map move comparator");
static_assert(!noexcept(std::declval<ML&>()=std::declval<ML&&>()),"throwing map move comparator");
static_assert(noexcept(std::swap(std::declval<MQ&>(),std::declval<MQ&>())),"map comparator swap");
static_assert(noexcept(std::declval<PQ&>().swap(std::declval<PQ&>())),"priority queue comparator swap");
static_assert(!noexcept(std::declval<PL&>().swap(std::declval<PL&>())),"throwing priority queue comparator");
static_assert(std::is_nothrow_swappable<std::queue<int>>::value,"queue default container swap");
static_assert(std::is_nothrow_swappable<std::stack<int>>::value,"stack default container swap");
static_assert(!std::is_nothrow_swappable<std::queue<int,custom::Storage>>::value,"queue custom container swap");
static_assert(!std::is_nothrow_swappable<std::stack<int,custom::Storage>>::value,"stack custom container swap");
static_assert(noexcept(std::declval<SQ&>().begin()) && noexcept(std::declval<const MQ&>().size()),"tree public observations");
int main() { return 0; }
