#include <iostream>
#include <iomanip>
#include <cstddef>
int main(){std::nullptr_t value=nullptr;std::cout<<std::setw(9)<<value<<'|'<<nullptr<<'\n';return std::cout.good()?0:1;}
