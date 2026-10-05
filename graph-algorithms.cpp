#include <vector>
#include <queue>
#include <stack>
#include <deque>
#include <algorithm>
#include <utility>

// ============================================================
//  常量与基础结构
// ============================================================
const long long INF = (long long)1 << 60;

struct Edge {
    int to;
    long long w;
};

struct RawEdge {
    int u;
    int v;
    long long w;
};

struct UEdge {
    int u;
    int v;
    long long w;
};

struct FlowEdge {
    int to;
    int rev;
    long long cap;
};

struct BellmanResult {
    std::vector<long long> dist;
    bool hasNegativeCycle;
};

// ============================================================
//  一、建图
// ============================================================
std::vector<std::vector<Edge> > buildDirectedGraph(int n, const std::vector<RawEdge>& es) {
    std::vector<std::vector<Edge> > g;
    g.resize(n);
    for (std::size_t i = 0; i < es.size(); ++i) {
        Edge e;
        e.to = es[i].v;
        e.w  = es[i].w;
        g[es[i].u].push_back(e);
    }
    return g;
}

std::vector<std::vector<Edge> > buildUndirectedGraph(int n, const std::vector<RawEdge>& es) {
    std::vector<std::vector<Edge> > g;
    g.resize(n);
    for (std::size_t i = 0; i < es.size(); ++i) {
        Edge e1;
        e1.to = es[i].v;
        e1.w  = es[i].w;
        Edge e2;
        e2.to = es[i].u;
        e2.w  = es[i].w;
        g[es[i].u].push_back(e1);
        g[es[i].v].push_back(e2);
    }
    return g;
}

// ============================================================
//  二、BFS
// ============================================================
std::vector<int> bfsShortestPath(const std::vector<std::vector<Edge> >& g, int src) {
    int n = (int)g.size();
    std::vector<int> dist;
    dist.assign(n, -1);
    std::queue<int> q;
    dist[src] = 0;
    q.push(src);
    while (!q.empty()) {
        int u = q.front(); q.pop();
        for (std::size_t i = 0; i < g[u].size(); ++i) {
            int v = g[u][i].to;
            if (dist[v] == -1) {
                dist[v] = dist[u] + 1;
                q.push(v);
            }
        }
    }
    return dist;
}

std::vector<int> bfsWithParent(const std::vector<std::vector<Edge> >& g,
                               int src, std::vector<int>& parent) {
    int n = (int)g.size();
    std::vector<int> dist;
    dist.assign(n, -1);
    parent.assign(n, -1);
    std::queue<int> q;
    dist[src] = 0;
    q.push(src);
    while (!q.empty()) {
        int u = q.front(); q.pop();
        for (std::size_t i = 0; i < g[u].size(); ++i) {
            int v = g[u][i].to;
            if (dist[v] == -1) {
                dist[v] = dist[u] + 1;
                parent[v] = u;
                q.push(v);
            }
        }
    }
    return dist;
}

std::vector<int> reconstructPath(const std::vector<int>& parent, int src, int dst) {
    std::vector<int> path;
    int cur = dst;
    while (cur != -1) {
        path.push_back(cur);
        if (cur == src) break;
        cur = parent[cur];
    }
    if (path.empty() || path.back() != src) return std::vector<int>();
    for (std::size_t i = 0; i < path.size() / 2; ++i) {
        int t = path[i];
        path[i] = path[path.size() - 1 - i];
        path[path.size() - 1 - i] = t;
    }
    return path;
}

int countConnectedComponents(const std::vector<std::vector<Edge> >& g) {
    int n = (int)g.size();
    std::vector<int> visited;
    visited.assign(n, 0);
    int comps = 0;
    for (int s = 0; s < n; ++s) {
        if (visited[s]) continue;
        comps++;
        std::queue<int> q;
        q.push(s);
        visited[s] = 1;
        while (!q.empty()) {
            int u = q.front(); q.pop();
            for (std::size_t i = 0; i < g[u].size(); ++i) {
                int v = g[u][i].to;
                if (!visited[v]) {
                    visited[v] = 1;
                    q.push(v);
                }
            }
        }
    }
    return comps;
}

