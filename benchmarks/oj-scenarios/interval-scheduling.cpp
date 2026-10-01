// Complete OJ program. The Java registry supplies identical full input to all four builds.
// Output encodes every result in deterministic order, including its observation count.
#include <cstdio>
unsigned long long hash_value;
unsigned long long hash_length;
void begin_answer(){hash_value=14695981039346656037ULL;hash_length=0;}
void observe(long long value){hash_value=(hash_value^(unsigned long long)value)*1099511628211ULL;++hash_length;}
void answer(int round){std::printf("round=%d observations=%llu hash=%llu\n",round,hash_length,(hash_value^hash_length)*1099511628211ULL);}
#include <vector>
#include <set>
#include <algorithm>
struct Interval{int start;int finish;int id;};
int main(){int rounds;if(std::scanf("%d",&rounds)!=1||rounds<1||rounds>1024)return 2;
 for(int round=0;round<rounds;++round){int n,k;if(std::scanf("%d %d",&n,&k)!=2||n<1||n>16384||k<1||k>16)return 2;
  std::vector<Interval> jobs;for(int i=0;i<n;++i){int start,finish;if(std::scanf("%d %d",&start,&finish)!=2||start<0||finish<=start)return 2;jobs.push_back(Interval{start,finish,i});}
  std::sort(jobs.begin(),jobs.end(),[](const Interval&a,const Interval&b){if(a.finish!=b.finish)return a.finish<b.finish;if(a.start!=b.start)return a.start<b.start;return a.id<b.id;});
  std::multiset<int> releases;for(int i=0;i<k;++i)releases.insert(0);int accepted=0;begin_answer();
  for(const auto& job:jobs){auto free=releases.upper_bound(job.start);bool fits=free!=releases.begin();observe(job.id);observe(fits?1:0);if(fits){--free;releases.erase(free);releases.insert(job.finish);++accepted;}}
  observe(accepted);for(int time:releases)observe(time);answer(round);
 }return 0;}
