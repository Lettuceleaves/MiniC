// @visual root=head kind=struct-list next=next label=value max-depth=4
struct Node {
    int value;
    struct Node *next;
};

int main() {
    struct Node a;
    struct Node b;
    struct Node c;
    struct Node *head;
    a.value = 1;
    b.value = 2;
    c.value = 3;
    a.next = &b;
    b.next = &c;
    c.next = NULL;
    head = &a;
    return head->value + head->next->value + head->next->next->value; // @break
}
