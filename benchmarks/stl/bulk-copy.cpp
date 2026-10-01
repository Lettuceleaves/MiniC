#include <algorithm>
#include <vector>
#include <stdio.h>

unsigned long long next_value(unsigned long long& state) {
    state=(state*1664525ULL+1013904223ULL)&0xffffffffULL;
    return (state>>8)&65535ULL;
}
unsigned long long add_hash(unsigned long long hash,unsigned long long value) {
    return (hash^value)*1099511628211ULL;
}
unsigned long long observe(unsigned long long hash,const int* values,int count) {
    hash=add_hash(hash,(unsigned long long)count);
    for(int i=0;i<count;++i)hash=add_hash(hash,(unsigned long long)values[i]);
    return hash;
}
int main() {
    int mode=0,size=0,rounds=0,iterations=0,seed=0;
    if(scanf("%d %d %d %d %d",&mode,&size,&rounds,&iterations,&seed)!=5)return 2;
    if(mode<0||mode>3||size<1||size>262144||rounds<1||rounds>10000||
       iterations<1||iterations>10000||seed<0||
       (unsigned long long)size*(unsigned long long)rounds*(unsigned long long)iterations>200000000ULL)return 3;
    // The volatile function-pointer load keeps observation opaque to inlining:
    // the completed destination must exist before every indirect observer call.
    unsigned long long (*volatile observer)(unsigned long long,const int*,int)=observe;
    for(int round=0;round<rounds;++round) {
        unsigned long long state=((unsigned long long)seed+(unsigned long long)round*1013904223ULL)&0xffffffffULL;
        unsigned long long hash=14695981039346656037ULL;
        if(mode==0) {
            // Both buffers and all elements exist before the measured repeated assignment.
            std::vector<int> source(size),destination(size);
            for(int i=0;i<size;++i)source[i]=(int)next_value(state);
            for(int iteration=0;iteration<iterations;++iteration) {
                state^=hash&0xffffffffULL;
                int index=(int)(next_value(state)%(unsigned long long)size);
                source[index]=(int)next_value(state);
                destination=source;
                hash=add_hash(hash,(unsigned long long)iteration);
                hash=observer(hash,destination.data(),size);
            }
        } else if(mode==1) {
            std::vector<int> values(size);
            for(int i=0;i<size;++i)values[i]=(int)next_value(state);
            for(int iteration=0;iteration<iterations;++iteration) {
                // Rebuild only the removed scalar tail; reserve capacity is retained.
                values.resize(size);
                state^=hash&0xffffffffULL;
                int index=(int)(next_value(state)%(unsigned long long)size);
                values[index]=(int)next_value(state);
                int count=1+(int)(next_value(state)%(unsigned long long)(size/4+1));
                int first=(int)(next_value(state)%(unsigned long long)(size-count+1));
                auto result=values.erase(values.begin()+first,values.begin()+first+count);
                hash=add_hash(hash,(unsigned long long)iteration);
                hash=add_hash(hash,(unsigned long long)(result-values.begin()));
                hash=add_hash(hash,(unsigned long long)count);
                hash=observer(hash,values.data(),(int)values.size());
            }
        } else {
            std::vector<int> values(size+8);
            for(int i=0;i<size+8;++i)values[i]=(int)next_value(state);
            for(int iteration=0;iteration<iterations;++iteration) {
                state^=hash&0xffffffffULL;
                int index=(int)(next_value(state)%(unsigned long long)(size+8));
                values[index]=(int)next_value(state);
                int gap=1+(int)(next_value(state)%8ULL);
                int* begin=values.data();
                int* result;
                // copy overlaps only to the left; copy_backward overlaps only to the right.
                if(mode==2)result=std::copy(begin+gap,begin+gap+size,begin);
                else result=std::copy_backward(begin,begin+size,begin+gap+size);
                hash=add_hash(hash,(unsigned long long)iteration);
                hash=add_hash(hash,(unsigned long long)(result-begin));
                hash=observer(hash,begin,size+8);
            }
        }
        printf("round=%d hash=%llu\n",round,hash);
    }
    return 0;
}
