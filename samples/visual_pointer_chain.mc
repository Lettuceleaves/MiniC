// @visual root=pp kind=pointer-chain
int main() {
    int value;
    int *p;
    int **pp;
    value = 4;
    p = &value;
    pp = &p;
    **pp = 12;
    return value; // @break
}
