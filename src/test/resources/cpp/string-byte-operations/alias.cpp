#include <string>
#include <stdio.h>
int check(const std::string& s,const char* e,int n){if(s.size()!=(unsigned long long)n||s.data()[n]!=0)return 0;for(int i=0;i<n;++i)if(s[i]!=e[i])return 0;return 1;}
void replace_expected(char* e,int* n,int p,int removed,const char* add,int count){char snapshot[512];for(int i=0;i<count;++i)snapshot[i]=add[i];if(count>removed){for(int i=*n-1;i>=p+removed;--i)e[i+count-removed]=e[i];}else{for(int i=p+removed;i<*n;++i)e[i-removed+count]=e[i];}for(int i=0;i<count;++i)e[p+i]=snapshot[i];*n=*n-removed+count;e[*n]=0;}
int main(){int lengths[7]={0,1,14,15,16,31,63};
 for(int k=0;k<7;++k){int n=lengths[k];char expected[512];for(int i=0;i<n;++i)expected[i]=(char)('a'+i%23);expected[n]=0;std::string s(expected,n);
 int added=n+1;s.append(s.data(),added);replace_expected(expected,&n,n,0,expected,added);if(!check(s,expected,n))return 1;
 int position=n/3;added=n/2;s.insert(position,s.data()+1,added);replace_expected(expected,&n,position,0,expected+1,added);if(!check(s,expected,n))return 2;
 int removed=n/4;added=n/2;s.replace(1,removed,s.data()+n/4,added);replace_expected(expected,&n,1,removed,expected+n/4,added);if(!check(s,expected,n))return 3;
 removed=n/2;added=n<2?n:2;s.replace(0,removed,s.data()+n-added,added);replace_expected(expected,&n,0,removed,expected+n-added,added);if(!check(s,expected,n))return 4;
 s.assign(s.data()+1,s.size()-1);replace_expected(expected,&n,0,n,expected+1,n-1);if(!check(s,expected,n))return 5;
 s=s;if(!check(s,expected,n))return 6;
 }
 puts("ok");return 0;}
