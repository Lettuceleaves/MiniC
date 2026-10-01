#include <string>
#include <utility>
#include <cstdio>
#include <cstdlib>
void require(bool okay,int point){if(!okay){std::printf("failed %d\n",point);std::abort();}}
void valid(const std::string& value){require(value.data()!=nullptr,1);require(value.size()<=value.capacity(),2);require(value.data()[value.size()]==0,3);require(value.begin()+value.size()==value.end(),4);}
int main(){
    std::string s="abcdefghijklmno";
    s.append(s.data()+14,1);require(s=="abcdefghijklmnoo",10);valid(s);
    s="abcdefghijklmno";s.append(s.c_str()+s.size(),1);
    require(s.size()==16&&s[15]==0&&s[16]==0,11);valid(s);
    s="abcdefghijklmnop";s.assign(s.data()+1,14);require(s=="bcdefghijklmno",12);valid(s);
    s.shrink_to_fit();s.insert(0,s.data()+12,2);require(s=="nobcdefghijklmno",13);valid(s);
    s="abcdef";s.insert(2,s.data()+1,4);require(s=="abbcdecdef",14);valid(s);
    s="abcdef";s.replace(1,4,s.data()+2,4);require(s=="acdeff",15);valid(s);
    s="abc";s+=s;require(s=="abcabc",16);valid(s);
    s.insert(s.begin()+1,s.begin(),s.end());require(s=="aabcabcbcabc",17);valid(s);
    s="abcdef";s.replace(s.begin()+1,s.begin()+5,s.begin()+2,s.end());require(s=="acdeff",18);valid(s);
    s="abcdefghijklmnop";s.replace(1,14,s.data()+2,12);require(s=="acdefghijklmnp",19);valid(s);
    s="abc";s.append(s.data(),0);s.insert(1,s.data()+s.size(),0);s.replace(0,0,s.data(),0);s.assign(s.data(),s.size());
    require(s=="abc",20);valid(s);
    char output[4]={'?','?','?','!'};require(s.copy(output,3)==3&&output[3]=='!'&&output[2]=='c',21);
    s="abc";std::string sum=std::move(s)+s;require(sum=="abcabc",22);valid(s);valid(sum);
    s="abcdefgh";sum=s+std::move(s);require(sum=="abcdefghabcdefgh",23);valid(s);valid(sum);
    s="abc";sum=std::move(s)+std::move(s);require(sum=="abcabc",24);valid(s);valid(sum);
    std::printf("alias ok\n");
}
