// @visual root=handle kind=struct-pointer-chain
struct Node {
    int value;
};

int main() {
    struct Node node;
    struct Node *current;
    struct Node **handle;
    node.value = 8;
    current = &node;
    handle = &current;
    (*handle)->value = (*handle)->value + 2;
    return node.value; // @break
}
