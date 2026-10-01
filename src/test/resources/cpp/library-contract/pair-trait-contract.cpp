#include <type_traits>
#include <utility>
#include <cstdio>
struct ExplicitInt { int value; explicit ExplicitInt(int n):value(n){} };
struct ExplicitDefault { int value; explicit ExplicitDefault():value(7){} };
struct NotCopyable { NotCopyable()=default; NotCopyable(const NotCopyable&)=delete; NotCopyable(NotCopyable&&)=default; };
struct CopyOnly { int value; CopyOnly(int n):value(n){} CopyOnly(const CopyOnly&)=default; CopyOnly(CopyOnly&&)=delete; CopyOnly& operator=(const CopyOnly&)=default; CopyOnly& operator=(CopyOnly&&)=delete; };
static_assert(std::is_default_constructible<std::pair<const int,int>>::value,"const pair zero init");
static_assert(!std::is_copy_assignable<std::pair<const int,int>>::value,"const first is not assignable");
static_assert(!std::is_move_assignable<std::pair<int,const int>>::value,"const second is not assignable");
static_assert(std::is_copy_assignable<std::pair<int&,int&>>::value,"reference pair assigns referents");
static_assert(!std::is_copy_constructible<std::pair<NotCopyable,int>>::value,"deleted element copy");
static_assert(std::is_move_constructible<std::pair<NotCopyable,int>>::value,"valid element move");
static_assert(std::is_constructible<std::pair<ExplicitInt,int>,int,int>::value,"direct explicit element");
static_assert(!std::is_convertible<std::pair<int,int>,std::pair<ExplicitInt,int>>::value,"conditional explicit pair conversion");
static_assert(std::is_constructible<std::pair<ExplicitDefault,int>>::value,"direct explicit default");
static_assert(std::is_array<int[]>::value && std::is_array<const int[3]>::value,"both array bounds");
static_assert(std::is_same<std::remove_extent<int[]>::type,int>::value,"unknown bound extent");
static_assert(std::is_same<std::decay<int[]>::type,int*>::value,"unknown bound decay");
static_assert(std::is_same<std::add_lvalue_reference<void>::type,void>::value,"void reference transform");
int identity(int n){return n;}
int main(){
    int a=1,b=2,c=3,d=4;
    std::pair<int&,int&> first(a,b),second(c,d);
    first=second;
    std::pair<int&,int&> third(c,d);
    first=std::move(third);
    first.first=8;
    std::pair<ExplicitInt,int> explicit_pair(5,6);
    std::pair<ExplicitDefault,int> default_pair;
    CopyOnly element(9);
    std::pair<CopyOnly,int> copy_source(element,10),copy_target(element,0);
    copy_target=std::move(copy_source);
    int array[]={11,12};
    auto made=std::make_pair(array,identity);
    static_assert(std::is_same<decltype(made),std::pair<int*,int(*)(int)>>::value,"make_pair decays array and function");
    std::printf("%d %d %d %d %d %d %d %d\n",a,b,c,d,explicit_pair.first.value,default_pair.first.value,copy_target.second,made.second(made.first[1]));
    return 0;
}
