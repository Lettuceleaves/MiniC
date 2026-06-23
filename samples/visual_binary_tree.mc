// @visual root=root kind=binary-tree label=key
struct Node {
    int key;
    struct Node *left;
    struct Node *right;
};

int main() {
    struct Node root;
    struct Node left;
    struct Node right;
    root.key = 7;
    left.key = 3;
    right.key = 11;
    root.left = &left;
    root.right = &right;
    left.left = NULL;
    left.right = NULL;
    right.left = NULL;
    right.right = NULL;
    return root.key + left.key + right.key;
}
