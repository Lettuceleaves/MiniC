#include <algorithm>
#include <stdio.h>
int main(){
    int source[3]={1,-2,16777217};float destination[3]={};
    std::copy(source,source+3,destination);
    if(destination[0]!=1.0f||destination[1]!=-2.0f||destination[2]!=16777216.0f)return 1;
    int one=1;int two=2;int* pointers[2]={&one,&two};const int* output[2]={nullptr,nullptr};
    std::copy(pointers,pointers+2,output);
    printf("%d %d %d %d %d\n",(int)destination[0],(int)destination[1],(int)destination[2],*output[0],*output[1]);
    return 0;
}
