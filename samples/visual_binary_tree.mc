// @visual root=root kind=binary-tree label=value
struct Node {
    int value;
    struct Node *left;
    struct Node *right;
};

int main() {
    struct Node n1;
    struct Node n2;
    struct Node n3;
    struct Node *root;
    n1.value = 10;
    n2.value = 5;
    n3.value = 15;
    n1.left = &n2;
    n1.right = &n3;
    n2.left = NULL;
    n2.right = NULL;
    n3.left = NULL;
    n3.right = NULL;
    root = &n1;
    return root->value + root->left->value + root->right->value; // @break
}
