#include <stdio.h>
namespace Types { typedef long long Word; }
namespace Init {
    using namespace Types;
    int Word = sizeof(Word);
}
using Types::Word;
int local_size() {
    int Word = sizeof(Word);
    return Word;
}
int main() {
    if (1) int Word = 99;
    int after_if = sizeof(Word);
    while (0) int Word = 99;
    int after_while = sizeof(Word);
    do int Word = 99; while (0);
    int after_do = sizeof(Word);
    for (int index = 0; index < 0; index++) int Word = 99;
    int after_for = sizeof(Word);
    switch (0) {
        case 0: int Word = 99; break;
    }
    int after_switch = sizeof(Word);
    printf("%d %d %d\n", Init::Word, local_size(),
           after_if + after_while + after_do + after_for + after_switch);
    return 0;
}
