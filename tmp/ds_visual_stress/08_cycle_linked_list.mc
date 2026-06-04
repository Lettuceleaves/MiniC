// @visual root=head kind=singly-list label=value max-depth=8
// @visual root=nodes kind=struct-array fields=value,next
struct Node {
    int value;
    struct Node *next;
};

int linkNode(struct Node *a, struct Node *b) {
    a->next = b;
    return 0;
}

int hasCycle(struct Node *head) {
    struct Node *slow = head;
    struct Node *fast = head;
    while (fast != NULL) {
        if (fast->next == NULL) {
            return 0;
        }
        slow = slow->next;
        fast = fast->next->next;
        if (slow == fast) {
            return 1;
        }
    }
    return 0;
}

int main() {
    struct Node n0;
    struct Node n1;
    struct Node n2;
    struct Node n3;
    struct Node *nodes[4];
    struct Node *head;
    int before = 0;
    int after = 0;
    nodes[0] = &n0;
    nodes[1] = &n1;
    nodes[2] = &n2;
    nodes[3] = &n3;
    n0.value = 1;
    n1.value = 2;
    n2.value = 3;
    n3.value = 4;
    linkNode(&n0, &n1);
    linkNode(&n1, &n2);
    linkNode(&n2, &n3);
    linkNode(&n3, NULL);
    head = &n0;
    before = hasCycle(head);
    linkNode(&n3, &n1);
    after = hasCycle(head);
    return before * 10 + after; // @break
}
