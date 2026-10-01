#include <bitset>
#include <cstddef>
#include <cstdio>
// Same acceptance source for the default word-count implementation and the
// MINIC_BITSET_FORCE_KERNIGHAN fallback; no direct call to either helper.
template<std::size_t N>
int check(const std::bitset<N>& bits,const unsigned char* model,int phase){
    std::size_t count=0;
    for(std::size_t i=0;i<N;++i){count+=model[i]?1:0;if(bits[i]!=(model[i]!=0))return phase;}
    if(bits.count()!=count||bits.all()!=(count==N)||bits.any()!=(count!=0)||bits.none()!=(count==0))return phase;
    return 0;
}
template<std::size_t N>
int exercise(){
    std::bitset<N> bits;
    unsigned char model[N?N:1]={};
    int failure=check<N>(bits,model,1);if(failure)return failure;
    if(N){bits.set(0);model[0]=1;}
    failure=check<N>(bits,model,2);if(failure)return failure;
    if(N>1){bits.set(N-1);model[N-1]=1;}
    failure=check<N>(bits,model,3);if(failure)return failure;
    bits.set();for(std::size_t i=0;i<N;++i)model[i]=1;
    failure=check<N>(bits,model,4);if(failure)return failure;
    bits.flip();for(std::size_t i=0;i<N;++i)model[i]=0;
    failure=check<N>(bits,model,5);if(failure)return failure;
    for(std::size_t i=0;i<N;++i){model[i]=(unsigned char)(i%2);bits.set(i,model[i]!=0);}
    failure=check<N>(bits,model,6);if(failure)return failure;
    unsigned long long state=1729;
    for(std::size_t i=0;i<N;++i){state=(state*1664525ULL+1013904223ULL)&0xffffffffULL;model[i]=(unsigned char)((state>>27)&1);bits.set(i,model[i]!=0);}
    failure=check<N>(bits,model,7);if(failure)return failure;
    bits.flip();for(std::size_t i=0;i<N;++i)model[i]=!model[i];
    failure=check<N>(bits,model,8);if(failure)return failure;
    const std::size_t shifts[]={0,1,63,64,65,N,N+1};
    for(int k=0;k<7;++k){
        std::size_t amount=shifts[k];unsigned char expected[N?N:1]={};
        std::bitset<N> left=bits<<amount;
        for(std::size_t i=0;i<N;++i)expected[i]=i>=amount?model[i-amount]:0;
        failure=check<N>(left,expected,9+2*k);if(failure)return failure;
        std::bitset<N> right=bits>>amount;
        for(std::size_t i=0;i<N;++i)expected[i]=i+amount<N?model[i+amount]:0;
        failure=check<N>(right,expected,10+2*k);if(failure)return failure;
        // Mutating forms must trim the tail too, including shift >= N.
        left=bits;left<<=amount;
        for(std::size_t i=0;i<N;++i)expected[i]=i>=amount?model[i-amount]:0;
        failure=check<N>(left,expected,31);if(failure)return failure;
        left.flip();for(std::size_t i=0;i<N;++i)expected[i]=!expected[i];
        failure=check<N>(left,expected,32);if(failure)return failure;
        right=bits;right>>=amount;
        for(std::size_t i=0;i<N;++i)expected[i]=i+amount<N?model[i+amount]:0;
        failure=check<N>(right,expected,33);if(failure)return failure;
    }
    return 0;
}
int main(){
    int code=exercise<0>();if(code){std::printf("N=0 phase=%d\n",code);return 1;}
    code=exercise<1>();if(code){std::printf("N=1 phase=%d\n",code);return 1;}
    code=exercise<63>();if(code){std::printf("N=63 phase=%d\n",code);return 1;}
    code=exercise<64>();if(code){std::printf("N=64 phase=%d\n",code);return 1;}
    code=exercise<65>();if(code){std::printf("N=65 phase=%d\n",code);return 1;}
    code=exercise<127>();if(code){std::printf("N=127 phase=%d\n",code);return 1;}
    code=exercise<128>();if(code){std::printf("N=128 phase=%d\n",code);return 1;}
    code=exercise<129>();if(code){std::printf("N=129 phase=%d\n",code);return 1;}
    code=exercise<1024>();if(code){std::printf("N=1024 phase=%d\n",code);return 1;}
    std::printf("bitset count paths ok\n");return 0;
}
