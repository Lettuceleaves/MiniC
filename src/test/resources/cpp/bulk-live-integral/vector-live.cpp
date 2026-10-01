#include <vector>
#include <stdio.h>
void dump(int group,int id,std::vector<int>& values,long long position) {
    printf("%d %d %lld %llu",group,id,position,(unsigned long long)values.size());
    for(unsigned long long i=0;i<values.size();++i)printf(" %d",values[i]);
    printf("\n");
}
int main() {
    int starts[6]={0,0,2,6,0,8};int ends[6]={0,1,5,8,8,8};
    for(int c=0;c<6;++c) {
        std::vector<int> v;v.reserve(16);for(int i=0;i<8;++i)v.push_back(i);
        auto result=v.erase(v.begin()+starts[c],v.begin()+ends[c]);
        dump(0,c,v,(long long)(result-v.begin()));
    }
    int oldSizes[5]={3,8,4,2,5};int newSizes[5]={6,3,4,20,5};
    for(int c=0;c<5;++c) {
        std::vector<int> a;a.reserve(c==3?2:16);for(int i=0;i<oldSizes[c];++i)a.push_back(-i-1);
        std::vector<int> b;for(int i=0;i<newSizes[c];++i)b.push_back(i*3+1);
        std::vector<int>* result;
        if(c==4)result=&(a=a);else result=&(a=b);
        dump(1,c,a,result==&a?0:-1);
    }
    int counts[3]={1,6,9};
    for(int capacity=0;capacity<2;++capacity)for(int c=0;c<3;++c) {
        std::vector<int> v;if(capacity)v.reserve(32);for(int i=0;i<8;++i)v.push_back(i);
        auto result=v.insert(v.begin()+2,counts[c],v[1]);
        dump(2,capacity*3+c,v,(long long)(result-v.begin()));
    }
    std::vector<int> empty;
    if(empty.erase(empty.begin(),empty.end())!=empty.end())return 1;
    empty=empty;
    printf("empty-ok\n");return 0;
}
