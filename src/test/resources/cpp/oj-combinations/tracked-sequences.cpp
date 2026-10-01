#include <vector>
#include <deque>
#include <utility>
#include <cstdio>
int alive=0,errors=0;
struct Record {
 int value;const Record* self;
 Record(int v=0):value(v),self(this){++alive;}
 Record(const Record& other):value(other.value),self(this){if(other.self!=&other)++errors;++alive;}
 Record(Record&& other) noexcept:value(other.value),self(this){if(other.self!=&other)++errors;other.value=-999;++alive;}
 Record& operator=(const Record& other){if(self!=this||other.self!=&other)++errors;value=other.value;return *this;}
 Record& operator=(Record&& other) noexcept{if(self!=this||other.self!=&other)++errors;value=other.value;other.value=-999;return *this;}
 ~Record(){if(self!=this)++errors;--alive;}
};
long long mix(long long hash,int value){return (hash*131+value+1000)%1000000007;}
int main(){int cases;std::scanf("%d",&cases);while(cases--){int steps;std::scanf("%d",&steps);long long hash=0;
 {std::vector<Record> v;std::deque<Record> d;
  for(int step=0;step<steps;++step){int op,index,other,value;std::scanf("%d%d%d%d",&op,&index,&other,&value);
   if(op==0){v.emplace_back(value);d.emplace_back(value);}
   else if(op==1){v.insert(v.begin()+index,v[other]);d.insert(d.begin()+index,d[other]);}
   else if(op==2){v.erase(v.begin()+index);d.erase(d.begin()+index);}
   else if(op==3){v.resize(index,Record(value));d.resize(index,Record(value));}
   else if(op==4){v[index]=Record(value);d[index]=Record(value);}
   else if(op==5){Record* vp=&v[0];Record* dp=&d[0];int old=d[0].value;v.reserve(v.capacity());d.emplace_front(value);d.pop_front();if(vp!=&v[0]||dp!=&d[0]||dp->value!=old)++errors;}
   else if(op==6){std::vector<Record> copy(v);std::deque<Record> dc(d);std::vector<Record> moved(std::move(copy));copy=moved;v.swap(copy);d=dc;}
   else if(op==7){v.pop_back();d.pop_back();}
   else {v.clear();d.clear();}
   if(v.size()!=d.size())++errors;hash=mix(hash,(int)v.size());
   for(unsigned long long i=0;i<v.size();++i){if(v[i].self!=&v[i]||d[i].self!=&d[i]||v[i].value!=d[i].value)++errors;hash=mix(hash,v[i].value);}
  }
 }
 std::printf("%lld %d %d\n",hash,alive,errors);}}
