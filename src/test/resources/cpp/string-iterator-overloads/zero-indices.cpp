#include <string>
#include <cstdio>
#include <cstdlib>
void require(bool value){if(!value)::abort();}
int main(){
    std::string s("abc");
    require(&s.erase(0,0)==&s && s=="abc");
    require(&s.insert(0,0,'x')==&s && s=="abc");
    require(&s.replace(0,0,s.data(),0)==&s && s=="abc");
    s.insert(0,2,'x');require(s=="xxabc");
    s.replace(0,2,"y",1);require(s=="yabc");
    require(&s.erase(0)==&s && s.empty());
    s.insert(0,"p");s.replace(0,0,std::string("q"));require(s=="qp");
    s.replace(0,1,0,'z');require(s=="p" && s.data()[s.size()]==0);
    std::puts("zero-indices ok");
}
