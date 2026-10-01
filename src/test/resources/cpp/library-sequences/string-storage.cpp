#include <string>
#include <utility>
#include <stdio.h>
#include <stdlib.h>
void require(bool okay,int line) { if(!okay){printf("failed %d\n",line);abort();} }
int main() {
    std::string s;
    require(s.data()!=nullptr && s.c_str()[0]==0 && s.begin()==s.end(),8);
    s="abcdef";s.append(s.data()+1,4);require(s=="abcdefbcde",9);
    s="abcdef";s.insert(2,s.data()+1,4);require(s=="abbcdecdef",10);
    s="abcdef";s.replace(1,4,s.data()+2,4);require(s=="acdeff",11);
    s="abcdef";s.assign(s.data()+2,3);require(s=="cde",12);
    s="abc";s+=s;require(s=="abcabc",13);
    s.insert(s.begin()+1,s.begin(),s.end());require(s=="aabcabcbcabc",14);
    s="abcdef";s.replace(s.begin()+1,s.begin()+5,s.begin()+2,s.end());require(s=="acdeff",15);
    s="abc";s.append(s.c_str()+s.size(),1);
    require(s.size()==4 && s[3]==0 && s.c_str()[4]==0,17);
    char raw[5];raw[0]='a';raw[1]=0;raw[2]='b';raw[3]=0;raw[4]='c';
    std::string binary(raw,5);require(binary.size()==5 && binary[4]=='c',19);
    require(binary.substr(1,3)==std::string(raw+1,3),20);
    char copied[6];copied[5]='!';
    require(binary.copy(copied,5)==5 && copied[5]=='!' && copied[2]=='b',22);
    std::string moved=std::move(binary);
    require(moved.size()==5 && binary.c_str()[binary.size()]==0,24);
    binary="reuse";require(binary=="reuse",25);
    moved.reserve(300);require(moved.size()==5 && moved.capacity()>=300 && moved[2]=='b',26);
    moved.resize(8,'x');require(moved[7]=='x' && moved[8]==0,27);
    moved.resize(2);require(moved.size()==2 && moved.c_str()[2]==0,28);
    moved.clear();moved.shrink_to_fit();require(moved.data()!=nullptr && moved[0]==0,29);
    moved={'a','b','c'};moved.insert(moved.end(),{'d','e'});require(moved=="abcde",30);
    require(std::string("你好").size()==6,31);
    std::string utf8="你好";require(utf8.data()[utf8.size()]==0,32);
    require('x'+moved=="xabcde" && moved+'y'=="abcdey",33);
    printf("string storage ok\n");
}
