#include <string>
#include <stdio.h>
int main(){std::string s(30,'a');s.reserve(160);s.replace(4,3,17,(char)255);if(s.size()!=44||s[3]!='a'||(unsigned char)s[4]!=255||(unsigned char)s[20]!=255||s[21]!='a')return 1;
 s.replace(5,27,2,0);if(s.size()!=19||s[5]!=0||s[6]!=0||s[7]!='a'||s.data()[19]!=0)return 2;
 s.erase(2,11);if(s.size()!=8||s[0]!='a'||s[1]!='a'||s[2]!='a'||s.data()[8]!=0)return 3;
 s.insert(0,16,'z');s.erase(20);if(s.size()!=20||s[15]!='z'||s[16]!='a'||s.data()[20]!=0)return 4;
 s.resize(35,'q');for(int i=20;i<35;++i)if(s[i]!='q')return 5;s.resize(15);s.shrink_to_fit();for(int i=0;i<15;++i)if(s[i]!='z')return 6;
 s.replace(0,s.size(),0,'x');if(!s.empty()||s.data()[0]!=0)return 7;s.assign(0,'x');s.append(0,'x');if(!s.empty())return 8;
 puts("ok");return 0;}