// ============================================================
//  三、DFS 与环检测
// ============================================================
void dfsRecursive(const std::vector<std::vector<Edge> >& g, int u,
                  std::vector<int>& visited, std::vector<int>& order) {
    visited[u] = 1;
    order.push_back(u);
    for (std::size_t i = 0; i < g[u].size(); ++i) {
        int v = g[u][i].to;
        if (!visited[v]) dfsRecursive(g, v, visited, order);
    }
}

std::vector<int> dfsOrderRecursive(const std::vector<std::vector<Edge> >& g, int src) {
    int n = (int)g.size();
    std::vector<int> visited;
    visited.assign(n, 0);
    std::vector<int> order;
    dfsRecursive(g, src, visited, order);
    return order;
}

std::vector<int> dfsOrderIterative(const std::vector<std::vector<Edge> >& g, int src) {
    int n = (int)g.size();
    std::vector<int> visited;
    visited.assign(n, 0);
    std::vector<int> order;
    std::stack<int> st;
    st.push(src);
    while (!st.empty()) {
        int u = st.top(); st.pop();
        if (visited[u]) continue;
        visited[u] = 1;
        order.push_back(u);
        for (int i = (int)g[u].size() - 1; i >= 0; --i) {
            int v = g[u][i].to;
            if (!visited[v]) st.push(v);
        }
    }
    return order;
}

bool hasCycleDirectedDFS(const std::vector<std::vector<Edge> >& g) {
    int n = (int)g.size();
    std::vector<int> color;
    color.assign(n, 0);
    for (int s = 0; s < n; ++s) {
        if (color[s] != 0) continue;
        std::stack<std::pair<int,int> > st;
        st.push(std::make_pair(s, 0));
        color[s] = 1;
        while (!st.empty()) {
            std::pair<int,int>& top = st.top();
            int u = top.first;
            int& idx = top.second;
            if (idx >= (int)g[u].size()) {
                color[u] = 2;
                st.pop();
                continue;
            }
            int v = g[u][idx].to;
            idx++;
            if (color[v] == 1) return true;
            if (color[v] == 0) {
                color[v] = 1;
                st.push(std::make_pair(v, 0));
            }
        }
    }
    return false;
}

struct DSU {
    std::vector<int> parent;
    std::vector<int> rk;

    DSU(int n) {
        parent.resize(n);
        rk.resize(n);
        for (int i = 0; i < n; ++i) {
            parent[i] = i;
            rk[i] = 0;
        }
    }

