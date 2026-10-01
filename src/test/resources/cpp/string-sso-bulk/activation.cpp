#include <string.h>
#include <stdio.h>
union Storage{unsigned long long capacity;char small[16];};
int main(){for(int n=0;n<16;++n){char source[16];for(int i=0;i<n;++i)source[i]=(char)(i%3==0?255:'a'+i);source[n]=0;
 Storage target;target.capacity=63;target.small[0]=source[0];if(n!=0)::memcpy(target.small+1,source+1,n);
 for(int i=0;i<=n;++i)if(target.small[i]!=source[i])return 1;
 target.capacity=127;target.small[0]=source[0];if(n!=0)::memcpy(target.small+1,source+1,n);
 for(int i=0;i<=n;++i)if(target.small[i]!=source[i])return 2;
 }puts("ok");return 0;}
