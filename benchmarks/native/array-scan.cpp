#include <stdio.h>
#include <stdlib.h>

int main() {
    int n = 0;
    int rounds = 0;
    unsigned int seed = 0;
    if (scanf("%d %d %u", &n, &rounds, &seed) != 3) return 2;
    if (n < 1 || rounds < 1) return 3;
    int *values = (int *)malloc((unsigned long long)n * sizeof(int));
    if (values == (int *)0) return 4;
    unsigned int state = seed % 65521u;
    for (int i = 0; i < n; i++) {
        state = (state * 17u + 31u) % 65521u;
        values[i] = (int)state;
    }
    unsigned long long checksum = 0;
    for (int r = 0; r < rounds; r++) {
        for (int i = 0; i < n; i++) {
            values[i] = (values[i] + (i & 255) + (r & 255)) & 65535;
            checksum += (unsigned long long)values[i];
        }
    }
    printf("checksum=%llu\n", checksum);
    free(values);
    return 0;
}
