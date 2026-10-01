#include <stdio.h>
#ifdef __MINIC__
#define CONSTRUCTIBLE(...) __is_constructible(__VA_ARGS__)
#define ASSIGNABLE(...) __is_assignable(__VA_ARGS__)
#define CONVERTIBLE(...) __is_convertible(__VA_ARGS__)
#else
#include <type_traits>
#define CONSTRUCTIBLE(...) std::is_constructible<__VA_ARGS__>::value
#define ASSIGNABLE(...) std::is_assignable<__VA_ARGS__>::value
#define CONVERTIBLE(...) std::is_convertible<__VA_ARGS__>::value
#endif

struct Explicit { explicit Explicit(int); explicit operator int(); };
struct Implicit { Implicit(int); operator int(); };
class Private { Private(int); public: Private(double); };
struct Immutable { const int value; };
struct RefMember { int& value; };
class PrivateDtor { ~PrivateDtor(); };
struct Assignment { void operator=(int); };
struct RefConversion { operator int&(); };
struct Ambiguous { Ambiguous(long); Ambiguous(double); };
struct Forward;
int main() {
    printf("%d %d %d %d %d\n", CONSTRUCTIBLE(int&, double&), CONSTRUCTIBLE(const int&, double),
        CONSTRUCTIBLE(const int&, volatile int&), CONSTRUCTIBLE(int&&, int), CONSTRUCTIBLE(int&&, int&));
    printf("%d %d %d %d %d\n", CONSTRUCTIBLE(Explicit,int), CONVERTIBLE(int,Explicit),
        CONVERTIBLE(Explicit,int), CONSTRUCTIBLE(int,Explicit), CONVERTIBLE(int,Implicit));
    printf("%d %d %d %d %d\n", CONSTRUCTIBLE(Private,int), CONSTRUCTIBLE(Ambiguous,int),
        CONSTRUCTIBLE(PrivateDtor), CONSTRUCTIBLE(PrivateDtor&,PrivateDtor&), CONSTRUCTIBLE(Forward&,Forward&));
    printf("%d %d %d %d %d\n", ASSIGNABLE(int,int), ASSIGNABLE(int&,int),
        ASSIGNABLE(Immutable&,const Immutable&), ASSIGNABLE(RefMember&,const RefMember&), ASSIGNABLE(Assignment,int));
    printf("%d %d %d %d %d\n", CONVERTIBLE(void*,int*), CONVERTIBLE(int*,void*),
        CONSTRUCTIBLE(bool,decltype(nullptr)), CONVERTIBLE(decltype(nullptr),bool), CONSTRUCTIBLE(int&,RefConversion));
    printf("%d %d %d %d\n", CONSTRUCTIBLE(int[2]), CONSTRUCTIBLE(int[2],int),
        CONVERTIBLE(int[3],const int*), CONSTRUCTIBLE(int()));
    return 0;
}
