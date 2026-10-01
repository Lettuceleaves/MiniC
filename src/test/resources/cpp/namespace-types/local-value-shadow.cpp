#include <stdio.h>
namespace Data {
    typedef long long Word;
    struct Record { int value; };
}
using Data::Word;
using Data::Record;
int main() {
    Word total = sizeof(Word);
    int hidden_size = 0;
    int record_value = 0;
    {
        int Word = 7;
        hidden_size = sizeof(Word);
        Data::Word explicit_value = (Data::Word)Word;
        total += explicit_value;
        int Record = 3;
        Data::Record item = {Record};
        record_value = item.value;
    }
    Word restored = (Word)1;
    Record restored_record = {record_value + 1};
    printf("%lld %d %d %lld %d\n", total, hidden_size, (int)sizeof(Word),
           restored, restored_record.value);
    return 0;
}
