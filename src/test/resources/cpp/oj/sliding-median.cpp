#include <iostream>
#include <vector>
#include <set>
#include <iterator>
int main(){
    int n,k;std::cin>>n>>k;std::vector<int> values(n);for(auto& x:values)std::cin>>x;
    std::multiset<int> window;
    for(int i=0;i<n;++i){
        window.insert(values[i]);
        if(i>=k)window.erase(window.find(values[i-k]));
        if(i>=k-1){auto middle=window.begin();std::advance(middle,(k-1)/2);std::cout<<(i==k-1?"":" ")<<*middle;}
    }
    std::cout<<'\n';return 0;
}
