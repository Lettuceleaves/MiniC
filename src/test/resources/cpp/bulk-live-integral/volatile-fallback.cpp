#include <algorithm>
#include <stdio.h>
struct Field { volatile int value; };
int main(){
    volatile int source[3]={3,5,8};int destination[3]={};
    std::copy(source,source+3,destination);
    volatile int output[3]={};std::move(destination,destination+3,output);
    printf("%d %d %d\n",output[0],output[1],output[2]);
    Field fields[2]={{17},{23}};Field copied[2];std::copy(fields,fields+2,copied);
    printf("%d %d\n",copied[0].value,copied[1].value);
    return 0;
}
