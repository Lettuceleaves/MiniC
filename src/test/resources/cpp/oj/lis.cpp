#include <iostream>
#include <vector>
#include <algorithm>
int main(){
    int n;std::cin>>n;std::vector<int> tails;
    while(n--){int x;std::cin>>x;auto it=std::lower_bound(tails.begin(),tails.end(),x);if(it==tails.end())tails.push_back(x);else *it=x;}
    std::cout<<tails.size()<<'\n';return 0;
}
