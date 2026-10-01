#include <stdio.h>
union Storage{unsigned long long capacity;char small[16];};
int main(){for(int n=0;n<16;++n){Storage source;source.small[0]=0;for(int i=0;i<n;++i)source.small[i]=(char)('a'+i);source.small[n]=0;
 Storage target;target.small[0]=0;target=source;
 for(int i=0;i<=n;++i)if(target.small[i]!=source.small[i])return 1;
 }puts("ok");return 0;}