    int find(int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    bool unite(int a, int b) {
        a = find(a);
        b = find(b);
        if (a == b) return false;
        if (rk[a] < rk[b]) { int t = a; a = b; b = t; }
        parent[b] = a;
        if (rk[a] == rk[b]) rk[a]++;
        return true;
    }

    bool same(int a, int b) { return find(a) == find(b); }
};

bool hasCycleUndirected(const std::vector<UEdge>& edges, int n) {
    DSU dsu(n);
    for (std::size_t i = 0; i < edges.size(); ++i) {
        if (!dsu.unite(edges[i].u, edges[i].v)) return true;
    }
    return false;
}

// ============================================================
//  四、拓扑排序
// ============================================================
std::vector<int> topologicalSortKahn(const std::vector<std::vector<Edge> >& g) {
    int n = (int)g.size();
    std::vector<int> indeg;
    indeg.assign(n, 0);
    for (int u = 0; u < n; ++u)
        for (std::size_t i = 0; i < g[u].size(); ++i)
            indeg[g[u][i].to]++;

    std::queue<int> q;
    for (int i = 0; i < n; ++i)
        if (indeg[i] == 0) q.push(i);

    std::vector<int> order;
    while (!q.empty()) {
        int u = q.front(); q.pop();
        order.push_back(u);
        for (std::size_t i = 0; i < g[u].size(); ++i) {
            int v = g[u][i].to;
            indeg[v]--;
            if (indeg[v] == 0) q.push(v);
        }
    }
    if ((int)order.size() != n) return std::vector<int>();
    return order;
}

std::vector<int> topologicalSortDFS(const std::vector<std::vector<Edge> >& g) {
    int n = (int)g.size();
    std::vector<int> color;
    color.assign(n, 0);
    std::vector<int> order;
    bool cycle = false;
    for (int s = 0; s < n; ++s) {
        if (color[s] != 0 || cycle) continue;
        std::stack<std::pair<int,int> > st;
        st.push(std::make_pair(s, 0));
        color[s] = 1;
        while (!st.empty()) {
            std::pair<int,int>& top = st.top();
            int u = top.first;
            int& idx = top.second;
            if (idx >= (int)g[u].size()) {
                color[u] = 2;
                order.push_back(u);
                st.pop();
                continue;
            }
            int v = g[u][idx].to;
            idx++;
            if (color[v] == 1) { cycle = true; break; }
            if (color[v] == 0) {
                color[v] = 1;
                st.push(std::make_pair(v, 0));
            }
        }
    }
    if (cycle) return std::vector<int>();
    for (std::size_t i = 0; i < order.size() / 2; ++i) {
        int t = order[i];
        order[i] = order[order.size() - 1 - i];
        order[order.size() - 1 - i] = t;
    }
    return order;
}

// ============================================================
//  五、Dijkstra（O(n^2) 朴素版）
// ============================================================
std::vector<long long> dijkstra(const std::vector<std::vector<Edge> >& g, int src) {
    int n = (int)g.size();
    std::vector<long long> dist;
    dist.assign(n, INF);
    std::vector<int> done;
    done.assign(n, 0);
    dist[src] = 0;
    for (int it = 0; it < n; ++it) {
        int best = -1;
        for (int u = 0; u < n; ++u) {
            if (done[u]) continue;
            if (best == -1 || dist[u] < dist[best]) best = u;
        }
        if (best == -1 || dist[best] == INF) break;
        done[best] = 1;
        for (std::size_t i = 0; i < g[best].size(); ++i) {
            int v = g[best][i].to;
            long long w = g[best][i].w;
            if (dist[best] + w < dist[v]) dist[v] = dist[best] + w;
        }
    }
    return dist;
}

std::vector<long long> dijkstraWithParent(const std::vector<std::vector<Edge> >& g,
                                          int src, std::vector<int>& parent) {
    int n = (int)g.size();
    std::vector<long long> dist;
    dist.assign(n, INF);
    parent.assign(n, -1);
    std::vector<int> done;
    done.assign(n, 0);
    dist[src] = 0;
    for (int it = 0; it < n; ++it) {
        int best = -1;
        for (int u = 0; u < n; ++u) {
            if (done[u]) continue;
            if (best == -1 || dist[u] < dist[best]) best = u;
        }
        if (best == -1 || dist[best] == INF) break;
        done[best] = 1;
        for (std::size_t i = 0; i < g[best].size(); ++i) {
            int v = g[best][i].to;
            long long w = g[best][i].w;
            if (dist[best] + w < dist[v]) {
                dist[v] = dist[best] + w;
                parent[v] = best;
            }
        }
    }
    return dist;
}

// ============================================================
//  六、Bellman-Ford
// ============================================================
BellmanResult bellmanFord(int n, const std::vector<RawEdge>& edges, int src) {
    std::vector<long long> dist;
    dist.assign(n, INF);
    dist[src] = 0;
    bool negCycle = false;
    for (int it = 0; it < n; ++it) {
        bool updated = false;
        for (std::size_t j = 0; j < edges.size(); ++j) {
            int u = edges[j].u;
            int v = edges[j].v;
            long long w = edges[j].w;
            if (dist[u] != INF && dist[u] + w < dist[v]) {
                dist[v] = dist[u] + w;
                updated = true;
                if (it == n - 1) negCycle = true;
            }
        }
        if (!updated) break;
    }
    BellmanResult r;
    r.dist = dist;
    r.hasNegativeCycle = negCycle;
    return r;
}

// ============================================================
//  七、Floyd-Warshall
// ============================================================
std::vector<std::vector<long long> > floydWarshall(int n,
                                                   const std::vector<RawEdge>& edges,
                                                   bool directed) {
    std::vector<std::vector<long long> > d;
    d.resize(n);
    for (int i = 0; i < n; ++i) {
        d[i].assign(n, INF);
        d[i][i] = 0;
    }
    for (std::size_t i = 0; i < edges.size(); ++i) {
        int u = edges[i].u;
        int v = edges[i].v;
        long long w = edges[i].w;
        if (w < d[u][v]) d[u][v] = w;
        if (!directed && w < d[v][u]) d[v][u] = w;
    }
    for (int k = 0; k < n; ++k)
        for (int i = 0; i < n; ++i) {
            if (d[i][k] == INF) continue;
            for (int j = 0; j < n; ++j) {
                if (d[k][j] == INF) continue;
                if (d[i][k] + d[k][j] < d[i][j])
                    d[i][j] = d[i][k] + d[k][j];
            }
        }
    return d;
}

// ============================================================
//  八、最小生成树（返回 long long，连通性通过 bool* 传出）
// ============================================================
long long kruskalMST(int n, std::vector<UEdge> edgesIn, bool* connected) {
    std::vector<UEdge> edges;
    for (std::size_t i = 0; i < edgesIn.size(); ++i) edges.push_back(edgesIn[i]);

    // 手写冒泡排序（按 w 升序）
    for (std::size_t i = 0; i + 1 < edges.size(); ++i) {
        for (std::size_t j = 0; j + 1 < edges.size() - i; ++j) {
            if (edges[j + 1].w < edges[j].w) {
                UEdge tmp = edges[j];
                edges[j] = edges[j + 1];
                edges[j + 1] = tmp;
            }
        }
    }

    DSU dsu(n);
    long long total = 0;
    int used = 0;
    for (std::size_t i = 0; i < edges.size(); ++i) {
        if (dsu.unite(edges[i].u, edges[i].v)) {
            total += edges[i].w;
            used++;
            if (used == n - 1) break;
        }
    }
    *connected = (used == n - 1);
    return total;
}

long long primMST(const std::vector<std::vector<Edge> >& g, bool* connected) {
    int n = (int)g.size();
    if (n == 0) { *connected = true; return 0; }

    std::vector<int> inMST;
    inMST.assign(n, 0);
    std::vector<long long> minW;
    minW.assign(n, INF);
    minW[0] = 0;

    long long total = 0;
    int cnt = 0;

    for (int it = 0; it < n; ++it) {
        int best = -1;
        for (int u = 0; u < n; ++u) {
            if (inMST[u]) continue;
            if (best == -1 || minW[u] < minW[best]) best = u;
        }
        if (best == -1 || minW[best] == INF) break;
        inMST[best] = 1;
        total += minW[best];
        cnt++;
        for (std::size_t i = 0; i < g[best].size(); ++i) {
            int v = g[best][i].to;
            long long ew = g[best][i].w;
            if (!inMST[v] && ew < minW[v]) minW[v] = ew;
        }
    }
    *connected = (cnt == n);
    return total;
}

// ============================================================
//  九、Tarjan 强连通分量
// ============================================================
struct TarjanSCC {
    int n;
    std::vector<std::vector<Edge> >* g;
    std::vector<int> dfn;
    std::vector<int> low;
    std::vector<int> stk;
    std::vector<int> comp;
    std::vector<int> inStack;
    int timer;
    int compCnt;

