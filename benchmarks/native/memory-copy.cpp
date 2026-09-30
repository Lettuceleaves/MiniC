#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main() {
    int n = 0;
    int rounds = 0;
    unsigned int seed = 0;
    if (scanf("%d %d %u", &n, &rounds, &seed) != 3) return 2;
    if (n < 1 || rounds < 1) return 3;
    unsigned long long bytes = (unsigned long long)n * sizeof(int);
    int *source = (int *)malloc(bytes);
    int *target = (int *)malloc(bytes);
    if (source == (int *)0 || target == (int *)0) {
        free(source);
        free(target);
        return 4;
    }
    unsigned int state = seed % 65521u;
    for (int i = 0; i < n; i++) {
        state = (state * 17u + 31u) % 65521u;
        source[i] = (int)state;
    }
    for (int r = 0; r < rounds; r++) {
        memcpy(target, source, bytes);
        int index = r % n;
        target[index] = (target[index] + (r & 255) + 1) & 65535;
        int *temporary = source;
        source = target;
        target = temporary;
    }
    unsigned long long checksum = 0;
    for (int i = 0; i < n; i++) checksum += (unsigned long long)source[i];
    printf("checksum=%llu\n", checksum);
    free(source);
    free(target);
    return 0;
}
