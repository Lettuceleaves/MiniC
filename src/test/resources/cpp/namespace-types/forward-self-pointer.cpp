#include <stdio.h>
namespace Graph {
    struct Node;
    typedef Node Link;
    struct Node { int value; Node *next; };
    int sum(Node *head) {
        int result = 0;
        while (head != 0) {
            result += head->value;
            head = head->next;
        }
        return result;
    }
}
namespace Graph { typedef Link Alias; }
int main() {
    Graph::Node tail = {2, 0};
    Graph::Alias head = {1, &tail};
    printf("%d %d\n", Graph::sum(&head), (int)sizeof(Graph::Link));
    return 0;
}
