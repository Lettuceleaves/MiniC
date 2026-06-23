// @visual root=root kind=binary-tree label=key
struct RBNode {
    int key;
    int color;
    struct RBNode *left;
    struct RBNode *right;
};

int rotateLeft(int node) {
    return node;
}

int rotateRight(int node) {
    return node;
}

int main() {
    struct RBNode root;
    struct RBNode left;
    struct RBNode right;
    root.key = 10;
    root.color = 0;
    left.key = 4;
    left.color = 1;
    right.key = 14;
    right.color = 1;
    root.left = &left;
    root.right = &right;
    left.left = NULL;
    left.right = NULL;
    right.left = NULL;
    right.right = NULL;
    return rotateLeft(root.key) + rotateRight(root.color);
}
