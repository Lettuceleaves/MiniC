#include "minic_std.mh"

#define ENABLED
#define START 1
#define LIMIT 5

int main() {
    int value = START;

#ifdef ENABLED
    value += 2;
#else
    value = 0;
#endif

    do {
        value++;
    } while (value < LIMIT);

    switch (value) {
        case 5:
            value = value % 4;
        case 1:
            value = (value << 2) | 1;
            break;
        default:
            value = sizeof(int);
    }

    printf("phase_d=%d\n", value);
    return value ? value : sizeof value;
}
