// @visual root=nodes kind=struct-array fields=k0,k1,k2,c0,c1,c2,c3,count,leaf
struct BNode {
    int k0;
    int k1;
    int k2;
    int count;
    int leaf;
    struct BNode *c0;
    struct BNode *c1;
    struct BNode *c2;
    struct BNode *c3;
};

int initBNode(struct BNode *node, int leaf) {
    node->k0 = 0;
    node->k1 = 0;
    node->k2 = 0;
    node->count = 0;
    node->leaf = leaf;
    node->c0 = NULL;
    node->c1 = NULL;
    node->c2 = NULL;
    node->c3 = NULL;
    return 0;
}

int setKey(struct BNode *node, int index, int value) {
    if (index == 0) {
        node->k0 = value;
    }
    if (index == 1) {
        node->k1 = value;
    }
    if (index == 2) {
        node->k2 = value;
    }
    return 0;
}

int getKey(struct BNode *node, int index) {
    if (index == 0) {
        return node->k0;
    }
    if (index == 1) {
        return node->k1;
    }
    return node->k2;
}

int insertLeafKey(struct BNode *node, int key) {
    if (node->count == 0) {
        node->k0 = key;
        node->count = 1;
        return 0;
    }
    if (node->count == 1) {
        if (key < node->k0) {
            node->k1 = node->k0;
            node->k0 = key;
        } else {
            node->k1 = key;
        }
        node->count = 2;
        return 0;
    }
    if (key < node->k0) {
        node->k2 = node->k1;
        node->k1 = node->k0;
        node->k0 = key;
    } else {
        if (key < node->k1) {
            node->k2 = node->k1;
            node->k1 = key;
        } else {
            node->k2 = key;
        }
    }
    node->count = 3;
    return 0;
}

int findInBNode(struct BNode *node, int key) {
    if (node == NULL) {
        return 0;
    }
    int i = 0;
    while (i < node->count) {
        if (getKey(node, i) == key) {
            return 1;
        }
        i = i + 1;
    }
    if (node->leaf != 0) {
        return 0;
    }
    if (key < node->k0) {
        return findInBNode(node->c0, key);
    }
    if (node->count == 1) {
        return findInBNode(node->c1, key);
    }
    if (key < node->k1) {
        return findInBNode(node->c1, key);
    }
    if (node->count == 2) {
        return findInBNode(node->c2, key);
    }
    if (key < node->k2) {
        return findInBNode(node->c2, key);
    }
    return findInBNode(node->c3, key);
}

int buildDemoBTree(struct BNode **nodes, struct BNode **root) {
    struct BNode *r = nodes[0];
    struct BNode *left = nodes[1];
    struct BNode *mid = nodes[2];
    struct BNode *right = nodes[3];
    initBNode(r, 0);
    initBNode(left, 1);
    initBNode(mid, 1);
    initBNode(right, 1);
    insertLeafKey(left, 1);
    insertLeafKey(left, 3);
    insertLeafKey(left, 4);
    insertLeafKey(mid, 6);
    insertLeafKey(mid, 7);
    insertLeafKey(right, 12);
    insertLeafKey(right, 17);
    insertLeafKey(right, 20);
    r->k0 = 5;
    r->k1 = 10;
    r->count = 2;
    r->leaf = 0;
    r->c0 = left;
    r->c1 = mid;
    r->c2 = right;
    *root = r;
    return 0;
}

int deleteMarkFromLeaf(struct BNode *root, int key) {
    if (root == NULL) {
        return 0;
    }
    if (root->leaf != 0) {
        if (root->k0 == key) {
            root->k0 = -1;
        }
        if (root->k1 == key) {
            root->k1 = -1;
        }
        if (root->k2 == key) {
            root->k2 = -1;
        }
        return 0;
    }
    deleteMarkFromLeaf(root->c0, key);
    deleteMarkFromLeaf(root->c1, key);
    deleteMarkFromLeaf(root->c2, key);
    deleteMarkFromLeaf(root->c3, key);
    return 0;
}

int main() {
    struct BNode n0;
    struct BNode n1;
    struct BNode n2;
    struct BNode n3;
    struct BNode n4;
    struct BNode n5;
    struct BNode n6;
    struct BNode n7;
    struct BNode *nodes[8];
    struct BNode *root;
    int score = 0;
    nodes[0] = &n0;
    nodes[1] = &n1;
    nodes[2] = &n2;
    nodes[3] = &n3;
    nodes[4] = &n4;
    nodes[5] = &n5;
    nodes[6] = &n6;
    nodes[7] = &n7;
    buildDemoBTree(nodes, &root);
    if (findInBNode(root, 7) != 0) {
        score = score + 7;
    }
    if (findInBNode(root, 12) != 0) {
        score = score + 12;
    }
    deleteMarkFromLeaf(root, 7);
    insertLeafKey(root->c1, 8);
    if (findInBNode(root, 8) != 0) {
        score = score + 8;
    }
    return score + root->k0 + root->k1; // @break
}