    TarjanSCC(int n_) {
        n = n_;
        g = 0;
        dfn.resize(n);
        low.resize(n);
        comp.resize(n);
        inStack.resize(n);
        for (int i = 0; i < n; ++i) {
            dfn[i] = -1;
            low[i] = 0;
            comp[i] = -1;
            inStack[i] = 0;
        }
        timer = 0;
        compCnt = 0;
    }

    void dfs(int u) {
        dfn[u] = low[u] = timer++;
        stk.push_back(u);
        inStack[u] = 1;
        for (std::size_t i = 0; i < (*g)[u].size(); ++i) {
            int v = (*g)[u][i].to;
            if (dfn[v] == -1) {
                dfs(v);
                if (low[v] < low[u]) low[u] = low[v];
            } else if (inStack[v]) {
                if (dfn[v] < low[u]) low[u] = dfn[v];
            }
        }
        if (low[u] == dfn[u]) {
            while (1) {
                int x = stk.back(); stk.pop_back();
                inStack[x] = 0;
                comp[x] = compCnt;
                if (x == u) break;
            }
            compCnt++;
        }
    }

    std::vector<int> run(const std::vector<std::vector<Edge> >& gr) {
        g = (std::vector<std::vector<Edge> >*)&gr;
        for (int i = 0; i < n; ++i)
            if (dfn[i] == -1) dfs(i);
        return comp;
    }
};

// ============================================================
//  十、Tarjan 割点与桥
// ============================================================
struct TarjanCut {
    int n;
    std::vector<std::vector<Edge> >* g;
    std::vector<int> dfn;
    std::vector<int> low;
    std::vector<int> isCut;
    std::vector<std::pair<int,int> > bridges;
    int timer;

