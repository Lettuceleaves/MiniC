#include <string>
#include <utility>
#include <cstdio>
#include <cstdlib>
void require(bool okay,int point){if(!okay){std::printf("failed %d\n",point);std::abort();}}
void valid(const std::string& value){require(value.data()!=nullptr,1);require(value.size()<=value.capacity(),2);require(value.data()[value.size()]==0,3);require(value.begin()+value.size()==value.end(),4);}
int main(){
    const char* text[5]={"","a","abcdefghijklmno","abcdefghijklmnop","abcdefghijklmnopqrstuvwxyz012345"};
    for(int a=0;a<5;++a)for(int b=0;b<5;++b){
        std::string left=text[a],right=text[b];left.swap(right);
        require(left==text[b]&&right==text[a],10);valid(left);valid(right);
        std::swap(left,right);require(left==text[a]&&right==text[b],11);valid(left);valid(right);
        std::string moved(std::move(left));require(moved==text[a],12);valid(moved);valid(left);
        left="reused";require(left=="reused",13);valid(left);
        right=std::move(moved);require(right==text[a],14);valid(right);valid(moved);
        moved="again";require(moved=="again",15);valid(moved);
        right=right;require(right==text[a],16);valid(right);
        right.swap(right);require(right==text[a],17);valid(right);
        right=std::move(right);valid(right); // Contents are valid but unspecified.
        right="after self move";require(right=="after self move",18);valid(right);
        std::string& alias=right;right.assign(std::move(alias));valid(right);
        right="final";require(right=="final",19);
    }
    std::printf("moves ok\n");
}
