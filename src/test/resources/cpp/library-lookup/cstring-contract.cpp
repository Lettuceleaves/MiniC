#include <cstring>
#include <type_traits>
#include <stdio.h>
static_assert(std::is_same<decltype(std::strchr((const char*)0,'a')),const char*>::value,"const strchr");
static_assert(std::is_same<decltype(std::strchr((char*)0,'a')),char*>::value,"mutable strchr");
static_assert(std::is_same<decltype(std::strrchr((const char*)0,'a')),const char*>::value,"const strrchr");
static_assert(std::is_same<decltype(std::strrchr((char*)0,'a')),char*>::value,"mutable strrchr");
static_assert(std::is_same<decltype(std::strstr((const char*)0,"a")),const char*>::value,"const strstr");
static_assert(std::is_same<decltype(std::strstr((char*)0,"a")),char*>::value,"mutable strstr");
static_assert(std::is_same<decltype(std::strpbrk((const char*)0,"a")),const char*>::value,"const strpbrk");
static_assert(std::is_same<decltype(std::strpbrk((char*)0,"a")),char*>::value,"mutable strpbrk");
static_assert(std::is_same<decltype(std::memchr((const void*)0,'a',1)),const void*>::value,"const memchr");
static_assert(std::is_same<decltype(std::memchr((void*)0,'a',1)),void*>::value,"mutable memchr");
int main(){
    char text[8];std::memcpy(text,"abca",5);const char* readonly=text;
    char* (*mutable_find)(char*,int)=std::strchr;
    const char* (*constant_find)(const char*,int)=std::strchr;
    *mutable_find(text,'b')='B';
    printf("%d %d %d %d %d\n",(int)(constant_find(readonly,'a')-readonly),
        (int)(std::strrchr(readonly,'a')-readonly),(int)(std::strstr(readonly,"Bc")-readonly),
        (int)(std::strpbrk(readonly,"cz")-readonly),(int)((const char*)std::memchr(readonly,'B',4)-readonly));
    char transformed[32];std::size_t size=std::strxfrm(transformed,"abc",32);
    printf("%s %d %d %d\n",text,std::strcoll("abc","abc")==0,size>0,std::strerror(0)!=nullptr);
    return 0;
}
