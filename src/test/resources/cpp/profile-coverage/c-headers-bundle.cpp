// The installed MinGW 8.1 <bits/stdc++.h> imports a broken <filesystem> even for
// an otherwise empty translation unit. In that host-STL build only, expand the
// same algorithm-profile headers. Both builds of our library use its real bundle.
#if !defined(__MINIC__) && !defined(__MINIC_SELF_STL__) && defined(__MINGW32__) && __GNUC__ == 8 && __GNUC_MINOR__ == 1
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <type_traits>
#include <utility>
#include <functional>
#include <iterator>
#include <algorithm>
#include <vector>
#include <string>
#include <deque>
#include <queue>
#include <stack>
#include <set>
#include <map>
#include <bitset>
#include <iostream>
#include <iomanip>
#else
#include <bits/stdc++.h>
#endif
#include <cctype>
#include <climits>
#include <cfloat>
#include <cassert>
#include <cerrno>
#include <ctime>

static_assert(CHAR_BIT==8&&sizeof(short)==2&&sizeof(int)==4&&sizeof(long)==4&&sizeof(long long)==8,"Windows x64 LLP64");
static_assert(SHRT_MAX==32767&&INT_MAX==2147483647&&LONG_MAX==2147483647L&&ULLONG_MAX==18446744073709551615ULL,"integer limits");
static_assert(FLT_RADIX==2&&FLT_MANT_DIG==24&&DBL_MANT_DIG==53&&FLT_MAX_EXP==128&&DBL_MAX_EXP==1024,"binary32 and binary64");
static_assert(sizeof(std::clock_t)==4&&sizeof(std::time_t)==8,"target clock and time models");
int main(){
    int evaluated=0;assert(++evaluated==1);if(evaluated!=1)return 1;
    assert(std::isalpha('A')!=0&&std::isalpha('7')==0&&std::isdigit('7')!=0);
    assert(std::isalnum('7')&&std::isblank(' ')&&std::iscntrl('\n')&&std::isgraph('!'));
    assert(std::islower('a')&&std::isupper('Z')&&std::isprint(' ')&&std::ispunct('!'));
    assert(std::isspace('\t')&&std::isxdigit('f')&&!std::isxdigit('g'));
    assert(std::tolower('A')=='a'&&std::toupper('z')=='Z'&&std::tolower(EOF)==EOF);
    assert(FLT_MIN>0.0f&&FLT_MAX>1.0e30f&&FLT_EPSILON>0.0f&&DBL_MIN>0.0&&DBL_EPSILON<FLT_EPSILON);
    errno=0;char* end=nullptr;
    long overflow=std::strtol("999999999999999999999999999999",&end,10);
    assert(overflow==LONG_MAX&&errno==ERANGE&&*end==0);
    errno=EDOM;assert(errno==EDOM);errno=0;
    std::tm date={};date.tm_year=124;date.tm_mon=1;date.tm_mday=29;
    date.tm_hour=3;date.tm_min=4;date.tm_sec=5;date.tm_wday=4;date.tm_yday=59;date.tm_isdst=0;
    char formatted[32];std::size_t written=std::strftime(formatted,sizeof(formatted),"%Y-%m-%d %H:%M:%S",&date);
    assert(written==19&&std::strcmp(formatted,"2024-02-29 03:04:05")==0);
    assert(std::difftime((std::time_t)100,(std::time_t)60)==40.0);
    std::pair<int,int> pair=std::make_pair(2,3);assert(pair.first+pair.second==5);
    std::printf("C headers and bundle ok\n");return 0;
}
