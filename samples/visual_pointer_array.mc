// @visual root=items kind=pointer-array
int main() {
    int first;
    int second;
    int *items[2];
    first = 10;
    second = 20;
    items[0] = &first;
    items[1] = &second;
    *items[0] = *items[0] + 1;
    return *items[0] + *items[1]; // @break
}
