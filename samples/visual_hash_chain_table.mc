// @visual root=buckets kind=hash-chain-table label=key
struct Entry {
    int key;
    struct Entry *next;
};

int main() {
    struct Entry *buckets[3];
    struct Entry e0;
    struct Entry e1;
    struct Entry e2;
    buckets[0] = &e0;
    buckets[1] = NULL;
    buckets[2] = &e2;
    e0.key = 10;
    e0.next = &e1;
    e1.key = 20;
    e1.next = NULL;
    e2.key = 30;
    e2.next = NULL;
    return e0.key + e1.key + e2.key; // @break
}
