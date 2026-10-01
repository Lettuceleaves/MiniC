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
struct Deleted { Deleted()=delete; Deleted(int)=delete; Deleted(double); };
struct Assignment { void operator=(int)=delete; void operator=(double); };
struct Conversion { operator int()=delete; };
struct Destructor { ~Destructor()=delete; };
struct Defaulted { Defaulted()=default; Defaulted(const Defaulted&)=default; Defaulted& operator=(const Defaulted&)=default; };
struct Member { const int value; };
struct Propagated { Member value; Propagated()=default; Propagated& operator=(const Propagated&)=default; };
int main() {
    printf("%d %d %d %d\n", CONSTRUCTIBLE(Deleted), CONSTRUCTIBLE(Deleted,int),
        ASSIGNABLE(Assignment&,int), CONVERTIBLE(Conversion,int));
    printf("%d %d %d %d %d\n", CONSTRUCTIBLE(Destructor), CONSTRUCTIBLE(Defaulted),
        ASSIGNABLE(Defaulted&,const Defaulted&), CONSTRUCTIBLE(Propagated), ASSIGNABLE(Propagated&,const Propagated&));
    return 0;
}
