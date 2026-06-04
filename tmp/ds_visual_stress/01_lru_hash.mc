// @visual root=buckets kind=hash-chain-table label=key next=hashNext
// @visual root=head kind=lru-list label=key
// @visual root=nodes kind=struct-array fields=key,value,prev,next,hashNext,alive
struct LruNode {
    int key;
    int value;
    int alive;
    struct LruNode *prev;
    struct LruNode *next;
    struct LruNode *hashNext;
};

int hashKey(int key) {
    return key % 5;
}

int clearBuckets(struct LruNode **buckets) {
    int i = 0;
    while (i < 5) {
        buckets[i] = NULL;
        i = i + 1;
    }
    return 0;
}

int initNode(struct LruNode *node, int key, int value) {
    node->key = key;
    node->value = value;
    node->alive = 1;
    node->prev = NULL;
    node->next = NULL;
    node->hashNext = NULL;
    return 0;
}

int hashInsert(struct LruNode **buckets, struct LruNode *node) {
    int bucket = hashKey(node->key);
    node->hashNext = buckets[bucket];
    buckets[bucket] = node;
    return 0;
}

int hashRemove(struct LruNode **buckets, struct LruNode *node) {
    int bucket = hashKey(node->key);
    struct LruNode *cur = buckets[bucket];
    struct LruNode *prev = NULL;
    while (cur != NULL) {
        if (cur == node) {
            if (prev == NULL) {
                buckets[bucket] = cur->hashNext;
            } else {
                prev->hashNext = cur->hashNext;
            }
            cur->hashNext = NULL;
            return 1;
        }
        prev = cur;
        cur = cur->hashNext;
    }
    return 0;
}

int hashFind(struct LruNode **buckets, int key, struct LruNode **out) {
    int bucket = hashKey(key);
    struct LruNode *cur = buckets[bucket];
    *out = NULL;
    while (cur != NULL) {
        if (cur->alive != 0) {
            if (cur->key == key) {
                *out = cur;
                return 1;
            }
        }
        cur = cur->hashNext;
    }
    return 0;
}

int detach(struct LruNode *node, struct LruNode **head, struct LruNode **tail) {
    if (node->prev != NULL) {
        node->prev->next = node->next;
    } else {
        *head = node->next;
    }
    if (node->next != NULL) {
        node->next->prev = node->prev;
    } else {
        *tail = node->prev;
    }
    node->prev = NULL;
    node->next = NULL;
    return 0;
}

int attachFront(struct LruNode *node, struct LruNode **head, struct LruNode **tail) {
    node->prev = NULL;
    node->next = *head;
    if (*head != NULL) {
        (*head)->prev = node;
    }
    *head = node;
    if (*tail == NULL) {
        *tail = node;
    }
    return 0;
}

int touch(struct LruNode *node, struct LruNode **head, struct LruNode **tail) {
    if (node != *head) {
        detach(node, head, tail);
        attachFront(node, head, tail);
    }
    return 0;
}

int put(struct LruNode **nodes, int *used, int capacity, struct LruNode **buckets, struct LruNode **head, struct LruNode **tail, int key, int value) {
    struct LruNode *found = NULL;
    hashFind(buckets, key, &found);
    if (found != NULL) {
        found->value = value;
        touch(found, head, tail);
        return 0;
    }
    if (*used < capacity) {
        struct LruNode *node = nodes[*used];
        *used = *used + 1;
        initNode(node, key, value);
        hashInsert(buckets, node);
        attachFront(node, head, tail);
        return 0;
    }
    struct LruNode *victim = *tail;
    hashRemove(buckets, victim);
    detach(victim, head, tail);
    victim->alive = 0;
    initNode(victim, key, value);
    hashInsert(buckets, victim);
    attachFront(victim, head, tail);
    return 0;
}

int get(struct LruNode **buckets, struct LruNode **head, struct LruNode **tail, int key) {
    struct LruNode *found = NULL;
    hashFind(buckets, key, &found);
    if (found == NULL) {
        return -1;
    }
    touch(found, head, tail);
    return found->value;
}

int main() {
    struct LruNode pool0;
    struct LruNode pool1;
    struct LruNode pool2;
    struct LruNode pool3;
    struct LruNode pool4;
    struct LruNode pool5;
    struct LruNode *nodes[6];
    struct LruNode *buckets[5];
    struct LruNode *head;
    struct LruNode *tail;
    int used = 0;
    int score = 0;
    nodes[0] = &pool0;
    nodes[1] = &pool1;
    nodes[2] = &pool2;
    nodes[3] = &pool3;
    nodes[4] = &pool4;
    nodes[5] = &pool5;
    head = NULL;
    tail = NULL;
    clearBuckets(buckets);
    put(nodes, &used, 4, buckets, &head, &tail, 1, 100);
    put(nodes, &used, 4, buckets, &head, &tail, 6, 600);
    put(nodes, &used, 4, buckets, &head, &tail, 2, 200);
    put(nodes, &used, 4, buckets, &head, &tail, 7, 700);
    score = score + get(buckets, &head, &tail, 1);
    put(nodes, &used, 4, buckets, &head, &tail, 3, 300);
    score = score + get(buckets, &head, &tail, 6);
    put(nodes, &used, 4, buckets, &head, &tail, 2, 222);
    score = score + get(buckets, &head, &tail, 2);
    score = score + get(buckets, &head, &tail, 7);
    return score + used; // @break
}
