#include <algorithm>
#include <iterator>
#include <vector>
#include <stdio.h>
int writes;
struct Proxy {int* pointer;Proxy& operator=(int value){++writes;*pointer=value+100;return *this;}};
struct Output {
    typedef std::output_iterator_tag iterator_category;
    typedef void value_type;
    typedef long long difference_type;
    typedef void reference;
    typedef void pointer;
    int* position;
    Proxy operator*()const{return Proxy{position};}
    Output& operator++(){++position;return *this;}
    Output operator++(int){Output old=*this;++*this;return old;}
};
int main(){
    int source[4]={1,2,3,4};int target[4]={};
    Output result=std::copy(source,source+4,Output{target});
    printf("proxy %d %lld",writes,(long long)(result.position-target));
    for(int i=0;i<4;++i)printf(" %d",target[i]);printf("\n");
    std::reverse_iterator<int*> first(source+4);std::reverse_iterator<int*> last(source);
    std::copy(first,last,target);printf("reverse");for(int i=0;i<4;++i)printf(" %d",target[i]);printf("\n");
    bool bits[5]={true,false,true,true,false};std::vector<bool> packed(5);
    std::copy(bits,bits+5,packed.begin());packed.erase(packed.begin()+1,packed.begin()+3);
    printf("bits");for(unsigned long long i=0;i<packed.size();++i)printf(" %d",(bool)packed[i]);printf("\n");
    return 0;
}
