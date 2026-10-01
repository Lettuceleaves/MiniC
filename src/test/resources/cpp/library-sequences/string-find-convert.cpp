#include <string>
#include <stdio.h>
#include <stdlib.h>
void require(bool okay,int line) { if(!okay){printf("failed %d\n",line);abort();} }
int main() {
    std::string text="abca cab";
    require(text.find("ca")==2 && text.rfind("ca")==5,7);
    require(text.find("")==0 && text.find("",text.size())==text.size(),8);
    require(text.find("",text.size()+1)==std::string::npos,9);
    require(text.rfind("")==text.size() && text.rfind("",2)==2,10);
    require(text.find_first_of("cz")==2 && text.find_last_of("az")==6,11);
    require(text.find_first_not_of("ab")==2 && text.find_last_not_of("ab")==5,12);
    require(text.find_first_not_of("")==0 && text.find_last_not_of("")==7,13);
    require(text.find_first_of("")==std::string::npos && text.find_last_of("")==std::string::npos,14);
    std::string empty;require(empty.rfind("")==0 && empty.find_last_not_of('x')==std::string::npos,15);
    std::string::size_type used=0;
    require(std::stoi(" -17tail",&used)==-17 && used==4,17);
    require(std::stoll("0x7fffffffffffffff",&used,0)==9223372036854775807LL && used==18,18);
    require(std::stoull("18446744073709551615")==18446744073709551615ULL,19);
    require(std::stod("1.25rest",&used)==1.25 && used==4,20);
    require(std::to_string(-9223372036854775807LL-1)=="-9223372036854775808",21);
    require(std::to_string(18446744073709551615ULL)=="18446744073709551615",22);
    require(std::to_string(1.25)=="1.250000",23);
    char high[1];high[0]=(char)0xff;std::string greater(high,1);
    require(greater>"z",25);
    require(text.compare(0,3,"abc")==0 && text.compare(5,3,std::string("xxcab"),2,3)==0,26);
    const std::string::size_type* npos=&std::string::npos;
    require(*npos==(std::string::size_type)-1,28);
    printf("string find convert ok\n");
}
