// @visual root=head kind=lru-list label=value
struct Node {
    int value;
    struct Node *prev;
    struct Node *next;
};

int main() {
    struct Node a;
    struct Node b;
    struct Node c;
    struct Node *head;
    a.value = 10;
    b.value = 20;
    c.value = 30;
    a.prev = NULL;
    a.next = &b;
    b.prev = &a;
    b.next = &c;
    c.prev = &b;
    c.next = NULL;
    head = &a;
    return head->value + head->next->value + head->next->next->value; // @break
}