    TarjanCut(int n_) {
        n = n_;
        g = 0;
        dfn.resize(n);
        low.resize(n);
        isCut.resize(n);
        for (int i = 0; i < n; ++i) {
            dfn[i] = -1;
            low[i] = 0;
            isCut[i] = 0;
        }
        timer = 0;
    }

    void dfs(int u, int parent) {
        dfn[u] = low[u] = timer++;
        int children = 0;
        for (std::size_t i = 0; i < (*g)[u].size(); ++i) {
            int v = (*g)[u][i].to;
            if (v == parent) continue;
            if (dfn[v] == -1) {
                children++;
                dfs(v, u);
                if (low[v] < low[u]) low[u] = low[v];
                if (parent != -1 && low[v] >= dfn[u]) isCut[u] = 1;
                if (low[v] > dfn[u]) bridges.push_back(std::make_pair(u, v));
            } else {
                if (dfn[v] < low[u]) low[u] = dfn[v];
            }
        }
        if (parent == -1 && children > 1) isCut[u] = 1;
    }

    void run(const std::vector<std::vector<Edge> >& gr) {
        g = (std::vector<std::vector<Edge> >*)&gr;
        for (int i = 0; i < n; ++i)
            if (dfn[i] == -1) dfs(i, -1);
    }
};

// ============================================================
//  十一、二分图与匈牙利
// ============================================================
bool isBipartite(const std::vector<std::vector<Edge> >& g, std::vector<int>& color) {
    int n = (int)g.size();
    color.assign(n, -1);
    std::queue<int> q;
    for (int s = 0; s < n; ++s) {
        if (color[s] != -1) continue;
        color[s] = 0;
        q.push(s);
        while (!q.empty()) {
            int u = q.front(); q.pop();
            for (std::size_t i = 0; i < g[u].size(); ++i) {
                int v = g[u][i].to;
                if (color[v] == -1) {
                    color[v] = color[u] ^ 1;
                    q.push(v);
                } else if (color[v] == color[u]) {
                    return false;
                }
            }
        }
    }
    return true;
}

struct Hungarian {
    int L;
    int R;
    std::vector<std::vector<int> > adj;
    std::vector<int> matchR;
    std::vector<int> matchL;
    std::vector<int> used;

    Hungarian(int l, int r) {
        L = l;
        R = r;
        adj.resize(l);
        matchR.assign(r, -1);
        matchL.assign(l, -1);
        used.assign(r, 0);
    }

    void addEdge(int u, int v) { adj[u].push_back(v); }

    bool tryKuhn(int u) {
        for (std::size_t i = 0; i < adj[u].size(); ++i) {
            int v = adj[u][i];
            if (used[v]) continue;
            used[v] = 1;
            if (matchR[v] == -1 || tryKuhn(matchR[v])) {
                matchR[v] = u;
                matchL[u] = v;
                return true;
            }
        }
        return false;
    }

    int maxMatching() {
        int res = 0;
        for (int u = 0; u < L; ++u) {
            for (int i = 0; i < R; ++i) used[i] = 0;
            if (tryKuhn(u)) res++;
        }
        return res;
    }
};

// ============================================================
//  十二、Dinic 最大流
// ============================================================
struct Dinic {
    int n;
    std::vector<std::vector<FlowEdge> > g;
    std::vector<int> level;
    std::vector<int> iter;

    Dinic(int n_) {
        n = n_;
        g.resize(n);
        level.resize(n);
        iter.resize(n);
    }

    void addEdge(int from, int to, long long cap) {
        FlowEdge e1;
        e1.to = to;
        e1.rev = (int)g[to].size();
        e1.cap = cap;
        g[from].push_back(e1);

        FlowEdge e2;
        e2.to = from;
        e2.rev = (int)g[from].size() - 1;
        e2.cap = 0;
        g[to].push_back(e2);
    }

    bool bfs(int s, int t) {
        for (int i = 0; i < n; ++i) level[i] = -1;
        std::queue<int> q;
        level[s] = 0;
        q.push(s);
        while (!q.empty()) {
            int u = q.front(); q.pop();
            for (std::size_t i = 0; i < g[u].size(); ++i) {
                FlowEdge& e = g[u][i];
                if (e.cap > 0 && level[e.to] < 0) {
                    level[e.to] = level[u] + 1;
                    q.push(e.to);
                }
            }
        }
        return level[t] >= 0;
    }

