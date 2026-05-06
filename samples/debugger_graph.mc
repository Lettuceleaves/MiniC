// @visual graph name=list kind=list root=head node=Node next=next label=value
// @visual-node graph=network id=1 label=head
// @visual-node graph=network id=2 label=tail
// @visual-edge graph=network from=1 to=2 label=next directed=true
int main() {
    int head = 1;
    int tail = head + 1;
    return tail;
}
