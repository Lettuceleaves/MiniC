#include <utility>
struct Explicit { explicit Explicit(int){} };
int main(){std::pair<Explicit,int> value={1,2};return 0;}
