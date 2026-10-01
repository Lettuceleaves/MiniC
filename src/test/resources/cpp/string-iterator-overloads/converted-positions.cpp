#include <string>
#include <type_traits>
#include <cstdio>
#include <cstdlib>
void require(bool value){if(!value)::abort();}
int conversions=0;
struct Position {
    std::string::const_iterator value;
    operator std::string::const_iterator()const {++conversions;return value;}
};
struct NoCopyPosition {
    std::string::const_iterator value;
    NoCopyPosition(std::string::const_iterator input):value(input){}
    NoCopyPosition(const NoCopyPosition&)=delete;
    operator std::string::const_iterator()const {++conversions;return value;}
};
static_assert(!std::is_convertible<int,std::string::const_iterator>::value,"integer type is not a null pointer constant");
static_assert(std::is_convertible<Position,std::string::const_iterator>::value,"retain implicit conversion");
int main(){
    std::string s("abc");
    s.insert(Position{s.cbegin()+1},'x');require(s=="axbc" && conversions==1);
    s.insert(Position{s.cbegin()+2},2,'y');require(s=="axyybc" && conversions==2);
    char text[2]={'P','Q'};
    s.insert(Position{s.cend()},text,text+2);require(s=="axyybcPQ" && conversions==3);
    s.insert(Position{s.cbegin()},{'L'});require(s=="LaxyybcPQ" && conversions==4);
    s.erase(Position{s.cbegin()});require(s=="axyybcPQ" && conversions==5);
    s.erase(Position{s.cbegin()+1},s.cbegin()+3);require(s=="aybcPQ" && conversions==6);
    s.replace(s.cbegin(),Position{s.cbegin()+2},std::string("AB"));require(s=="ABbcPQ" && conversions==7);
    s.replace(Position{s.cbegin()+2},Position{s.cbegin()+4},"CD");require(s=="ABCDPQ" && conversions==9);
    s.replace(Position{s.cbegin()+4},s.cend(),"EFz",2);require(s=="ABCDEF" && conversions==10);
    s.replace(s.cbegin(),Position{s.cbegin()+2},2,'x');require(s=="xxCDEF" && conversions==11);
    s.replace(Position{s.cbegin()+2},s.cbegin()+4,text,text+2);require(s=="xxPQEF" && conversions==12);
    s.replace(s.cbegin()+4,Position{s.cend()},{'R','S'});require(s=="xxPQRS" && conversions==13);
    NoCopyPosition first(s.cbegin());s.erase(first);require(s=="xPQRS" && conversions==14);
    s.insert(NoCopyPosition(s.cbegin()),'g');require(s=="gxPQRS" && conversions==15);
    std::puts("converted-positions ok");
}
