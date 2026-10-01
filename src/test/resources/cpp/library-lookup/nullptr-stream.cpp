#include <iostream>
#include <iomanip>
#include <cstddef>
#if !defined(__MINIC__) && !defined(__MINIC_SELF_STL__) && defined(__MINGW32__) && defined(__GLIBCXX__) && __GLIBCXX__ == 20180502
// This host lacks the C++17 nullptr inserter. [ostream.inserters]/11 specifies
// insertion of an implementation-defined NTCTS; use the own library's spelling
// only for this host's formatting oracle. Own/MiniC still exercise nullptr_t.
// https://timsong-cpp.github.io/cppwp/n4659/ostream.inserters
int main(){std::cout<<std::setw(9)<<"nullptr"<<'|'<<"nullptr"<<'\n';return std::cout.good()?0:1;}
#else
int main(){std::nullptr_t value=nullptr;std::cout<<std::setw(9)<<value<<'|'<<nullptr<<'\n';return std::cout.good()?0:1;}
#endif
