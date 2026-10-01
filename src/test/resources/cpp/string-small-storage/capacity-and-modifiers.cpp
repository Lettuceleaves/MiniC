#include <string>
#include <utility>
#include <cstdio>
#include <cstdlib>
void require(bool okay,int point){if(!okay){std::printf("failed %d\n",point);std::abort();}}
void valid(const std::string& value){require(value.data()!=nullptr,1);require(value.size()<=value.capacity(),2);require(value.data()[value.size()]==0,3);require(value.begin()+value.size()==value.end(),4);}
int main(){
    std::string value="abcdefghijklmno";valid(value);require(value.size()==15,10);
    value.reserve(15);valid(value);require(value=="abcdefghijklmno",11);
    value.push_back('p');valid(value);require(value=="abcdefghijklmnop",12);
    value.pop_back();valid(value);require(value=="abcdefghijklmno",13);
    value.shrink_to_fit();valid(value);require(value=="abcdefghijklmno",14);
    value.reserve(96);valid(value);require(value.capacity()>=96,15);
    value.resize(16,'x');valid(value);require(value[15]=='x',16);
    value.resize(3);value.shrink_to_fit();valid(value);require(value=="abc",17);
    value.resize(15);valid(value);for(unsigned long long i=3;i<15;++i)require(value[i]==0,18);
    value.clear();value.shrink_to_fit();valid(value);require(value.empty(),19);
    value.assign(15,'q');value.erase(2,12);valid(value);require(value=="qqq",20);
    value.insert(value.begin()+1,2,'r');valid(value);require(value=="qrrqq",21);
    value.replace(1,2,3,'s');valid(value);require(value=="qsssqq",22);
    value.append({'a','b'});value.insert(value.end(),{'c','d'});valid(value);require(value=="qsssqqabcd",23);
    std::string part=value.substr(1,3);require(part=="sss",24);valid(part);
    require(value.find("abc")==6&&value.rfind('q')==5&&value.compare(value)==0,25);
    std::string sum='['+part+']';require(sum=="[sss]",26);valid(sum);
    std::printf("modifiers ok\n");
}