    long long dfs(int u, int t, long long f) {
        if (u == t) return f;
        for (int& i = iter[u]; i < (int)g[u].size(); ++i) {
            FlowEdge& e = g[u][i];
            if (e.cap > 0 && level[u] < level[e.to]) {
                long long cap2 = f < e.cap ? f : e.cap;
                long long d = dfs(e.to, t, cap2);
                if (d > 0) {
                    e.cap -= d;
                    g[e.to][e.rev].cap += d;
                    return d;
                }
            }
        }
        return 0;
    }

    long long maxFlow(int s, int t) {
        long long flow = 0;
        while (bfs(s, t)) {
            for (int i = 0; i < n; ++i) iter[i] = 0;
            while (1) {
                long long f = dfs(s, t, INF);
                if (f <= 0) break;
                flow += f;
            }
        }
        return flow;
    }
};

// ============================================================
//  十三、LCA 倍增
// ============================================================
struct LCA {
    int n;
    int LOG;
    std::vector<std::vector<int> > up;
    std::vector<int> depth;

    LCA(int n_) {
        n = n_;
        LOG = 1;
        while ((1 << LOG) <= n) LOG++;
        up.resize(LOG);
        for (int k = 0; k < LOG; ++k) up[k].assign(n, -1);
        depth.assign(n, 0);
    }

    void build(const std::vector<std::vector<Edge> >& g, int root) {
        std::vector<int> parent;
        parent.assign(n, -1);
        std::vector<int> visited;
        visited.assign(n, 0);
        std::queue<int> q;
        q.push(root);
        visited[root] = 1;
        depth[root] = 0;
        while (!q.empty()) {
            int u = q.front(); q.pop();
            for (std::size_t i = 0; i < g[u].size(); ++i) {
                int v = g[u][i].to;
                if (!visited[v]) {
                    visited[v] = 1;
                    parent[v] = u;
                    depth[v] = depth[u] + 1;
                    q.push(v);
                }
            }
        }
        for (int i = 0; i < n; ++i) up[0][i] = parent[i];
        for (int k = 1; k < LOG; ++k)
            for (int i = 0; i < n; ++i) {
                if (up[k-1][i] == -1) up[k][i] = -1;
                else up[k][i] = up[k-1][up[k-1][i]];
            }
    }

    int lift(int u, int d) {
        for (int k = 0; k < LOG; ++k)
            if ((d >> k) & 1) u = up[k][u];
        return u;
    }

    int lca(int u, int v) {
        if (depth[u] < depth[v]) { int t = u; u = v; v = t; }
        u = lift(u, depth[u] - depth[v]);
        if (u == v) return u;
        for (int k = LOG - 1; k >= 0; --k)
            if (up[k][u] != up[k][v]) {
                u = up[k][u];
                v = up[k][v];
            }
        return up[0][u];
    }

