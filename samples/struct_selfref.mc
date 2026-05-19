struct Node {
    int value;
    struct Node *next;
};

int main() {
    struct Node a;
    struct Node b;

    a.value = 10;
    a.next = &b;
    b.value = 20;
    b.next = NULL;

    return a.value + a.next->value;
}
