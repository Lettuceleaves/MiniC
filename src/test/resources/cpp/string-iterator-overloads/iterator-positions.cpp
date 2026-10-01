#include <string>
#include <cstdio>
#include <cstdlib>
void require(bool value){if(!value)::abort();}
int main(){
    std::string s("abc");
    require(s.insert(s.cbegin()+1,'x')==s.begin()+1);require(s=="axbc");
    s.insert(s.begin()+2,2,'y');require(s=="axyybc");
    char source[2]={'P','Q'};
    s.insert(s.cend(),source,source+2);s.insert(s.begin(),{'L','M'});require(s=="LMaxyybcPQ");
    require(s.erase(s.cbegin())==s.begin());require(s=="MaxyybcPQ");
    s.erase(s.begin(),s.cbegin()+1);require(s=="axyybcPQ");
    s.erase(s.cbegin()+1,s.begin()+3);require(s=="aybcPQ");
    s.replace(s.begin(),s.cbegin()+2,std::string("AB"));require(s=="ABbcPQ");
    s.replace(s.cbegin()+2,s.begin()+4,"CD");require(s=="ABCDPQ");
    s.replace(s.begin()+4,s.cend(),"EFz",2);require(s=="ABCDEF");
    s.replace(s.cbegin(),s.begin()+2,2,'x');require(s=="xxCDEF");
    s.replace(s.begin()+2,s.cbegin()+4,source,source+2);require(s=="xxPQEF");
    s.replace(s.cbegin()+4,s.end(),{'R','S'});require(s=="xxPQRS");
    require(s.data()[s.size()]==0);std::puts("iterator-positions ok");
}
