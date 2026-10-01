#include <map>
#include <vector>
#include <string>
#include <algorithm>
#include <utility>
#include <iostream>
int main(){int cases;std::cin>>cases;while(cases--){int n;std::cin>>n;std::map<std::string,int> counts;
 for(int i=0;i<n;++i){std::string word;std::cin>>word;++counts[word];}
 typedef std::pair<std::string,int> Word;std::vector<Word> ranking;for(const auto& entry:counts)ranking.emplace_back(entry.first,entry.second);
 std::sort(ranking.begin(),ranking.end(),[](const Word& a,const Word& b){return a.second!=b.second?a.second>b.second:a.first<b.first;});
 std::cout<<ranking.size();for(const Word& entry:ranking)std::cout<<' '<<entry.first<<':'<<entry.second;std::cout<<'\n';}}
