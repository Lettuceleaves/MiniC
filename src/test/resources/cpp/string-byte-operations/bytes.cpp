#include <string>
#include <stdio.h>
int sign(int n){return n<0?-1:n>0?1:0;}
int main(){
 char left[6]={'a','\0',(char)255,'z','x','\0'};
 char right[6]={'a','\0',(char)128,'z','x','\0'};
 std::string a(left,5),b(right,5);
 if(a.size()!=5||a[1]!=0||sign(a.compare(b))!=1||sign(b.compare(a))!=-1)return 1;
 if(a.compare(a)!=0||a.compare(0,2,b,0,2)!=0)return 2;
 if(sign(a.compare(0,2,b))!=-1||sign(b.compare(0,5,a,0,2))!=1)return 3;
 char output[8]={'q','q','q','q','q','q','q','q'};
 if(a.copy(output,3,1)!=3||output[0]!=0||(unsigned char)output[1]!=255||output[2]!='z'||output[3]!='q')return 4;
 if(a.copy((char*)0,0,5)!=0||a.compare(5,0,(const char*)0,0)!=0)return 5;
 std::string empty((const char*)0,0);empty.assign((const char*)0,0);empty.append((const char*)0,0);
 empty.insert(0,(const char*)0,0);empty.replace(0,0,(const char*)0,0);
 if(!empty.empty()||empty.data()[0]!=0)return 6;
 std::string terminated(left);if(terminated.size()!=1)return 7;
 char bytes[256];for(int i=0;i<256;++i)bytes[i]=(char)i;
 std::string all(bytes,256),copied(all);if(copied!=all||copied.size()!=256)return 8;
 char destination[256];all.copy(destination,256);for(int i=0;i<256;++i)if((unsigned char)destination[i]!=i)return 9;
 puts("ok");return 0;
}