    int distance(int u, int v) {
        return depth[u] + depth[v] - 2 * depth[lca(u, v)];
    }
};

// ============================================================
//  十四、0-1 BFS
// ============================================================
std::vector<long long> bfs01(const std::vector<std::vector<Edge> >& g, int src) {
    int n = (int)g.size();
    std::vector<long long> dist;
    dist.assign(n, INF);
    std::deque<int> dq;
    dist[src] = 0;
    dq.push_front(src);
    while (!dq.empty()) {
        int u = dq.front(); dq.pop_front();
        for (std::size_t i = 0; i < g[u].size(); ++i) {
            int v = g[u][i].to;
            long long w = g[u][i].w;
            if (dist[u] + w < dist[v]) {
                dist[v] = dist[u] + w;
                if (w == 0) dq.push_front(v);
                else        dq.push_back(v);
            }
        }
    }
    return dist;
}

// ============================================================
//  十五、Johnson 全源最短路
// ============================================================
std::vector<std::vector<long long> > johnson(int n, const std::vector<RawEdge>& edges) {
    std::vector<RawEdge> aug;
    for (std::size_t i = 0; i < edges.size(); ++i) aug.push_back(edges[i]);
    for (int i = 0; i < n; ++i) {
        RawEdge e;
        e.u = n; e.v = i; e.w = 0;
        aug.push_back(e);
    }

    BellmanResult bf = bellmanFord(n + 1, aug, n);
    std::vector<std::vector<long long> > empty;
    if (bf.hasNegativeCycle) return empty;

    std::vector<std::vector<Edge> > g;
    g.resize(n);
    for (std::size_t i = 0; i < edges.size(); ++i) {
        int u = edges[i].u;
        int v = edges[i].v;
        long long w = edges[i].w + bf.dist[u] - bf.dist[v];
        Edge e;
        e.to = v;
        e.w = w;
        g[u].push_back(e);
    }

    std::vector<std::vector<long long> > result;
    result.resize(n);
    for (int s = 0; s < n; ++s) {
        std::vector<long long> d = dijkstra(g, s);
        result[s].assign(n, INF);
        for (int t = 0; t < n; ++t) {
            if (d[t] < INF) result[s][t] = d[t] - bf.dist[s] + bf.dist[t];
        }
    }
    return result;
}

// ============================================================
//  示例主函数（无输入输出）
// ============================================================
int main() {
    int n = 6;

    // -------- 有向边集合（RawEdge）--------
    std::vector<RawEdge> edges;
    {
        RawEdge e;
        e.u = 0; e.v = 1; e.w = 4; edges.push_back(e);
        e.u = 0; e.v = 2; e.w = 1; edges.push_back(e);
        e.u = 2; e.v = 1; e.w = 2; edges.push_back(e);
        e.u = 1; e.v = 3; e.w = 1; edges.push_back(e);
        e.u = 2; e.v = 3; e.w = 5; edges.push_back(e);
        e.u = 3; e.v = 4; e.w = 3; edges.push_back(e);
        e.u = 4; e.v = 5; e.w = 2; edges.push_back(e);
        e.u = 2; e.v = 4; e.w = 8; edges.push_back(e);
    }

    // -------- 无向边集合（UEdge），专供 Kruskal --------
    std::vector<UEdge> uedges;
    {
        UEdge e;
        e.u = 0; e.v = 1; e.w = 4; uedges.push_back(e);
        e.u = 0; e.v = 2; e.w = 1; uedges.push_back(e);
        e.u = 2; e.v = 1; e.w = 2; uedges.push_back(e);
        e.u = 1; e.v = 3; e.w = 1; uedges.push_back(e);
        e.u = 3; e.v = 4; e.w = 3; uedges.push_back(e);
        e.u = 4; e.v = 5; e.w = 2; uedges.push_back(e);
    }

    // -------- 建图 --------
    std::vector<std::vector<Edge> > g  = buildDirectedGraph(n, edges);
    std::vector<std::vector<Edge> > ug = buildUndirectedGraph(n, edges);

    // -------- 各类算法调用 --------
    std::vector<int> dist   = bfsShortestPath(g, 0);
    std::vector<int> order  = dfsOrderRecursive(g, 0);
    std::vector<int> topo   = topologicalSortKahn(g);
    std::vector<long long> dk = dijkstra(g, 0);
    std::vector<std::vector<long long> > fw = floydWarshall(n, edges, true);
    BellmanResult bf = bellmanFord(n, edges, 0);

    bool mstConn = false;
    long long mstTotal = kruskalMST(n, uedges, &mstConn);

    bool primConn = false;
    long long primTotal = primMST(ug, &primConn);

    TarjanSCC scc(n);
    std::vector<int> comp = scc.run(g);

    TarjanCut cut(n);
    cut.run(ug);

    std::vector<int> color;
    bool bip = isBipartite(ug, color);

    Hungarian hun(3, 3);
    hun.addEdge(0, 0); hun.addEdge(0, 1);
    hun.addEdge(1, 1); hun.addEdge(2, 2);
    int mm = hun.maxMatching();

    Dinic dinic(n);
    dinic.addEdge(0, 1, 10);
    dinic.addEdge(1, 2, 5);
    dinic.addEdge(0, 2, 3);
    long long flow = dinic.maxFlow(0, 2);

    LCA lca((int)ug.size());
    lca.build(ug, 0);
    int d = lca.distance(3, 5);

    std::vector<long long> d01 = bfs01(g, 0);
    std::vector<std::vector<long long> > jh = johnson(n, edges);

    (void)dist; (void)order; (void)topo; (void)dk; (void)fw;
    (void)bf; (void)mstTotal; (void)mstConn;
    (void)primTotal; (void)primConn; (void)comp;
    (void)cut; (void)bip; (void)mm; (void)flow;
    (void)d; (void)d01; (void)jh;

    return 0;
}
