#include <stdio.h>
int main() {
    int first=fgetc(stdin);
    int pushed=ungetc(first,stdin);
    int number=0;
    int read=scanf("%d",&number);
    int delimiter=getchar();
    fputc('E',stderr);
    fputc('O',stdout);
    printf(" %d %d %d %d\n",first==pushed,read,number,delimiter);
    fflush(stdout);fflush(stderr);
    int c=fgetc(stdin);
    while(c!=EOF){fputc(c,stdout);c=fgetc(stdin);}
    return fflush(0);
}
