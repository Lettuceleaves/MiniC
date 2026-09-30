#include "probe_support.mh"

int main(void) {
    int scenario = getchar() - '0';
    if (scenario == 0) {
        int original = probe_construct(7);
        int copy = probe_copy(original);
        probe_check_independent(original, copy);
        int moved = probe_move(copy);
        probe_set(moved, 9);
        if (probe_value(original) != 7 || probe_value(moved) != 9) probe_errors++;
        int values[6] = {6, 5, 4, 3, 2, 1};
        for (int i = 1; i < 6; i++) {
            int value = values[i];
            int j = i;
            while (j > 0 && probe_less(value, values[j - 1])) {
                values[j] = values[j - 1];
                j--;
            }
            values[j] = value;
        }
        for (int i = 0; i < 6; i++) {
            if (values[i] != i + 1) probe_errors++;
        }
        probe_destroy(moved);
        probe_destroy(copy);
        probe_destroy(original);
    } else if (scenario == 1) {
        int object = probe_construct(7);
        probe_destroy(object);
        probe_destroy(object);
    } else if (scenario == 2) {
        probe_construct(7); /* Deliberate missing destructor; cleanup follows reporting. */
    } else if (scenario == 3) {
        int original = probe_construct(7);
        int shallow_copy = probe_register(probe_object_resource[original]);
        probe_copies++;
        probe_check_independent(original, shallow_copy);
        /* Undo the injected alias so the fixture does not perform a double free. */
        probe_object_resource[shallow_copy] = 0;
        probe_destroy(shallow_copy);
        probe_destroy(original);
    } else if (scenario == 4) {
        probe_allocate(7); /* Allocation without an owning object. */
    } else if (scenario == 5) {
        int resource = probe_allocate(7);
        probe_release(resource);
        probe_release(resource);
    } else {
        return 2;
    }
    probe_check_leaks();
    printf("live=%d created=%d destroyed=%d copies=%d moves=%d allocations=%d frees=%d "
           "comparisons=%d double_destroy=%d double_free=%d copy_alias=%d "
           "object_leaks=%d allocation_leaks=%d errors=%d\n",
           probe_live, probe_created, probe_destroyed, probe_copies, probe_moves,
           probe_allocations, probe_frees, probe_comparisons, probe_double_destroy,
           probe_double_free, probe_copy_alias, probe_object_leaks, probe_allocation_leaks, probe_errors);
    int failed = probe_errors != 0;
    probe_cleanup();
    return failed;
}
