/* Reference-only C++17 fixture until the language/libraries are implemented.
   This source deliberately uses real special members and std::vector. */
#include "probe_support.mh"
#include <algorithm>
#include <memory>
#include <utility>
#include <vector>

class Tracked {
    int object_;
public:
    explicit Tracked(int value) : object_(probe_construct(value)) {}
    Tracked(const Tracked& source) : object_(probe_copy(source.object_)) {
        probe_check_independent(source.object_, object_);
    }
    Tracked(Tracked&& source) noexcept : object_(probe_move(source.object_)) {}
    ~Tracked() { probe_destroy(object_); }
    int value() const { return probe_value(object_); }
    void set(int value) { probe_set(object_, value); }
};

int allocator_live;
int allocator_calls;
template<class T> struct CountingAllocator {
    using value_type = T;
    CountingAllocator() = default;
    template<class U> CountingAllocator(const CountingAllocator<U>&) noexcept {}
    T* allocate(std::size_t count) {
        T* memory = std::allocator<T>{}.allocate(count);
        allocator_live++;
        allocator_calls++;
        return memory;
    }
    void deallocate(T* memory, std::size_t count) noexcept {
        std::allocator<T>{}.deallocate(memory, count);
        allocator_live--;
    }
    template<class U> bool operator==(const CountingAllocator<U>&) const noexcept { return true; }
    template<class U> bool operator!=(const CountingAllocator<U>&) const noexcept { return false; }
};

int main() {
    int reserve_extra;
    {
        std::vector<Tracked, CountingAllocator<Tracked>> values;
        values.reserve(4);
        int before = allocator_calls;
        values.emplace_back(7);
        values.emplace_back(9);
        reserve_extra = allocator_calls - before;
        Tracked copy(values[0]);
        copy.set(11);
        Tracked moved(std::move(copy));
        if (values[0].value() != 7 || values[1].value() != 9 || moved.value() != 11) probe_errors++;
    }
    int values[6] = {6, 5, 4, 3, 2, 1};
    std::sort(values, values + 6, probe_less);
    int comparison_ok = probe_comparisons > 0 && probe_comparisons < 100;
    for (int i = 0; i < 6; i++) {
        if (values[i] != i + 1) comparison_ok = 0;
    }
    if (allocator_live != 0 || reserve_extra != 0 || !comparison_ok) probe_errors++;
    probe_check_leaks();
    printf("live=%d created=%d destroyed=%d copies=%d moves=%d allocations=%d frees=%d "
           "allocator_live=%d reserve_extra=%d comparison_ok=%d errors=%d\n",
           probe_live, probe_created, probe_destroyed, probe_copies, probe_moves,
           probe_allocations, probe_frees, allocator_live, reserve_extra, comparison_ok, probe_errors);
    return probe_errors != 0;
}
