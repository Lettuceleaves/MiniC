// @visual root=head kind=array
// @visual root=to kind=array
// @visual root=weight kind=array
// @visual root=dist kind=array
int minVertex(int *dist, int *used) {
    int best = -1;
    int i = 0;
    while (i < 6) {
        if (used[i] == 0) {
            if (best == -1) {
                best = i;
            } else {
                if (dist[i] < dist[best]) {
                    best = i;
                }
            }
        }
        i = i + 1;
    }
    return best;
}

int addEdge(int *head, int *to, int *weight, int *next, int *edgeCount, int u, int v, int w) {
    to[*edgeCount] = v;
    weight[*edgeCount] = w;
    next[*edgeCount] = head[u];
    head[u] = *edgeCount;
    *edgeCount = *edgeCount + 1;
    return 0;
}

int addUndirected(int *head, int *to, int *weight, int *next, int *edgeCount, int u, int v, int w) {
    addEdge(head, to, weight, next, edgeCount, u, v, w);
    addEdge(head, to, weight, next, edgeCount, v, u, w);
    return 0;
}

int dijkstra(int *head, int *to, int *weight, int *next, int *dist, int *used, int source) {
    int i = 0;
    while (i < 6) {
        dist[i] = 10000;
        used[i] = 0;
        i = i + 1;
    }
    dist[source] = 0;
    i = 0;
    while (i < 6) {
        int u = minVertex(dist, used);
        int e = 0;
        if (u == -1) {
            return 0;
        }
        used[u] = 1;
        e = head[u];
        while (e != -1) {
            int v = to[e];
            int nd = dist[u] + weight[e];
            if (nd < dist[v]) {
                dist[v] = nd;
            }
            e = next[e];
        }
        i = i + 1;
    }
    return 0;
}

int main() {
    int head[6];
    int to[32];
    int weight[32];
    int next[32];
    int dist[6];
    int used[6];
    int edgeCount = 0;
    int i = 0;
    while (i < 6) {
        head[i] = -1;
        i = i + 1;
    }
    addUndirected(head, to, weight, next, &edgeCount, 0, 1, 7);
    addUndirected(head, to, weight, next, &edgeCount, 0, 2, 9);
    addUndirected(head, to, weight, next, &edgeCount, 0, 5, 14);
    addUndirected(head, to, weight, next, &edgeCount, 1, 2, 10);
    addUndirected(head, to, weight, next, &edgeCount, 1, 3, 15);
    addUndirected(head, to, weight, next, &edgeCount, 2, 3, 11);
    addUndirected(head, to, weight, next, &edgeCount, 2, 5, 2);
    addUndirected(head, to, weight, next, &edgeCount, 3, 4, 6);
    addUndirected(head, to, weight, next, &edgeCount, 4, 5, 9);
    dijkstra(head, to, weight, next, dist, used, 0);
    return dist[1] + dist[2] + dist[3] + dist[4] + dist[5]; // @break
}
