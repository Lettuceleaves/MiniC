// @visual root=nodes kind=struct-array fields=end,count,a,b,c,d
struct TrieNode {
    int end;
    int count;
    struct TrieNode *a;
    struct TrieNode *b;
    struct TrieNode *c;
    struct TrieNode *d;
};

int initTrieNode(struct TrieNode *node) {
    node->end = 0;
    node->count = 0;
    node->a = NULL;
    node->b = NULL;
    node->c = NULL;
    node->d = NULL;
    return 0;
}

int child(struct TrieNode *node, int ch, struct TrieNode **out) {
    *out = NULL;
    if (ch == 0) {
        *out = node->a;
        return 0;
    }
    if (ch == 1) {
        *out = node->b;
        return 0;
    }
    if (ch == 2) {
        *out = node->c;
        return 0;
    }
    *out = node->d;
    return 0;
}

int setChild(struct TrieNode *node, int ch, struct TrieNode *next) {
    if (ch == 0) {
        node->a = next;
    }
    if (ch == 1) {
        node->b = next;
    }
    if (ch == 2) {
        node->c = next;
    }
    if (ch == 3) {
        node->d = next;
    }
    return 0;
}

int insertWord(struct TrieNode **nodes, int *used, struct TrieNode *root, int *word, int len) {
    struct TrieNode *cur = root;
    int i = 0;
    cur->count = cur->count + 1;
    while (i < len) {
        int ch = word[i];
        struct TrieNode *next = NULL;
        child(cur, ch, &next);
        if (next == NULL) {
            next = nodes[*used];
            *used = *used + 1;
            initTrieNode(next);
            setChild(cur, ch, next);
        }
        cur = next;
        cur->count = cur->count + 1;
        i = i + 1;
    }
    cur->end = cur->end + 1;
    return 0;
}

int queryWord(struct TrieNode *root, int *word, int len) {
    struct TrieNode *cur = root;
    int i = 0;
    while (i < len) {
        struct TrieNode *next = NULL;
        child(cur, word[i], &next);
        cur = next;
        if (cur == NULL) {
            return 0;
        }
        i = i + 1;
    }
    return cur->end;
}

int deleteWord(struct TrieNode *root, int *word, int len) {
    struct TrieNode *cur = root;
    int i = 0;
    if (queryWord(root, word, len) == 0) {
        return 0;
    }
    cur->count = cur->count - 1;
    while (i < len) {
        struct TrieNode *next = NULL;
        child(cur, word[i], &next);
        cur = next;
        cur->count = cur->count - 1;
        i = i + 1;
    }
    cur->end = cur->end - 1;
    return 0;
}

int main() {
    struct TrieNode n0;
    struct TrieNode n1;
    struct TrieNode n2;
    struct TrieNode n3;
    struct TrieNode n4;
    struct TrieNode n5;
    struct TrieNode n6;
    struct TrieNode n7;
    struct TrieNode n8;
    struct TrieNode n9;
    struct TrieNode *nodes[16];
    struct TrieNode *root;
    int used = 1;
    int word1[4];
    int word2[4];
    int word3[4];
    int score = 0;
    nodes[0] = &n0;
    nodes[1] = &n1;
    nodes[2] = &n2;
    nodes[3] = &n3;
    nodes[4] = &n4;
    nodes[5] = &n5;
    nodes[6] = &n6;
    nodes[7] = &n7;
    nodes[8] = &n8;
    nodes[9] = &n9;
    root = nodes[0];
    initTrieNode(root);
    word1[0] = 0;
    word1[1] = 1;
    word1[2] = 2;
    word2[0] = 0;
    word2[1] = 1;
    word2[2] = 3;
    word3[0] = 1;
    word3[1] = 0;
    word3[2] = 3;
    insertWord(nodes, &used, root, word1, 3);
    insertWord(nodes, &used, root, word2, 3);
    insertWord(nodes, &used, root, word3, 3);
    score = score + queryWord(root, word1, 3);
    deleteWord(root, word2, 3);
    score = score + queryWord(root, word2, 3);
    insertWord(nodes, &used, root, word2, 3);
    score = score + queryWord(root, word2, 3);
    return score + used; // @break
}
