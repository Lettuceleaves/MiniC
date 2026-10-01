#include <iostream>
#include <iomanip>
#include <string>
#include <cstdio>
int main(){
    int a;std::string word,line;std::cin>>a>>word;std::getline(std::cin,line);
    std::cout<<a<<'|'<<word<<'|'<<line<<'\n';
    std::cout<<std::hex<<std::showbase<<255<<' '<<std::dec<<std::fixed<<std::setprecision(2)<<1.25<<'\n';
    std::cout<<std::setfill('_')<<std::setw(5)<<42<<' '<<std::boolalpha<<true<<'\n';
    char c=0;std::cin.get(c);std::cin.unget();std::cin.get(c);std::cout<<c<<'\n';
    std::cout.flush();std::printf("mixed\n");std::cout<<"done"<<std::endl;
}
