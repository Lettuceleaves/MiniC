#include <stdio.h>

unsigned int mix(unsigned int state, unsigned int value) {
    return (state * 17u + value + 31u) % 65521u;
}

int main() {
    int n = 0;
    int rounds = 0;
    unsigned int seed = 0;
    if (scanf("%d %d %u", &n, &rounds, &seed) != 3) return 2;
    if (n < 1 || rounds < 1) return 3;
    unsigned int state = seed % 65521u;
    for (int r = 0; r < rounds; r++) {
        for (int i = 0; i < n; i++) state = mix(state, (unsigned int)(i & 255));
    }
    printf("checksum=%llu\n", (unsigned long long)state);
    return 0;
}
