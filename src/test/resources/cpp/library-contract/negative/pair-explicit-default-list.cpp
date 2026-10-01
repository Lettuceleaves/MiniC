#include <utility>
struct ExplicitDefault { explicit ExplicitDefault(){} };
int main(){std::pair<ExplicitDefault,int> value={};return 0;}
