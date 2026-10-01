#include <stdio.h>
int main() {
    int a=0,b=0;
    int converted=scanf("%d,%i",&a,&b);
    int suffix=getchar();
    int literal=scanf("?");
    int next=fgetc(stdin);
    printf("%d %d %d %c %d %c\n",converted,a,b,suffix,literal,next);
    int eof=scanf("%d",&a);
    printf("%d\n",eof);
    return 0;
}
