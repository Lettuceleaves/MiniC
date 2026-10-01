#include <algorithm>
#include <functional>
#include <cstdio>
#include <cstdlib>

void require(bool value,int line){if(!value){std::printf("algorithm failure %d\n",line);std::abort();}}
struct Tens{bool operator()(int a,int b)const{return a/10==b/10;}};
bool heap(const int* a,int n,bool minimum){for(int i=1;i<n;++i)if(minimum?a[(i-1)/2]>a[i]:a[(i-1)/2]<a[i])return false;return true;}
struct RandomIndex{int calls;bool valid;RandomIndex():calls(0),valid(true){}int operator()(int n){++calls;if(n<=0)valid=false;return 0;}};
void permutation(const int* a,int n){for(int v=0;v<n;++v){int count=0;for(int i=0;i<n;++i)if(a[i]==v)++count;require(count==1,10);}}
int main(){
    int filled[]={9,9,9,9,9};std::fill(filled+1,filled+4,7);std::fill(filled,filled,2);
    require(filled[0]==9&&filled[1]==7&&filled[3]==7&&filled[4]==9,13);
    int reversed[]={1,2,3,4,5};std::reverse(reversed,reversed+5);std::reverse(reversed,reversed);std::reverse(reversed+2,reversed+3);
    require(reversed[0]==5&&reversed[2]==3&&reversed[4]==1,15);
    int duplicate[]={1,1,2,2,2,3};int* end=std::unique(duplicate,duplicate+6);
    require(end==duplicate+3&&duplicate[0]==1&&duplicate[1]==2&&duplicate[2]==3,17);
    require(std::unique(duplicate,duplicate)==duplicate&&std::unique(duplicate,duplicate+1)==duplicate+1,18);
    int groups[]={11,12,21,22,23,31};require(std::unique(groups,groups+6,Tens())==groups+3&&groups[1]==21&&groups[2]==31,19);
    int ascending[]={1,2,2,2,4};
    require(std::upper_bound(ascending,ascending+5,2)==ascending+4&&std::upper_bound(ascending,ascending+5,0)==ascending,21);
    require(std::upper_bound(ascending,ascending+5,9)==ascending+5&&std::upper_bound(ascending,ascending,2)==ascending,22);
    require(std::binary_search(ascending,ascending+5,2)&&!std::binary_search(ascending,ascending+5,3)&&!std::binary_search(ascending,ascending,1),23);
    int descending[]={4,2,2,1};std::greater<int> greater;
    require(std::upper_bound(descending,descending+4,2,greater)==descending+3&&std::binary_search(descending,descending+4,2,greater)&&!std::binary_search(descending,descending+4,3,greater),25);
    int p[]={1,1,2};int visits=0,trace=0;
    do{++visits;trace=trace*1000+p[0]*100+p[1]*10+p[2];}while(std::next_permutation(p,p+3));
    require(visits==3&&trace==112121211&&p[0]==1&&p[2]==2,28);
    int q[]={2,1,1};visits=0;trace=0;
    do{++visits;trace=trace*1000+q[0]*100+q[1]*10+q[2];}while(std::prev_permutation(q,q+3));
    require(visits==3&&trace==211121112&&q[0]==2&&q[2]==1,31);
    visits=0;do{++visits;}while(std::next_permutation(q,q+3,greater));require(visits==3&&q[0]==2&&q[2]==1,32);
    visits=0;do{++visits;}while(std::prev_permutation(p,p+3,greater));require(visits==3&&p[0]==1&&p[2]==2,33);
    require(!std::next_permutation(p,p)&&!std::prev_permutation(p,p+1)&&p[0]==1,34);
    int h[6]={3,1,4,1,5,9};std::make_heap(h,h+5);require(heap(h,5,false)&&h[0]==5,35);
    std::push_heap(h,h+6);require(heap(h,6,false)&&h[0]==9,36);
    std::pop_heap(h,h+6);require(heap(h,5,false)&&h[5]==9,37);std::sort_heap(h,h+5);
    require(h[0]==1&&h[1]==1&&h[2]==3&&h[3]==4&&h[4]==5&&h[5]==9,38);
    int m[6]={3,1,4,1,5,0};std::make_heap(m,m+5,greater);require(heap(m,5,true)&&m[0]==1,39);
    std::push_heap(m,m+6,greater);require(heap(m,6,true)&&m[0]==0,40);
    std::pop_heap(m,m+6,greater);require(heap(m,5,true)&&m[5]==0,41);std::sort_heap(m,m+5,greater);
    require(m[0]==5&&m[1]==4&&m[2]==3&&m[3]==1&&m[4]==1,42);
    std::make_heap(h,h);std::sort_heap(h,h);std::make_heap(h,h+1);std::push_heap(h,h+1);std::pop_heap(h,h+1);std::sort_heap(h,h+1);require(h[0]==1,43);
    // Explicit pre-C++17 compatibility extension: check permutations, never a rand sequence.
    int shuffled[]={0,1,2,3,4,5,6,7};std::srand(123);std::random_shuffle(shuffled,shuffled+8);permutation(shuffled,8);
    RandomIndex random;std::random_shuffle(shuffled,shuffled+8,random);permutation(shuffled,8);require(random.valid&&random.calls==7,46);
    std::random_shuffle(shuffled,shuffled);std::random_shuffle(shuffled,shuffled+1,random);require(random.calls==7,47);
    std::printf("algorithms ok\n");return 0;
}
