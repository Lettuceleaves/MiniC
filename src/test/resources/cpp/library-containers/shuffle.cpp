#include <algorithm>
#include <cstdio>
struct Engine{
    typedef unsigned int result_type;unsigned int state;
    Engine():state(1){}static result_type min(){return 0;}static result_type max(){return 32767;}
    result_type operator()(){state=state*214013u+2531011u;return (state>>16)&32767u;}
};
int main(){
    int values[300];for(int i=0;i<300;++i)values[i]=i;Engine engine;
    std::shuffle(values,values+300,engine);std::sort(values,values+300);
    int ok=1;for(int i=0;i<300;++i)if(values[i]!=i)ok=0;std::printf("%d\n",ok);
}
