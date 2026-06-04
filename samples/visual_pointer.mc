// @visual root=p kind=pointer
int main() {
    int value;
    int *p;
    value = 3;
    p = &value;
    *p = 9;
    return value; // @break
}
