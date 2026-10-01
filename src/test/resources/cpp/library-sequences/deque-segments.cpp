#include <deque>
#include <utility>
#include <stdio.h>
#include <stdlib.h>
void require(bool okay,int line) { if(!okay){printf("failed %d\n",line);abort();} }
int main() {
    std::deque<int> values;
    require(values.begin()==values.end(),8);
    values.push_back(42);
    int* stable=&values.front();
    for(int i=0;i<2000;++i){values.push_front(-i-1);values.push_back(i+1);}
    require(*stable==42 && &values[2000]==stable,12);
    require(values.end()-values.begin()==4001,13);
    std::deque<int>::iterator middle=values.begin()+2000;
    std::deque<int>::const_iterator constant=middle;
    require(*constant==42 && constant-middle==0 && middle==constant,16);
    require(middle[-1]==-1 && middle[1]==1,17);
    middle-=129;middle+=129;
    require(&*middle==stable && *(129+(middle-129))==42,19);
    require(values.rbegin()[0]==2000 && values.rend()-values.rbegin()==4001,20);
    for(int i=0;i<2000;++i){values.pop_front();values.pop_back();}
    require(values.size()==1 && &values.front()==stable,22);
    std::deque<int> other;
    other.push_back(9);
    std::deque<int>::iterator saved=values.begin();
    values.swap(other);
    require(*saved==42 && &other.front()==stable && values.front()==9,27);
    std::deque<int> moved(std::move(other));
    require(&moved.front()==stable,29);
    other.push_front(8);
    require(other.front()==8 && moved.front()==42,31);
    moved.shrink_to_fit();require(&moved.front()==stable,32);
    moved.clear();moved.shrink_to_fit();moved.push_back(7);
    require(moved.front()==7 && moved.end()-moved.begin()==1,34);
    printf("deque segments ok\n");
}
