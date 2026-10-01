#include <vector>
#include <bitset>
#include <algorithm>
#include <cstdio>
int main(){
    std::vector<bool> bits(130);bits[0]=true;bits[63]=true;bits[64]=true;bits[129]=true;
    bits.insert(bits.begin()+64,5,true);bits.erase(bits.begin()+2,bits.begin()+7);
    std::iter_swap(bits.begin(),bits.end()-1);bits.flip();
    std::sort(bits.begin(),bits.end());int count=0;for(bool b:bits)if(b)++count;
    std::bitset<130> word;word.set(0).set(63).set(64).set(129);word=(word<<1)^(word>>64);
    std::printf("%llu %d %llu %d %d\n",(unsigned long long)bits.size(),count,(unsigned long long)word.count(),word[64]?1:0,word[129]?1:0);
    std::bitset<0> empty;std::printf("%d %d %llu\n",empty.all(),empty.none(),(unsigned long long)empty.count());
}
