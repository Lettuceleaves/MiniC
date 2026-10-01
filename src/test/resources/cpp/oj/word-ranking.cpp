#include <iostream>
#include <string>
#include <map>
#include <vector>
#include <algorithm>
#include <utility>
int main(){
    int n;std::cin>>n;std::map<std::string,int> count;
    while(n--){std::string word;std::cin>>word;++count[word];}
    std::vector<std::pair<std::string,int>> words;
    for(const auto& entry:count)words.emplace_back(entry.first,entry.second);
    std::sort(words.begin(),words.end(),[](const auto& a,const auto& b){return a.second!=b.second?a.second>b.second:a.first<b.first;});
    for(const auto& [word,times]:words)std::cout<<word<<':'<<times<<'\n';
    return 0;
}
