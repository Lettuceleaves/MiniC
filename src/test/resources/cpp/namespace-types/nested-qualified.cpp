#include <stdio.h>
namespace Outer::Inner {
    typedef long long Count;
    struct Record { Count value; };
}
namespace Other {
    using Outer::Inner::Record;
    Record copy(Record input) { return input; }
}
::Outer::Inner::Record global = {9};
int main() {
    Outer::Inner::Count increment = (Outer::Inner::Count)4;
    ::Outer::Inner::Record local = Other::copy(global);
    Outer::Inner::Record *pointer = &local;
    pointer->value += increment;
    printf("%lld %d\n", local.value, (int)sizeof(::Outer::Inner::Record));
    return 0;
}
