struct Node {
    int value;
    struct Node *next;
};

int main() {
    struct Node a;
    struct Node b;
    struct Node c;

    a.value = 1;
    b.value = 2;
    c.value = 3;

    a.next = &b;
    b.next = &c;
    c.next = NULL;

    int sum = a.value + a.next->value + a.next->next->value;
    return sum;
}
