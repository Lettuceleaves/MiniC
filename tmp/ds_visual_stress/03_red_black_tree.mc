// @visual root=root kind=binary-tree label=key
// @visual root=nodes kind=struct-array fields=key,color,left,right,parent,alive
struct RbNode {
    int key;
    int color;
    int alive;
    struct RbNode *left;
    struct RbNode *right;
    struct RbNode *parent;
};

int initRbNode(struct RbNode *node, int key) {
    node->key = key;
    node->color = 1;
    node->alive = 1;
    node->left = NULL;
    node->right = NULL;
    node->parent = NULL;
    return 0;
}

int rotateLeft(struct RbNode **root, struct RbNode *x) {
    struct RbNode *y = x->right;
    x->right = y->left;
    if (y->left != NULL) {
        y->left->parent = x;
    }
    y->parent = x->parent;
    if (x->parent == NULL) {
        *root = y;
    } else {
        if (x == x->parent->left) {
            x->parent->left = y;
        } else {
            x->parent->right = y;
        }
    }
    y->left = x;
    x->parent = y;
    return 0;
}

int rotateRight(struct RbNode **root, struct RbNode *x) {
    struct RbNode *y = x->left;
    x->left = y->right;
    if (y->right != NULL) {
        y->right->parent = x;
    }
    y->parent = x->parent;
    if (x->parent == NULL) {
        *root = y;
    } else {
        if (x == x->parent->right) {
            x->parent->right = y;
        } else {
            x->parent->left = y;
        }
    }
    y->right = x;
    x->parent = y;
    return 0;
}

int fixInsert(struct RbNode **root, struct RbNode *z) {
    struct RbNode *cur = z;
    while (cur->parent != NULL) {
        if (cur->parent->color == 0) {
            return 0;
        }
        if (cur->parent == cur->parent->parent->left) {
            struct RbNode *y = cur->parent->parent->right;
            if (y != NULL) {
                if (y->color == 1) {
                    cur->parent->color = 0;
                    y->color = 0;
                    cur->parent->parent->color = 1;
                    cur = cur->parent->parent;
                } else {
                    if (cur == cur->parent->right) {
                        cur = cur->parent;
                        rotateLeft(root, cur);
                    }
                    cur->parent->color = 0;
                    cur->parent->parent->color = 1;
                    rotateRight(root, cur->parent->parent);
                }
            } else {
                if (cur == cur->parent->right) {
                    cur = cur->parent;
                    rotateLeft(root, cur);
                }
                cur->parent->color = 0;
                cur->parent->parent->color = 1;
                rotateRight(root, cur->parent->parent);
            }
        } else {
            struct RbNode *y2 = cur->parent->parent->left;
            if (y2 != NULL) {
                if (y2->color == 1) {
                    cur->parent->color = 0;
                    y2->color = 0;
                    cur->parent->parent->color = 1;
                    cur = cur->parent->parent;
                } else {
                    if (cur == cur->parent->left) {
                        cur = cur->parent;
                        rotateRight(root, cur);
                    }
                    cur->parent->color = 0;
                    cur->parent->parent->color = 1;
                    rotateLeft(root, cur->parent->parent);
                }
            } else {
                if (cur == cur->parent->left) {
                    cur = cur->parent;
                    rotateRight(root, cur);
                }
                cur->parent->color = 0;
                cur->parent->parent->color = 1;
                rotateLeft(root, cur->parent->parent);
            }
        }
    }
    (*root)->color = 0;
    return 0;
}

int rbInsert(struct RbNode **nodes, int *used, struct RbNode **root, int key) {
    struct RbNode *z = nodes[*used];
    *used = *used + 1;
    initRbNode(z, key);
    struct RbNode *y = NULL;
    struct RbNode *x = *root;
    while (x != NULL) {
        y = x;
        if (z->key < x->key) {
            x = x->left;
        } else {
            x = x->right;
        }
    }
    z->parent = y;
    if (y == NULL) {
        *root = z;
    } else {
        if (z->key < y->key) {
            y->left = z;
        } else {
            y->right = z;
        }
    }
    fixInsert(root, z);
    return 0;
}

int rbFind(struct RbNode *root, int key) {
    struct RbNode *cur = root;
    while (cur != NULL) {
        if (key == cur->key) {
            return 1;
        }
        if (key < cur->key) {
            cur = cur->left;
        } else {
            cur = cur->right;
        }
    }
    return 0;
}

int rbDeleteMark(struct RbNode *root, int key) {
    struct RbNode *cur = root;
    while (cur != NULL) {
        if (key == cur->key) {
            cur->alive = 0;
            return 0;
        }
        if (key < cur->key) {
            cur = cur->left;
        } else {
            cur = cur->right;
        }
    }
    return 0;
}

int main() {
    struct RbNode n0;
    struct RbNode n1;
    struct RbNode n2;
    struct RbNode n3;
    struct RbNode n4;
    struct RbNode n5;
    struct RbNode n6;
    struct RbNode n7;
    struct RbNode *nodes[16];
    struct RbNode *root = NULL;
    int used = 0;
    int score = 0;
    nodes[0] = &n0;
    nodes[1] = &n1;
    nodes[2] = &n2;
    nodes[3] = &n3;
    nodes[4] = &n4;
    nodes[5] = &n5;
    nodes[6] = &n6;
    nodes[7] = &n7;
    rbInsert(nodes, &used, &root, 10);
    rbInsert(nodes, &used, &root, 5);
    rbInsert(nodes, &used, &root, 1);  // LL case: rotateRight
    rbInsert(nodes, &used, &root, 15);
    rbInsert(nodes, &used, &root, 20); // RR case: rotateLeft
    rbInsert(nodes, &used, &root, 7);
    rbInsert(nodes, &used, &root, 6);
    if (rbFind(root, 20) != 0) {
        score = score + 20;
    }
    rbDeleteMark(root, 5);
    rbInsert(nodes, &used, &root, 8);
    if (rbFind(root, 8) != 0) {
        score = score + 8;
    }
    return score + root->key + used; // @break
}
