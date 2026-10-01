#include <bitset>
#include <vector>
#include <utility>
typedef std::bitset<70> B;
typedef std::bitset<70>::reference BR;
typedef std::vector<bool>::reference VR;
static_assert(noexcept(std::declval<BR&>()=true),"bitset proxy assignment");
static_assert(noexcept(std::declval<BR&>()=std::declval<const BR&>()),"bitset proxy copy assignment");
static_assert(noexcept(bool(std::declval<BR>())),"bitset proxy conversion");
static_assert(noexcept(~std::declval<BR>()),"bitset proxy complement");
static_assert(noexcept(std::declval<BR&>().flip()),"bitset proxy flip");
static_assert(noexcept(std::declval<B&>().set()),"whole bitset set");
static_assert(noexcept(std::declval<B&>().reset()),"whole bitset reset");
static_assert(noexcept(std::declval<B&>().flip()),"whole bitset flip");
static_assert(noexcept(std::declval<const B&>().count()),"bit count");
static_assert(noexcept(std::declval<const B&>().any()) && noexcept(std::declval<const B&>().all()),"bit predicates");
static_assert(noexcept(std::declval<const B&>()&std::declval<const B&>()),"bit operators");
static_assert(noexcept(std::declval<B&>()<<=3) && noexcept(std::declval<const B&>()>>3),"bit shifts");
static_assert(noexcept(std::declval<const B&>()==std::declval<const B&>()),"bit equality");
static_assert(noexcept(std::declval<VR&>()=true),"vector proxy assignment");
static_assert(noexcept(bool(std::declval<VR>())),"vector proxy conversion");
static_assert(noexcept(std::declval<VR&>().flip()),"vector proxy flip");
static_assert(noexcept(std::declval<std::vector<bool>&>().flip()),"vector bit flip");
int main() {
    B a(3),b(5);a&=b;a<<=2;
    std::vector<bool> bits(3,false);bits[1]=true;bits.flip();
    return a.count()==1 && a[2] && bits[0] && !bits[1] && bits[2] ? 0 : 1;
}
